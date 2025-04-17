package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Lists;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.FlagTimerJob;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
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
public class Street extends Timed {
    protected final List<String> capture_points;
    private final LinkedList<String> taken_points;
    private int active_capture_point;
    private boolean allow_pushback;

    public Street(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        this.capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(Object::toString).sorted().collect(Collectors.toList());
        this.allow_pushback = game_parameters.optBoolean("allow_pushback", true);
        this.taken_points = new LinkedList<>();
        this.active_capture_point = 0;
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/street.xml"), null);
            fsm.setStatesAfterTransition(_flag_state_PROLOG, (state, obj) -> cp_prolog(agent));
            fsm.setStatesAfterTransition(_flag_state_INACTIVE, (state, obj) -> cp_to_inactive(agent));
            fsm.setStatesAfterTransition(_flag_state_ACTIVE, (state, obj) ->
                    send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_NORMAL), agent));
            fsm.setStatesAfterTransition(_flag_state_LOCKED, (state, obj) -> locked(agent));

            fsm.setAction(_flag_state_ACTIVE, _msg_TAKE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    take(agent);
                    return true;
                }
            });

            fsm.setAction(_flag_state_LOCKED, _msg_UNLOCK, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    unlock(agent);
                    return true;
                }
            });

            fsm.setAction(_flag_state_TAKEN, _msg_REACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    taken_points.pop();
                    if (active_capture_point > 1)
                        cpFSMs.get(capture_points.get(active_capture_point - 2)).ProcessFSM(_msg_UNLOCK);
                    send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SHUTDOWN_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
                    final String active_agent = capture_points.get(active_capture_point);
                    cpFSMs.get(active_agent).ProcessFSM(_msg_DEACTIVATE);
                    add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", active_agent).put("state", _msg_DEACTIVATE));
                    active_capture_point--;
                    return true;
                }
            });


            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    protected void cp_prolog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "PROLOG"),
                agent);
    }

    protected void cp_to_inactive(String agent) {
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is INactive"),
                agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
    }


    protected void unlock(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.GREEN, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _msg_UNLOCK));
    }

    protected void take(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.GREEN, MQTT.RECURRING_SCHEME_NORMAL), agent);

        taken_points.push(agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _flag_state_TAKEN));

        if (!is_first(agent))
            cpFSMs.get(capture_points.get(active_capture_point - 1)).ProcessFSM(_msg_LOCK);

        if (!is_last(agent)) {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.ALERT_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
            active_capture_point++;
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
            broadcast_score();
        } else {
            game_fsm.ProcessFSM(_msg_GAME_OVER); // the game is over now. no siren needed.
        }
    }

    protected void locked(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        if (allow_pushback)
            add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _flag_state_LOCKED));
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            final FSM fsm = cpFSMs.get(agent_id);

            if (fsm.getCurrentState().equals(_flag_state_ACTIVE)) fsm.ProcessFSM(_msg_TAKE);
            else if (allow_pushback && fsm.getCurrentState().equals(_flag_state_TAKEN)) fsm.ProcessFSM(_msg_REACTIVATE);
        } else super.on_external_message(agent_id, source, message);
    }

    @Override
    public void on_run() {
        super.on_run();
        active_capture_point = 0;
        // run the first flag
        cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
    }

    @Override
    public void on_reset() {
        super.on_reset();
        taken_points.clear();
        active_capture_point = 0;
    }

    private boolean is_last(String agent) {
        return capture_points.indexOf(agent) == capture_points.size() - 1;
    }

    private boolean is_first(String agent) {
        return capture_points.indexOf(agent) == 0;
    }

    @Override
    public String getGameMode() {
        return "street";
    }

    @Override
    protected JSONObject get_broadcast_vars() {
        JSONObject vars = super.get_broadcast_vars();
        vars.put("num_flags", cpFSMs.size());
        vars.put("taken", taken_points.size());
        return vars;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        ArrayList<String> points_to_go = new ArrayList<>(capture_points);
        final String active_point = capture_points.get(active_capture_point);
        points_to_go.removeAll(taken_points);
        points_to_go.remove(active_point);
        model.addAttribute("capture_points", capture_points);
        model.addAttribute("active_point", active_point);
        model.addAttribute("taken_points", taken_points.stream().sorted().collect(Collectors.toList()));
        model.addAttribute("points_to_go", points_to_go);
        model.addAttribute("allow_pushback", allow_pushback);
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0",
                    "Game Over         ${wifi_signal}",
                    "",
                    "Taken ${taken}/${num_flags}",
                    "");
        }
        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "Time: ${remaining}  ${wifi_signal}",
                            "",
                            "",
                            "Taken ${taken}/${num_flags}")
            );
        }
        return MQTT.page("page0", game_description);
    }
}
