package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import de.flashheart.rlg.commander.misc.DOTWriter;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class Meshed extends WithRespawns implements HasScoreBroadcast {

    final MutableGraph<String> mesh;
    private final HashMap<String, String> dot_format_map;
    private final HashMap<String, String> spawn_agent_to_color;

    public Meshed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        dot_format_map = new HashMap<>();
        spawn_agent_to_color = new HashMap<>();

        spawn_segments.column(active_segment).entrySet().forEach(stringPairEntry -> spawn_agent_to_color.put(stringPairEntry.getValue().getLeft(),
                StringUtils.substringBefore(stringPairEntry.getKey(), "_")));

        // create mesh structure
        mesh = GraphBuilder.undirected().build();
        // cp agents first
        roles.get("capture_points").forEach(agent -> mesh.addNode(agent));
        // warpgates represented by spawn agents
        // we have only one spawn segment
        // agent name on the left (key part of Pair)
        mesh.addNode(spawn_segments.get("red_spawn", 0).getLeft());
        mesh.addNode(spawn_segments.get("blue_spawn", 0).getLeft());
        mesh.addNode(spawn_segments.get("yellow_spawn", 0).getLeft());

        // to color the spawn agents
        dot_format_map.put(spawn_segments.get("red_spawn", 0).getLeft(), "[fillcolor=red fontcolor=yellow style=filled shape=box]");
        dot_format_map.put(spawn_segments.get("blue_spawn", 0).getLeft(), "[fillcolor=blue fontcolor=yellow style=filled shape=box]");
        dot_format_map.put(spawn_segments.get("yellow_spawn", 0).getLeft(), "[fillcolor=yellow fontcolor=black style=filled shape=box]");

        game_parameters.getJSONArray("mesh").forEach(entry -> {
            JSONArray connection = (JSONArray) entry;
            mesh.putEdge(connection.getString(0), connection.getString(1));
        });

        log.debug(DOTWriter.write(mesh, dot_format_map));
//
//        log.debug(mesh);
//        log.debug(mesh.adjacentNodes("ag01"));
//        log.debug(mesh.adjacentNodes("ag02"));
//        get_valid_cp_states_for("ag01");
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            // don't care about 0 and 1
            //String[] fsms = new String[]{"", "", "conquest", "meshed3", "meshed4"}; // file names of cp FSMs definitions - see resources/games
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/meshed3.xml"), null);

            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                broadcast_score();
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED", "YELLOW", "GREEN"))), (state, obj) -> {
                switch_cp(agent, state);
                broadcast_score();
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

        if (cpFSMs.containsKey(sender) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
            String transition_message = get_FSM_color_change_message(sender);
            if (!transition_message.isEmpty())
                cpFSMs.get(sender).ProcessFSM(transition_message);
        } else
            super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
    }


    private Set<String> get_valid_cp_states_for(String agent) {
        return new HashSet<>(List.of("RED", "BLUE", "YELLOW"));
    }

    /**
     * creates the necessary message for allowed state transition
     *
     * @param agent
     * @return
     */
    private String get_FSM_color_change_message(String agent) {
        List<String> valid_states = new ArrayList<>(get_valid_cp_states_for_tmp(agent));
        if (valid_states.isEmpty()) return "";
        Collections.sort(valid_states);

        String current_state = cpFSMs.get(agent).getCurrentState();

        int idx_state = 0;
        if (!current_state.equals("NEUTRAL")) {
            idx_state = valid_states.indexOf(current_state) + 1;
            if (idx_state >= valid_states.size()) idx_state = 0;
        }

        return "to_" + valid_states.get(idx_state).toLowerCase();

    }

    /**
     * checks the adjacent agents for this CP and
     * creates a list of states that may be set by the player.
     * If no state change is allowed (when agent is deeply in own territory)
     * the list is empty.
     * <p>
     * can be switched to a color, when at least connected to one agent with the same color or home spawn
     *
     * @param agent
     * @return
     */
    private Set<String> get_valid_cp_states_for_tmp(String agent) {
        Set<String> adjacent = mesh.adjacentNodes(agent);
        // first get allowed states from al adjacent capture points
        final Set<String> allowed_states = adjacent.stream().filter(cpFSMs::containsKey)
                .map(adj_agents -> cpFSMs.get(adj_agents).getCurrentState().toUpperCase()).collect(Collectors.toSet());
        allowed_states.remove("NEUTRAL");

        // then check if there are spawns. add those colors, too.
        team_registry.forEach((s, team) -> log.debug(s, team));



        //adjacent.stream().filter(get_all_spawn_agents()::contains).forEach(spawn -> allowed_states.add(spawn.));

        // remove neutral from state list

        if (allowed_states.size() == 1 && allowed_states.contains(cpFSMs.get(agent).getCurrentState()))
            allowed_states.clear(); // nothing to change

        return allowed_states;
    }


    @Override
    protected void on_respawn_signal_received(String spawn, String agent) {
        super.on_respawn_signal_received(spawn, agent);
        log.debug(team_registry.toString());
    }


    private void cp_to_neutral(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
        dot_format_map.remove(agent);
    }

    private void switch_cp(String agent, String state) {
        String led_color = switch (state) {
            case "BLUE" -> MQTT.BLUE;
            case "GREEN" -> MQTT.GREEN;
            case "RED" -> MQTT.RED;
            case "YELLOW" -> MQTT.YELLOW;
            default -> "error";
        };

        String foreground_color = switch (state) {
            case "BLUE" -> "YELLOW";
            case "GREEN" -> "WHITE";
            case "RED" -> "YELLOW";
            case "YELLOW" -> "BLACK";
            default -> "error";
        };

        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", led_color, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject()
                .put("item", "capture_point")
                .put("agent", agent)
                .put("state", state)
        );

        dot_format_map.put(agent, String.format("[fillcolor=%s fontcolor=%s style=filled]", state.toLowerCase(),
                foreground_color.toLowerCase()));

    }

    @Override
    public String getGameMode() {
        return "meshed";
    }

    @Override
    public void broadcast_score() {
        log.debug(DOTWriter.write(mesh, dot_format_map));
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        model.addAttribute("vizdot", DOTWriter.write(mesh, dot_format_map));
    }
}
