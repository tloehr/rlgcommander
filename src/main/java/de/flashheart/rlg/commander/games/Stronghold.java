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
import java.util.*;

/**
 * defense rings have to be taken in order
 * blue -> green -> yellow -> red
 * maximum number of rings: 4
 * if we need only one ring we start with red
 * then red, yellow and so on
 */
@Log4j2
public class Stronghold extends Timed {
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_LOCKED = "LOCKED";
    public static final String _msg_LOCK = "lock";
    public static final String _msg_TAKEN = "taken";
    private int active_ring;
    private int max_number_of_rings;
    private boolean lock_taken_flags;
    String[] rings = new String[]{"red", "ylw", "grn", "blu"};
    private HashMap<String, String> map_agent_to_ring_color;

    Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                " ____  _                         _           _     _\n" +
                "/ ___|| |_ _ __ ___  _ __   __ _| |__   ___ | | __| |\n" +
                "\\___ \\| __| '__/ _ \\| '_ \\ / _` | '_ \\ / _ \\| |/ _` |\n" +
                " ___) | |_| | | (_) | | | | (_| | | | | (_) | | (_| |\n" +
                "|____/ \\__|_|  \\___/|_| |_|\\__, |_| |_|\\___/|_|\\__,_|\n" +
                "                           |___/");

        lock_taken_flags = game_parameters.optBoolean("lock_taken_flags");
        map_agent_to_ring_color = new HashMap<>();
        List.of(rings).forEach(color -> {
            if (!roles.get(color).isEmpty()) max_number_of_rings++;
            roles.get(color).forEach(agent ->
                    map_agent_to_ring_color.put(color, agent));
        });
    }

    @Override
    public FSM create_CP_FSM(String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/stronghold.xml"), null);
            fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> prolog(agent));
            fsm.setStatesAfterTransition(_state_STANDBY, (state, obj) -> standby(agent));
            fsm.setStatesAfterTransition(_state_DEFUSED, (state, obj) -> defused(agent));
            fsm.setStatesAfterTransition(_state_FUSED, (state, obj) -> fused(agent));
            fsm.setStatesAfterTransition(_state_TAKEN, (state, obj) -> taken(agent));

            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
                    if (active_ring > 0)
                        send("acoustic", MQTT.toJSON(MQTT.SIR2, "long"), roles.get("sirens"));
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
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

    private void taken(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
    }

    private void fused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "fast"), agent);
        if (lock_taken_flags) cpFSMs.get(agent).ProcessFSM(_msg_LOCK);
        if (active_ring_taken()) {
            roles.get(rings[active_ring]).forEach(other_agents_in_this_ring -> {
                cpFSMs.get(other_agents_in_this_ring).ProcessFSM(_msg_TAKEN);
            });
            active_ring++;
            if (active_ring == max_number_of_rings) game_fsm.ProcessFSM(_msg_GAME_OVER);
        }
    }

    private void defused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", map_agent_to_ring_color.get(agent), "fast"), agent);
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
    }

    private void prolog(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", map_agent_to_ring_color.get(agent), "normal"), agent);
    }

    private boolean active_ring_taken() {
        return roles.get(rings[active_ring]).stream()
                .allMatch(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_FUSED + "|" + _state_LOCKED + "|" + _state_TAKEN));
    }

    @Override
    public void run_operations() {
        super.run_operations();
    }

    @Override
    public void reset_operations() {
        super.reset_operations();
        active_ring = 0;
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        return null;
    }

    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {

    }
}
