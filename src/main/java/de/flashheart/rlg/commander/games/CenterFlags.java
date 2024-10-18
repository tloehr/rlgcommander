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
import org.javatuples.Quartet;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Log4j2
public class CenterFlags extends Timed implements HasScoreBroadcast {
    private long broadcast_cycle_counter;
    private final List<String> capture_points;
    private final Table<String, String, Long> scores;
    private final JobKey broadcastScoreJobkey;
    private long last_job_broadcast;


    public CenterFlags(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        assert_two_teams_red_and_blue();
        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());
        scores = HashBasedTable.create();
        reset_score_table();
        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
    }

    private void reset_score_table() {
        Lists.newArrayList("blue", "red").forEach(color -> {
            capture_points.forEach(agent -> scores.put(agent, color, 0L));
            scores.put("all", color, 0L);
        });
    }

    private JSONObject scores_to_vars() {
        JSONObject vars = new JSONObject();
        Lists.newArrayList("blue", "red").forEach(color -> {
            capture_points.forEach(agent -> {
                vars.put(get_agent_key(agent, color), JavaTimeConverter.format(scores.get(agent, color)));
            });
            vars.put("score_" + color, JavaTimeConverter.format(scores.get("all", color)));

            vars.put("red_respawns", team_registry.get("red_spawn").getRespawns());
            vars.put("blue_respawns", team_registry.get("blue_spawn").getRespawns());
        });
        return vars;
    }

    private String get_agent_key(String agent, String state) {
        return String.format("%s_%s", state.toLowerCase(), agent);
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED"))), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
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
                        "I am ${agentname}...",
                        "...and Your Flag",
                        "Blue: ${" + get_agent_key(agent, "blue") + "}",
                        "Red: ${" + get_agent_key(agent, "red") + "}"
                ), agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "neutral"));
    }

    private void cp_to_blue(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.BLUE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "blue"));
        broadcast_score();
    }

    private void cp_to_red(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "red"));
        broadcast_score();
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        deleteJob(broadcastScoreJobkey); // this cycle has no use anymore
        //todo: this is not called the last time. agents show an outdated score after game over - WHY ?
        broadcast_score(); // one last time
    }

    @Override
    public void on_reset() {
        super.on_reset();
        deleteJob(broadcastScoreJobkey);
        broadcast_cycle_counter = 0L;
        last_job_broadcast = 0L;
        reset_score_table();
        broadcast_score();
    }

    @Override
    public void on_run() {
        super.on_run();
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
            scores.put(agent, color, 0L);
            scores.put("all", color, sum_score_for_this_color - score_for_this_agent_and_color);
        });
    }

    @Override
    public boolean hasZeus() {
        return true;
    }

    @Override
    public void process_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else
            super.process_external_message(agent_id, source, message);
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            JSONObject epilog_pages = MQTT.page("page0",
                    "Game Over         ${wifi_signal}",
                    "Punktestand",
                    "Blau: ${score_blue}",
                    "Rot: ${score_red}");
            if (count_respawns) MQTT.merge(epilog_pages,
                    MQTT.page("page1",
                            "Game Over         ${wifi_signal}",
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
                            StringUtils.center("Zeiten", 18) + "${wifi_signal}",
                            "Blau: ${score_blue}",
                            "Rot: ${score_red}"),
                    MQTT.page("page1",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Rote Flaggen", 18) + "${wifi_signal}",
                            "${redline1}",
                            "${redline2}"),
                    MQTT.page("page2",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Blaue Flaggen", 18) + "${wifi_signal}",
                            "${blueline1}",
                            "${blueline2}")
            );
            if (count_respawns) MQTT.merge(pages_when_game_runs,
                    MQTT.page("page3",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Respawns", 18) + "${wifi_signal}",
                            "Blau: ${blue_respawns}",
                            "Rot: ${red_respawns}")
            );
            return pages_when_game_runs;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("scores", new JSONObject(scores.columnMap()));
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        final ArrayList<Quartet<String, String, String, String>> my_scores = new ArrayList<>();
        cpFSMs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(agent_fsm -> {
            String css_classname = "table-light";
            if (agent_fsm.getValue().getCurrentState().toLowerCase().matches("red|game_over_red"))
                css_classname = "table-danger";
            if (agent_fsm.getValue().getCurrentState().toLowerCase().matches("blue|game_over_blue"))
                css_classname = "table-primary";

            String agent = agent_fsm.getKey();

            my_scores.add(new Quartet<>(css_classname, agent,
                    JavaTimeConverter.format(Instant.ofEpochMilli(scores.get(agent, "blue"))),
                    JavaTimeConverter.format(Instant.ofEpochMilli(scores.get(agent, "red")))
            ));
        });

        model.addAttribute("scores", my_scores);
        model.addAttribute("sum_blue", JavaTimeConverter.format(Instant.ofEpochMilli(scores.get("all", "blue"))));
        model.addAttribute("sum_red", JavaTimeConverter.format(Instant.ofEpochMilli(scores.get("all", "red"))));

    }

    @Override
    public String getGameMode() {
        return "center_flags";
    }

    @Override
    public String get_in_game_event_description(JSONObject event) {
        String result = super.get_in_game_event_description(event);
        if (result.isEmpty()) {
            result = "error";
            String type = event.getString("type");

            if (type.equalsIgnoreCase("in_game_state_change")) {
                String zeus = (event.has("zeus") ? " (by the hand of ZEUS)" : "");
                if (event.getString("item").equals("add_seconds")) {
                    String text = event.getLong("amount") >= 0 ? " has been granted %d seconds" : " has lost %d seconds";
                    result = "Team " + event.getString("team") + String.format(text, Math.abs(event.getLong("amount")))
                            + zeus;
                }
            }
        }
        return result;
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
            add_in_game_event(new JSONObject()
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
            add_in_game_event(new JSONObject()
                    .put("item", "add_seconds")
                    .put("team", team)
                    .put("amount", amount)
                    .put("zeus", "intervention"));
        }
        if (count_respawns && operation.equalsIgnoreCase("add_respawns")) {
            String team = params.getString("team").toLowerCase();
            int amount = params.getInt("amount");
            if (!team.toLowerCase().matches("blue|red")) throw new IllegalStateException("team must be blue or red");

            team_registry.get(team.toLowerCase() + "_spawn").add_respawn(amount);

            scores.put("all", team, scores.get("all", team) + amount * 1000L);
            add_in_game_event(new JSONObject()
                    .put("item", "add_respawns")
                    .put("team", team)
                    .put("amount", amount)
                    .put("zeus", "intervention"));
        }
    }
}
