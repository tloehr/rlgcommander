package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.DelayedReactionJob;
import de.flashheart.rlg.commander.games.traits.HasDelayedReaction;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Log4j2
public class Signal extends Timed implements HasDelayedReaction {
    public static final String _msg_CLOSE = "close";
    public static final String _msg_START_CLOSING = "start_closing";
    public static final String _msg_OPEN = "open";
    public static final String _state_OPEN = "OPEN";
    public static final String _state_CLOSING = "CLOSING";
    public static final String _state_CLOSED = "CLOSED";
    private final List<String> cps_for_red;
    private final List<String> cps_for_blue;
    private String active_color = "";
    //private JSONObject variables_for_score_broadcasting;
    private int blue_points, red_points;
    private final long UNLOCK_TIME;

    public Signal(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        assert_two_teams_red_and_blue();
        //variables_for_score_broadcasting = new JSONObject();
        UNLOCK_TIME = game_parameters.optLong("unlock_time");
        cps_for_red = game_parameters.getJSONObject("agents").getJSONArray("red").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());
        cps_for_blue = game_parameters.getJSONObject("agents").getJSONArray("blu").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());
        register_job("signal_unlock");
        // really ?
        //roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        send(MQTT.CMD_PAGED, MQTT.page("page0",
                "I am ${agentname}", "", "I will be a", "Capture Point"), roles.get("capture_points"));
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/signal.xml"), null);
            fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList("PROLOG", "NEUTRAL")), (state, obj) -> cp_to_neutral(agent));

            fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> prolog(agent));
            fsm.setStatesAfterTransition(_state_OPEN, (state, obj) -> open(agent));
            fsm.setStatesAfterTransition(_state_CLOSING, (state, obj) -> closing(agent));
            fsm.setStatesAfterTransition(_state_CLOSED, (state, obj) -> closed(agent));
            fsm.setStatesAfterTransition(_state_EPILOG, (state, obj) -> epilog(agent));

            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void prolog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF,
                get_color_for(agent), MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    private void epilog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    private void open(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF,
                get_color_for(agent), MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), get_all_spawn_agents());
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_OPEN));
    }

    // one flag is pushed and closing all the others before closing itself
    private void closing(String agent) {
        active_color = get_color_for(agent);
        if (active_color.equals(MQTT.BLUE)) blue_points++;
        else red_points++;

        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _msg_START_CLOSING));
        broadcast_score();
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, "triple_buzz"), get_all_spawn_agents());
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, active_color, MQTT.RECURRING_SCHEME_MEGA_FAST), get_all_spawn_agents());
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.SCHEME_LONG), roles.get("sirens"));

        // closing everybody
        cpFSMs.values().forEach(fsm1 -> fsm1.ProcessFSM(_msg_CLOSE));

        // create unlock job
        create_job_with_reschedule("signal_unlock", LocalDateTime.now().plusSeconds(UNLOCK_TIME), DelayedReactionJob.class, Optional.empty());
    }

    private void closed(String agent) {
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_CLOSED));
        if (active_color.equals(get_color_for(agent))) {
            send(MQTT.CMD_VISUAL, MQTT.toJSON(active_color, MQTT.RECURRING_SCHEME_MEGA_FAST), agent);
        } else {
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        }
    }

    @Override
    public void delayed_reaction(JobDataMap map) {
        open_all();
    }

    private void open_all() {
        // unlocking everyone
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF, MQTT.SIR3, MQTT.SCHEME_LONG), roles.get("sirens"));
        cpFSMs.values().forEach(fsm1 -> fsm1.ProcessFSM(_msg_OPEN));
        active_color = "";
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            if (!cpFSMs.get(agent_id).getCurrentState().equals(_state_OPEN)) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_START_CLOSING);
        } else
            super.on_external_message(agent_id, source, message);
    }

    private void cp_to_neutral(String agent) {
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}...", "...and Your Flag", "", ""),
                agent);

        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, get_color_for(agent), MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "unlocked"));
        //int index_of_agent = capture_points.indexOf(agent) + 1;
        //variables_for_score_broadcasting.put("line" + index_of_agent, "");
        broadcast_score();
    }


    private String get_color_for(String agent) {
        if (cps_for_blue.contains(agent)) return MQTT.BLUE;
        if (cps_for_red.contains(agent)) return MQTT.RED;
        return MQTT.WHITE; // error
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over",
                    "Results:",
                    "Red: ${red_points}",
                    "Blue: ${blue_points}");
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return
                    MQTT.merge(
//                            MQTT.page("page0",
//                                    "Restzeit:  ${remaining}",
//                                    "${line1}",
//                                    "${line2}",
//                                    "${line3}"),
                            MQTT.page("page0",
                                    "Restzeit:  ${remaining}",
                                    "",
                                    "Red: ${red_points}",
                                    "Blue: ${blue_points}")
                    );
            // we have only 3 lines, so we only use 3 Capture Points
        }

        return MQTT.page("page0", game_description);
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            broadcast_score();
            // send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), get_all_spawn_agents());
        }
    }


    @Override
    public String getGameMode() {
        return "signal";
    }

    @Override
    public void on_reset() {
        super.on_reset();
        // spawn agents are used as display not for a specific team
//        empty_lines();
        blue_points = 0;
        red_points = 0;
    }

//    void empty_lines() {
//        variables_for_score_broadcasting = MQTT.toJSON("line1", "", "line2", "", "line3", "", "line4", "");
//    }

    @Override
    protected JSONObject get_broadcast_vars() {
        return super.get_broadcast_vars()
                .put("red_points", red_points)
                .put("blue_points", blue_points);
    }

    @Override
    public JSONObject get_full_state() {
        JSONObject json = super.get_full_state();
        json.getJSONObject("played")
                .put("red_points", red_points)
                .put("blue_points", blue_points);
        return json;
    }


    @Override
    public boolean hasZeus() {
        return true;
    }

    @Override
    public void zeus(JSONObject params) throws IllegalStateException, JSONException {
        if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return;

        String operation = params.getString("operation").toLowerCase();

        if (operation.equalsIgnoreCase("unlock")) {
            if (active_color.isEmpty()) return;
            // correct score
            if (active_color.equals(MQTT.BLUE)) blue_points--;
            else red_points--;
            // unlock all flags
            add_in_game_event(new JSONObject()
                    .put("item", "unlock")
                    .put("agent", "all")
                    .put("state", "open")
                    .put("zeus", "intervention"));
            open_all();
        }
    }

    @Override
    public String get_in_game_event_description(JSONObject event) {
        String result = super.get_in_game_event_description(event);
        if (result.isEmpty()) {
            result = "error";
            String type = event.getString("type");

            if (type.equalsIgnoreCase("in_game_state_change")) {
                String zeus = (event.has("zeus") ? " (by the hand of ZEUS)" : "");
                if (event.getString("item").equals("unlock")) {
                    result = "Flags have been unlocked";
                }
                result += zeus;
            }
        }
        return result;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        model.addAttribute("red_points", red_points);
        model.addAttribute("blue_points", blue_points);
    }

}
