package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.BroadcastScoreJob;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.swing.text.html.HTML;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Log4j2
public class CenterFlags extends Timed implements HasScoreBroadcast {
    private final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final List<String> capture_points;
    private final Table<String, String, Long> scores;
    private final JobKey broadcastScoreJobkey;
    private long last_job_broadcast;
    private int blue_respawns, red_respawns;
    private final boolean count_respawns;

    public CenterFlags(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                "   ______           __            ________\n" +
                "  / ____/__  ____  / /____  _____/ ____/ /___ _____ ______\n" +
                " / /   / _ \\/ __ \\/ __/ _ \\/ ___/ /_  / / __ `/ __ `/ ___/\n" +
                "/ /___/  __/ / / / /_/  __/ /  / __/ / / /_/ / /_/ (__  )\n" +
                "\\____/\\___/_/ /_/\\__/\\___/_/  /_/   /_/\\__,_/\\__, /____/\n" +
                "                                            /____/");

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());
        count_respawns = game_parameters.optBoolean("count_respawns");
        scores = HashBasedTable.create();
        reset_score_table();
        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
    }

    private void reset_score_table() {
        Lists.newArrayList("blue", "red").forEach(color -> {
            capture_points.forEach(agent -> {
                scores.put(agent, color, 0l);
            });
            scores.put("all", color, 0l);
        });
    }

    private JSONObject scores_to_vars() {
        JSONObject vars = new JSONObject();
        Lists.newArrayList("blue", "red").forEach(color -> {
            capture_points.forEach(agent -> {
                vars.put(get_agent_key(agent, color), JavaTimeConverter.format(scores.get(agent, color)));
            });
            vars.put("score_" + color, JavaTimeConverter.format(scores.get("all", color)));
            vars.put("red_respawns", red_respawns);
            vars.put("blue_respawns", blue_respawns);
        });
        return vars;
    }

    private String get_agent_key(String agent, String state) {
        return String.format("%s_%s", state.toLowerCase(), agent);
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest_cp.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED"))), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
//                process_message(_msg_IN_GAME_EVENT_OCCURRED);
            });

            fsm.setAction("to_neutral", new FSMAction() {   // admin function
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    revert_score_for(agent);
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}...", "...and Your Flag", "Blue: ${" + get_agent_key(agent, "blue") + "}", "Red: ${" + get_agent_key(agent, "red") + "}"),
                agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "neutral"));
    }

    private void cp_to_blue(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "normal"), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "blue"));
        broadcast_score();
    }

    private void cp_to_red(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "normal"), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "red"));
        broadcast_score();
    }

    @Override
    public void game_over_operations() {
        super.game_over_operations();
        deleteJob(broadcastScoreJobkey); // this cycle has no use anymore
        //todo: this is not called the last time. agents show an outdated score after game over - WHY ?
        broadcast_score(); // one last time
    }

    @Override
    public void reset_operations() {
        super.reset_operations();
        deleteJob(broadcastScoreJobkey);
        broadcast_cycle_counter = 0l;
        last_job_broadcast = 0l;
        blue_respawns = 0;
        red_respawns = 0;
        reset_score_table();
        broadcast_score();
    }

    @Override
    public void run_operations() {
        super.run_operations();
        long repeat_every_ms = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();
        create_job(broadcastScoreJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), BroadcastScoreJob.class);
        broadcast_cycle_counter = 0;
    }

    @Override
    public void broadcast_score() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
        final long time_to_add = now - last_job_broadcast;
        last_job_broadcast = now;

        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            cpFSMs.entrySet().forEach(stringFSMEntry -> add_score_for(stringFSMEntry.getKey(), stringFSMEntry.getValue().getCurrentState(), time_to_add));

        broadcast_cycle_counter++;
        if (!game_fsm.getCurrentState().equals(_state_RUNNING) || broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0) {
            JSONObject vars = MQTT.merge(scores_to_vars(),
                    get_agents_states_for_lcd_with("red"),
                    get_agents_states_for_lcd_with("blue")
            );
            send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), get_active_spawn_agents());
            send("vars", vars, agents.keySet());
            log.trace(vars.toString(4));
        }

    }

    private ArrayList<String> get_agents_with(String _color) {
        ArrayList<String> result = new ArrayList<>();
        cpFSMs.entrySet().stream()
                .filter(stringFSMEntry -> stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase(_color))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(stringFSMEntry -> result.add(stringFSMEntry.getKey()));
        return result;
    }

    /**
     * creates line variables to show the state of the agents involved.
     *
     * @return
     */
    private JSONObject get_agents_states_for_lcd_with(String _color) {
        JSONObject lines = MQTT.toJSON(_color + "line1", "", _color + "line2", "", _color + "line3", "", _color + "line4", "");
        int index = 0;
        for (List<String> list : Lists.partition(get_agents_with(_color), 4)) {
            index++;
            lines.put(_color + "line" + index, list.stream().collect(Collectors.joining(",")));
        }
        return lines;
    }

    private void add_score_for(String agent, String current_state, long time_to_add) {
        String color = current_state.toLowerCase();
        if (!color.equals("blue") && !color.equals("red")) return;
        // replace cell values
        scores.put(agent, color, scores.get(agent, color) + time_to_add); // replace cell value
        scores.put("all", color, scores.get("all", color) + time_to_add);
    }

    /**
     * if we have to reset a flag back to neutral (white) then all scores added by this agent will be reverted to zero.
     * And his scores will be subtracted from the overall sums.
     *
     * @param agent
     */
    private void revert_score_for(String agent) {
        Lists.newArrayList("blue", "red").forEach(color -> {
            long score_for_this_agent_and_color = scores.get(agent, color);
            long sum_score_for_this_color = scores.get("all", color);
            scores.put(agent, color, 0l);
            scores.put("all", color, sum_score_for_this_color - score_for_this_agent_and_color);
        });
    }

    @Override
    public void zeus(JSONObject params) throws IllegalStateException, JSONException {
        if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return;

        String operation = params.getString("operation").toLowerCase();
        if (operation.equalsIgnoreCase("to_neutral")) {
            String agent = params.getString("agent");
            if (!cpFSMs.containsKey(agent)) throw new IllegalStateException(agent + " unknown");
            if (!cpFSMs.get(agent).getCurrentState().toLowerCase().matches("blue|red"))
                throw new IllegalStateException(agent + " is in state " + cpFSMs.get(agent).getCurrentState() + " must be BLUE or RED");
            cpFSMs.get(agent).ProcessFSM(operation);
            addEvent(new JSONObject()
                    .put("item", "capture_point")
                    .put("agent", agent)
                    .put("state", "neutral")
                    .put("zeus", "intervention"));
        }
        if (operation.equalsIgnoreCase("add_seconds")) {
            String team = params.getString("team").toLowerCase();
            long amount = params.getLong("amount");
            if (!team.toLowerCase().matches("blue|red")) throw new IllegalStateException("team must be blue or red");
            scores.put("all", team, scores.get("all", team) + amount * 1000L);
            addEvent(new JSONObject()
                    .put("item", "add_seconds")
                    .put("team", team)
                    .put("amount", amount)
                    .put("zeus", "intervention"));
        }
        if (operation.equalsIgnoreCase("add_respawns")) {
            String team = params.getString("team").toLowerCase();
            int amount = params.getInt("amount");
            if (!team.toLowerCase().matches("blue|red")) throw new IllegalStateException("team must be blue or red");
            if (team.equalsIgnoreCase("blue")) blue_respawns += amount;
            if (team.equalsIgnoreCase("red")) red_respawns += amount;
            scores.put("all", team, scores.get("all", team) + amount * 1000L);
            addEvent(new JSONObject()
                    .put("item", "add_respawns")
                    .put("team", team)
                    .put("amount", amount)
                    .put("zeus", "intervention"));
        }
    }

    @Override
    public void process_external_message(String sender, String source, JSONObject message) {
        if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
        if (!message.getString("button").equalsIgnoreCase("up")) return;

        if (cpFSMs.containsKey(sender) && game_fsm.getCurrentState().equals(_state_RUNNING))
            cpFSMs.get(sender).ProcessFSM(source.toLowerCase());
        else {
            super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
        }
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            JSONObject epilog_pages = MQTT.page("page0",
                    "Game Over",
                    "Punktestand",
                    "Blau: ${score_blue}",
                    "Rot: ${score_red}");
            if (count_respawns) MQTT.merge(epilog_pages,
                    MQTT.page("page1",
                            "Game Over",
                            "Respawns",
                            "Blau: ${blue_respawns}",
                            "Rot: ${red_respawns}")
            );
            return epilog_pages;
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            JSONObject pages_when_game_runs = MQTT.merge(
                    MQTT.page("page0",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Zeiten", 20),
                            "Blau: ${score_blue}",
                            "Rot: ${score_red}"),
                    MQTT.page("page1",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Rote Flaggen", 20),
                            "${redline1}",
                            "${redline2}"),
                    MQTT.page("page2",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Blaue Flaggen", 20),
                            "${blueline1}",
                            "${blueline2}")
            );
            if (count_respawns) MQTT.merge(pages_when_game_runs,
                    MQTT.page("page3",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Respawns", 20),
                            "Blau: ${blue_respawns}",
                            "Rot: ${red_respawns}")
            );
            return pages_when_game_runs;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {
        if (!count_respawns) return;

        if (spawn_role.equals(RED_SPAWN)) {
            red_respawns++;
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "single_buzz"), agent);
            send("visual", MQTT.toJSON(MQTT.WHITE, "single_buzz"), agent);
            addEvent(new JSONObject().put("item", "respawn").put("agent", agent).put("team", "red").put("value", red_respawns));
        }
        if (spawn_role.equals(BLUE_SPAWN)) {
            blue_respawns++;
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "single_buzz"), agent);
            send("visual", MQTT.toJSON(MQTT.WHITE, "single_buzz"), agent);
            addEvent(new JSONObject().put("item", "respawn").put("agent", agent).put("team", "blue").put("value", blue_respawns));
        }
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("scores", new JSONObject(scores.columnMap()))
                .put("red_respawns", red_respawns)
                .put("blue_respawns", blue_respawns);
    }


    public static String get_in_game_event_description(JSONObject event) {
        String type = event.getString("type");
        if (type.equalsIgnoreCase("general_game_state_change")) {
            return event.getString("message");
        }

        if (event.getString("item").equals("respawn")) {
            return "Respawn Team " + event.getString("team") + ": #" + event.getInt("value");
        }

        if (type.equalsIgnoreCase("in_game_state_change")) {
            String zeus = (event.has("zeus") ? "<br>/(by the hand of ZEUS)" : "");
            if (event.getString("item").equals("capture_point")) {
                return event.getString("agent") + " => " + event.getString("state")
                        + zeus;
            }
            if (event.getString("item").equals("add_seconds")) {
                String text = event.getLong("amount") >= 0 ? " has been granted %d seconds" : " has lost %d seconds";
                return "Team " + event.getString("team") + String.format(text, Math.abs(event.getLong("amount")))
                        + zeus;
            }
        }
        return "";
    }

}
