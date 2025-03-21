package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class Street extends Timed {
    public static final String _msg_GO = "go";
    public static final String _flag_state_TAKEN = "TAKEN";
    protected final List<String> capture_points;
    private int active_capture_point;

    public Street(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(Object::toString).collect(Collectors.toList());
        this.active_capture_point = 0;
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/street.xml"), null);
            fsm.setStatesAfterTransition(_flag_state_PROLOG, (state, obj) -> cp_prolog(agent));
            fsm.setStatesAfterTransition(_flag_state_STAND_BY, (state, obj) -> cp_to_stand_by(agent));
            fsm.setStatesAfterTransition(_flag_state_ACTIVE, (state, obj) -> cp_to_active(agent));
            fsm.setStatesAfterTransition(_flag_state_TAKEN, (state, obj) -> taken(agent));
//            fsm.setAction(_flag_state_GET_READY, _msg_GO, new FSMAction() {
//                @Override
//                public boolean action(String curState, String message, String nextState, Object args) {
//                    send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.EVENT_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
//                    return true;
//                }
//            });

            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    protected void cp_prolog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "PROLOG"),
                agent);
    }

    protected void cp_to_stand_by(String agent) {
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag standing by"),
                agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
    }

    protected void cp_to_active(String agent) {
        if (active_capture_point > 0) // after the first flag has been taken, not earlier.
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.EVENT_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_NORMAL), agent);
    }

    protected void taken(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.GREEN, MQTT.RECURRING_SCHEME_NORMAL), agent);
        if (!is_last(agent)) {
            // the game is over now. no siren needed.
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.EVENT_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
            active_capture_point++;
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_GO);
            broadcast_score();
        } else game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (capture_points.contains(agent_id)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else super.on_external_message(agent_id, source, message);
    }

    @Override
    public void on_run() {
        super.on_run();
        active_capture_point = 0;
        // run the first flag
        cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_GO);
    }

    @Override
    public void on_reset() {
        super.on_reset();
        active_capture_point = 0;
    }

    private boolean is_last(String agent) {
        return capture_points.indexOf(agent) == capture_points.size() - 1;
    }

    @Override
    public String getGameMode() {
        return "street";
    }


    protected JSONObject get_broadcast_vars() {
        JSONObject vars = new JSONObject();
        long taken_flags = cpFSMs.values().stream().filter(fsm -> fsm.getCurrentState().equals(_flag_state_TAKEN)).count();
        //BigDecimal bd_taken = new BigDecimal(num_flags);
        vars.put("num_flags", cpFSMs.size());
        vars.put("taken", taken_flags);
        //vars.put("percent", bd_taken.divide(new BigDecimal(cpFSMs.size()), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
        return vars;
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
                            "Taken ${taken}/${num_flags}",
                            "${percent}%",
                            "${remaining}"),
                    MQTT.page("page1",
                            "Förtschritt   ${wifi_signal}",
                            "Erobert ${taken}/${num_flags}",
                            "${percent}%",
                            "${remaining}"),
                    MQTT.page("page2",
                            "Прогресс   ${wifi_signal}",
                            "завоеван ${taken}/${num_flags}",
                            "${percent}%",
                            "${remaining}")
            );
        }
        return MQTT.page("page0", game_description);
    }
}
