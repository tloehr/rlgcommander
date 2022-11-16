package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * defense rings have to be taken in order blue -> green -> yellow -> red maximum number of rings: 4 if we need only one
 * ring we start with red then red, yellow and so on
 */
@Log4j2
public class Stronghold extends Timed implements HasScoreBroadcast {
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_LOCKED = "LOCKED";
    public static final String _msg_LOCK = "lock";
    public static final String _msg_TAKEN = "taken";
    private int active_ring;
    private int max_number_of_rings;
    private boolean allow_defuse;
    //String[] rings = new String[]{"blu", "ylw", "grn", "red"};
    String[] rings = new String[]{"red", "ylw", "grn", "blu"};
    private HashMap<String, String> map_agent_to_ring_color;
    private JSONObject variables;

    public Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                " ____  _                         _           _     _\n" +
                "/ ___|| |_ _ __ ___  _ __   __ _| |__   ___ | | __| |\n" +
                "\\___ \\| __| '__/ _ \\| '_ \\ / _` | '_ \\ / _ \\| |/ _` |\n" +
                " ___) | |_| | | (_) | | | | (_| | | | | (_) | | (_| |\n" +
                "|____/ \\__|_|  \\___/|_| |_|\\__, |_| |_|\\___/|_|\\__,_|\n" +
                "                           |___/");

        variables = new JSONObject();
        allow_defuse = game_parameters.optBoolean("allow_defuse");
        map_agent_to_ring_color = new HashMap<>();
        List.of(rings).forEach(color -> {
            if (roles.get(color).isEmpty()) return;
            max_number_of_rings++;
            roles.get(color).forEach(agent ->
                    map_agent_to_ring_color.put(agent, color));
        });

        setGameDescription(game_parameters.getString("comment"),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Number of rings: %s", max_number_of_rings)
        );

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
                    send("acoustic", MQTT.toJSON(MQTT.SIR2, "off", MQTT.SIR3, "medium"), roles.get("sirens"));
                    return true;
                }
            });

            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    //addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
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

        if (game_fsm.getCurrentState().equals(_state_RUNNING) && roles.get(rings[active_ring]).contains(sender))
            cpFSMs.get(sender).ProcessFSM(source.toLowerCase());
        else super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
    }

    private void taken(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_TAKEN));
    }

    private void fused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_FUSED));
        if (!allow_defuse) cpFSMs.get(agent).ProcessFSM(_msg_LOCK);
        if (active_ring_taken()) {
            // the others are still locked. Need to be TAKEN now
            roles.get(rings[active_ring])
                    .stream()
                    .filter(s -> !cpFSMs.get(s).equals(_state_TAKEN)) // only those which are not already taken
                    .forEach(other_agents_in_this_ring -> cpFSMs.get(other_agents_in_this_ring).ProcessFSM(_msg_TAKEN)
                    );
            addEvent(new JSONObject().put("item", "ring").put("ring", rings[active_ring]).put("state", _state_TAKEN));
            active_ring--; // on to the next ring
            if (active_ring < 0) {
                active_ring = 0;
                game_fsm.ProcessFSM(_msg_GAME_OVER); // last ring ? we are done.
            } else activate_ring(active_ring);
            broadcast_score();
        } else {
            send("acoustic", MQTT.toJSON("sir2", "medium"), roles.get("sirens"));
        }
    }


    @Override
    public void reset_operations() {
        super.reset_operations();
        active_ring = max_number_of_rings - 1; // start backwards
        broadcast_score();
    }

    @Override
    public void run_operations() {
        super.run_operations();
        activate_ring(active_ring);
        broadcast_score();
    }

    private void activate_ring(int active_ring) {
        roles.get(rings[active_ring]).forEach(agent -> cpFSMs.get(agent).ProcessFSM(_msg_ACTIVATE));
        if (active_ring < max_number_of_rings - 1) // not with the first or only ring
            send("acoustic", MQTT.toJSON(MQTT.SIR4, "long"), roles.get("sirens"));
        addEvent(new JSONObject().put("item", "ring").put("ring", rings[active_ring]).put("state", "activated"));
    }

    private void defused(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", map_agent_to_ring_color.get(agent), "fast"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_DEFUSED));
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
        //addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_STANDBY));
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
    protected JSONObject getSpawnPages(String state) {
        if (state.matches(_state_EPILOG)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "Game Over",
                            "",
                            "Erobert",
                            "${rings_taken}")
            );
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "Restzeit:  ${remaining}",
                            "  >>  >>  >>  >>  >>",
                            "${rings_progress}",
                            ">>  >>  >>  >>  >>  "),
                    MQTT.page("page1",
                            "Restzeit:  ${remaining}",
                            ">>  >>  >>  >>  >>  ",
                            "${rings_progress}",
                            "  >>  >>  >>  >>  >>")
            );
        }
        return MQTT.page("page0", game_description);
    }


    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {

    }

    @Override
    public void broadcast_score() {
        super.broadcast_score();
        List<String> rings_taken = new ArrayList<>();
        String active = rings[active_ring];
        List<String> rings_to_go = new ArrayList<>();
        if (max_number_of_rings > 1) {
            rings_taken = Arrays.asList(Arrays.copyOfRange(rings, active_ring + 1, max_number_of_rings));
            rings_to_go = Arrays.asList(Arrays.copyOfRange(rings, 0, active_ring));
        }

        String progress = rings_taken.stream().collect(Collectors.joining(","));
        progress += " **" + active.toUpperCase() + "** ";
        progress += rings_to_go.stream().collect(Collectors.joining(","));

        variables.put("active_ring", active);
        variables.put("rings_taken", rings_taken);
        variables.put("rings_to_go", rings_to_go);
        variables.put("rings_progress", progress);

        send("vars", variables, get_all_spawn_agents());
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
//        if (operation.equalsIgnoreCase("add_seconds")) {
//            String team = params.getString("team").toLowerCase();
//            long amount = params.getLong("amount");
//            if (!team.toLowerCase().matches("blue|red")) throw new IllegalStateException("team must be blue or red");
//            scores.put("all", team, scores.get("all", team) + amount * 1000L);
//            addEvent(new JSONObject()
//                    .put("item", "add_seconds")
//                    .put("team", team)
//                    .put("amount", amount)
//                    .put("zeus", "intervention"));
//        }
//        if (operation.equalsIgnoreCase("add_respawns")) {
//            String team = params.getString("team").toLowerCase();
//            int amount = params.getInt("amount");
//            if (!team.toLowerCase().matches("blue|red")) throw new IllegalStateException("team must be blue or red");
//            if (team.equalsIgnoreCase("blue")) blue_respawns += amount;
//            if (team.equalsIgnoreCase("red")) red_respawns += amount;
//            scores.put("all", team, scores.get("all", team) + amount * 1000L);
//            addEvent(new JSONObject()
//                    .put("item", "add_respawns")
//                    .put("team", team)
//                    .put("amount", amount)
//                    .put("zeus", "intervention"));
//        }
    }

    @Override
    public JSONObject getState() {
        return MQTT.merge(super.getState(), variables);
    }
}
