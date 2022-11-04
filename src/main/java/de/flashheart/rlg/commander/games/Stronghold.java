package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

/**
 * defense rings have to be taken in order blue -> green -> yellow -> red maximum number of rings: 4 if we need only one
 * ring we start with red then red, yellow and so on
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

    public Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
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
            if (roles.get(color).isEmpty()) return;
            max_number_of_rings++;
            roles.get(color).forEach(agent ->
                    map_agent_to_ring_color.put(agent, color));
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

            // DEFUSED => FUSED
            fsm.setAction(_state_FUSED, _msg_BUTTON_01, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // the siren is activated on the message NOT on the state, so it won't be activated when the game starts only when the flag has been defused.
                    send("acoustic", MQTT.toJSON(MQTT.SIR2, "off", MQTT.SIR3, "1:on,2000;off,1"), roles.get("sirens"));
                    //send("play", MQTT.toJSON("subpath", "announce", "soundfile", "shutdown"), get_active_spawn_agents());
                    return true;
                }
            });

            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
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
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_TAKEN));
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
    }

    private void fused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "fast"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_FUSED));
        if (lock_taken_flags) cpFSMs.get(agent).ProcessFSM(_msg_LOCK);
        if (active_ring_taken()) {
            roles.get(rings[active_ring])
                    .stream()
                    .filter(s -> !cpFSMs.get(s).equals(_state_TAKEN)) // only those which are not already taken
                    .forEach(other_agents_in_this_ring -> cpFSMs.get(other_agents_in_this_ring).ProcessFSM(_msg_TAKEN)
                    );
            active_ring++; // on to the next ring
            if (active_ring == max_number_of_rings) game_fsm.ProcessFSM(_msg_GAME_OVER); // last ring ? we are done.
            activate_ring(active_ring);
        } else {
            send("acoustic", MQTT.toJSON("sir2", "short"), roles.get("sirens"));
        }
    }

    private void activate_ring(int active_ring) {
        roles.get(rings[active_ring]).forEach(agent -> cpFSMs.get(agent).ProcessFSM(_msg_ACTIVATE));
    }

    private void defused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", map_agent_to_ring_color.get(agent), "fast"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_DEFUSED));
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_STANDBY));
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
    public void reset_operations() {
        super.reset_operations();
        active_ring = 0;
    }

    @Override
    public void run_operations() {
        super.run_operations();
        activate_ring(active_ring);
    }

    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {

    }
}
