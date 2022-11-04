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

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    public CenterFlags(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                "   ______           __            ________\n" +
                "  / ____/__  ____  / /____  _____/ ____/ /___ _____ ______\n" +
                " / /   / _ \\/ __ \\/ __/ _ \\/ ___/ /_  / / __ `/ __ `/ ___/\n" +
                "/ /___/  __/ / / / /_/  __/ /  / __/ / / /_/ / /_/ (__  )\n" +
                "\\____/\\___/_/ /_/\\__/\\___/_/  /_/   /_/\\__,_/\\__, /____/\n" +
                "                                            /____/");
        setGameDescription(game_parameters.getString("comment"), "",
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))), "");

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());

        scores = HashBasedTable.create();
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));

        reset_score_table();

        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
        // just for score
//        add_spawn_for("red_spawn", MQTT.RED, "RedFor");
//        add_spawn_for("blue_spawn", MQTT.BLUE, "BlueFor");

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
            JSONObject vars = MQTT.merge(scores_to_vars(), get_agents_states_for_lcd());
            send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), get_active_spawn_agents());
            send("vars", vars, agents.keySet());
            if (game_fsm.getCurrentState().equals(_state_EPILOG))
                log.info(vars.toString(4));
            else
                log.trace(vars.toString(4));
        }

    }

    /**
     * creates line variables to show the state of the agents involved.
     *
     * @return
     */
    private JSONObject get_agents_states_for_lcd() {
        ArrayList<String> s = new ArrayList<>();
        capture_points.forEach(agent -> {
            String color = cpFSMs.get(agent).getCurrentState().toLowerCase();
            if (!color.matches("red|blue")) color = "--";
            s.add(String.format("%s:%s", agent, StringUtils.left(color, 4)));
        });
        JSONObject lines = MQTT.toJSON("line1", "", "line2", "", "line3", "", "line4", "");
        int index = 0;
        for (List<String> list : Lists.partition(s, 2)) {
            index++;
            lines.put("line" + index, list.get(0) + (list.size() > 1 ? "," + list.get(1) : ""));
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
            return MQTT.page("page0", "Game Over",
                    "",
                    "Blau: ${score_blue}",
                    "Rot: ${score_red}");
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return MQTT.merge(MQTT.page("page0",
                            "Restzeit:  ${remaining}",
                            "",
                            "Blau: ${score_blue}",
                            "Rot: ${score_red}"),
                    MQTT.page("page1",
                            "Restzeit:  ${remaining}",
                            "${line1}",
                            "${line2}",
                            "${line3}")
            );
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {
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
        final JSONObject states = new JSONObject();
        cpFSMs.forEach((agentid, fsm) -> states.put(agentid, fsm.getCurrentState()));
        return super.getState()
                .put("scores", new JSONObject(scores.columnMap()))
                .put("red_respawns", red_respawns)
                .put("blue_respawns", blue_respawns)
                .put("agent_states", states);
    }
}
