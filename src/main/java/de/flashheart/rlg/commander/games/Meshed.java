package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.graph.*;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import de.flashheart.rlg.commander.misc.DOTWriter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class Meshed extends WithRespawns implements HasScoreBroadcast {

    // key names for blinking schemes as defined in meshed.json
    private final String FLAG_IMMUTABLE = "flag_immutable";
    private final String FLAG_MUTABLE = "flag_mutable";
    private final String ADJACENT_COLOR_HINT = "adjacent_color_hint";

    private final String red_spawn, blue_spawn, yellow_spawn;
    private final JSONArray json_mesh;

    final MutableValueGraph<String, String> mesh;

    private final HashMap<String, String> map_dot_format;
    private final HashMap<String, String> map_spawn_agent_to_its_teamcolor;
    private final HashMap<String, Set<String>> map_of_neighbourhood_states_for_agents;

    final Map<String, String> spawn_agents_fake_states;

    public Meshed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        map_dot_format = new HashMap<>();
        map_spawn_agent_to_its_teamcolor = new HashMap<>();
        map_of_neighbourhood_states_for_agents = new HashMap<>();

        spawn_segments.stream()
                .filter(o -> o.getValue1() == active_segment)
                .forEach(o2 -> map_spawn_agent_to_its_teamcolor.put(o2.getValue2(),
                        StringUtils.substringBefore(o2.getValue0(), "_"))
                );

        // create mesh structure
        mesh = ValueGraphBuilder.directed().build();
        // cp agents first
        roles.get("capture_points").forEach(agent -> {
            mesh.addNode(agent);
        });
        // warp_gates represented by spawn agents
        // we have only one spawn segment
        // agent name on the left (key part of Pair)

        red_spawn = get_spawn_node_for("red_spawn");
        blue_spawn = get_spawn_node_for("blue_spawn");
        yellow_spawn = get_spawn_node_for("yellow_spawn");

        spawn_agents_fake_states = Map.of(red_spawn, _flag_state_RED, yellow_spawn, _flag_state_YELLOW, blue_spawn, _flag_state_BLUE);

        json_mesh = game_parameters.getJSONArray("mesh");
        //init_graph();

        //log.debug(DOTWriter.write(mesh, map_dot_format));
    }

    /**
     * prepare and init graph representation
     */
    private void init_graph(){
        map_dot_format.clear();
        // to color the spawn agents
        map_dot_format.put(red_spawn, "[fillcolor=red fontcolor=yellow style=filled shape=box]");
        map_dot_format.put(blue_spawn, "[fillcolor=blue fontcolor=yellow style=filled shape=box]");
        map_dot_format.put(yellow_spawn, "[fillcolor=yellow fontcolor=black style=filled shape=box]");

        // now add all edges as defined in game_parameters
        json_mesh.forEach(entry -> {
            JSONArray connection = (JSONArray) entry;
            String agent1 = connection.getString(0);
            String agent2 = connection.getString(1);

            if (spawn_agents_fake_states.containsKey(agent1)) {
                // this is a spawn agent. The outgoing edge will be set to it's color
                mesh.putEdgeValue(agent1, agent2, spawn_agents_fake_states.get(agent1));
                // and add it to the format map
                map_dot_format.put(agent1 + "," + agent2, String.format("[color=%s]", mesh.edgeValue(agent1, agent2)));
                mesh.putEdgeValue(agent2, agent1, _flag_state_NEUTRAL);
            } else if (spawn_agents_fake_states.containsKey(agent2)) {
                // same as above, but the other way round
                mesh.putEdgeValue(agent2, agent1, spawn_agents_fake_states.get(agent2));
                map_dot_format.put(agent2 + "," + agent1, String.format("[color=%s]", mesh.edgeValue(agent2, agent1)));
                mesh.putEdgeValue(agent1, agent2, _flag_state_NEUTRAL);
            } else {
                mesh.putEdgeValue(agent2, agent1, _flag_state_NEUTRAL);
                mesh.putEdgeValue(agent1, agent2, _flag_state_NEUTRAL);
            }

            // prepare neighbourhood map
            map_of_neighbourhood_states_for_agents.putIfAbsent(agent1, new HashSet<>());
            map_of_neighbourhood_states_for_agents.putIfAbsent(agent2, new HashSet<>());
        });

        map_of_neighbourhood_states_for_agents.remove(red_spawn);
        map_of_neighbourhood_states_for_agents.remove(blue_spawn);
        map_of_neighbourhood_states_for_agents.remove(yellow_spawn);
    }

    /**
     * refreshes the current neighbourhood states for all nodes.
     * This map is refreshed when the game resets and after every flag_state change
     */
    private void refresh_neighbourhood_state_map() {
        final Set<String> NEUTRAL_STATES = Set.of(_flag_state_NEUTRAL, _flag_state_PROLOG);
        // empty all state sets of capture points first. Leave the spawn's sets intact.
        mesh.nodes().stream().filter(agent -> roles.get("capture_points").contains(agent)).forEach(cp -> {
            map_of_neighbourhood_states_for_agents.get(cp).clear();
        });
        // now refill all sets
        mesh.nodes().stream().filter(agent -> roles.get("capture_points").contains(agent)).forEach(agent ->
                mesh.adjacentNodes(agent)
                        // we don't care for neutral neighbours, so we filter them out
                        // if a state is inside the spawn_agents_fake_map then we will use this otherwise a cp
                        .forEach(neighbour -> {
                            // this is a spawn
                            if (spawn_agents_fake_states.containsKey(neighbour))
                                map_of_neighbourhood_states_for_agents.get(agent).add(spawn_agents_fake_states.get(neighbour));
                            else { // this is a regular capture point. We never add NEUTRAL states to the list
                                // as it is not allowed to switch a CP back
                                String this_neighbours_state = cpFSMs.get(neighbour).getCurrentState();
                                if (!NEUTRAL_STATES.contains(this_neighbours_state))
                                    map_of_neighbourhood_states_for_agents.get(agent).add(this_neighbours_state);
                            }
                        })
        );
        log.debug(map_of_neighbourhood_states_for_agents);
    }

    @Override
    public void on_reset() {
        super.on_reset();
        init_graph();
        refresh_neighbourhood_state_map();
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/meshed3.xml"), null);
            fsm.setStatesAfterTransition(new ArrayList<>(List.of(_flag_state_NEUTRAL, _flag_state_PROLOG)), (state, obj) -> {
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition(new ArrayList<>(_flag_ALL_RUNNING_STATES), (state, obj) -> {
                switch_cp(agent, state);
                refresh_neighbourhood_state_map();
                update_adjacent_flags_to(agent);
                broadcast_score();
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_SLOW), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        map_dot_format.remove(agent);
    }


    /**
     * handles everything when a flag is changing its state
     *
     * @param agent
     * @param state
     */
    private void switch_cp(String agent, String state) {
        send("visual", MQTT.toJSON(get_flag_blinking_scheme_for(agent, state)), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);

        // add in game event to the event list
        add_in_game_event(new JSONObject()
                .put("item", "capture_point")
                .put("agent", agent)
                .put("state", state)
        );

        // create a dot format line for the graph of this agent
        String font_foreground_color = switch (state) {
            case "BLUE", "RED" -> "YELLOW";
            case "GREEN" -> "WHITE";
            default -> "BLACK"; // includes YELLOW
        };
        map_dot_format.put(agent, String.format("[fillcolor=%s fontcolor=%s style=filled]", state.toLowerCase(),
                font_foreground_color.toLowerCase()));
    }

    @Override
    public void process_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            String transition_message = get_FSM_color_change_message(agent_id);
            if (!transition_message.isEmpty())
                cpFSMs.get(agent_id).ProcessFSM(transition_message);
        } else
            super.process_external_message(agent_id, source, message);
    }

    /**
     * when the player presses the flag button,
     * we want to cycle to the next color. But in meshed
     * it may be not possible to change it or restricted to
     * certain colors.
     * <p/>
     * this method returns the FSM message to switch the flag
     * to the -next- color. it sorts all possible options
     * alphabetically first.
     *
     * @param agent the agent to switch
     * @return the message to change the flag to the next color. if empty, no change is allowed
     */
    private String get_FSM_color_change_message(String agent) {
        if (map_of_neighbourhood_states_for_agents.get(agent).isEmpty()) return "";
        List<String> allowed_states = new ArrayList<>(map_of_neighbourhood_states_for_agents.get(agent));
        Collections.sort(allowed_states);
        String current_state = cpFSMs.get(agent).getCurrentState();

        int idx_state = 0;
        if (!current_state.equals(_flag_state_NEUTRAL)) {
            idx_state = allowed_states.indexOf(current_state) + 1;
            if (idx_state >= allowed_states.size()) idx_state = 0;
        }

        return "to_" + allowed_states.get(idx_state).toLowerCase();
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
    private Set<String> get_states_from_adjacent_flags(String agent) {
        log.debug("get_states_from_adjacent_flags({})", agent);
        Set<String> adjacent = mesh.adjacentNodes(agent);
        log.debug("adjacent nodes for {} are :{}", agent, adjacent);

        // first get allowed states from al adjacent capture points
        final Set<String> allowed_states = adjacent.stream().filter(cpFSMs::containsKey)
                .map(adj_agents -> cpFSMs.get(adj_agents).getCurrentState().toUpperCase()).collect(Collectors.toSet());
        log.debug("allowed states for {} are :{}", agent, allowed_states);

        // now we add adjacent spawn colors
        adjacent.stream()
                .filter(adj_agent -> get_active_spawn_agents().contains(adj_agent))
                .forEach(adj_agent -> allowed_states.add(map_spawn_agent_to_its_teamcolor.get(adj_agent).toUpperCase()));

        log.debug(allowed_states);

        allowed_states.remove("NEUTRAL");

        if (allowed_states.size() == 1 && allowed_states.contains(cpFSMs.get(agent).getCurrentState()))
            allowed_states.clear(); // nothing to change

        return allowed_states;
    }

    /**
     * when a flag changes, the adjacent flags may need to hint to the new possibility.
     * so we need to send them an update.
     *
     * @param agent
     */
    private void update_adjacent_flags_to(String agent) {
        mesh.adjacentNodes(agent)
                .forEach(neighbour ->
                        send("visual",
                                MQTT.toJSON(get_flag_blinking_scheme_for(agent)), agent));
    }

    private String[] get_flag_blinking_scheme_for(String agent) {
        return get_flag_blinking_scheme_for(agent, cpFSMs.get(agent).getCurrentState());
    }

    /**
     * this method creates an array of MQTT visual messages to show the current flag state
     * and all optional colors as a short hint.
     *
     * @param agent
     * @param current_flag_state
     * @return
     */
    private String[] get_flag_blinking_scheme_for(String agent, String current_flag_state) {
        //Set<String> adj_colors = get_states_from_adjacent_flags(agent);
        //String active_state_scheme = get_signal(adj_colors.isEmpty() ? FLAG_IMMUTABLE : FLAG_MUTABLE);

        log.debug(agent);


        // construct appropriate blinking scheme for active state
        // active states blink slowly
        List<String> signal_pairs = new ArrayList<>(List.of(MQTT.LED_ALL, MQTT.OFF, get_led_color_for_flag_state(current_flag_state), MQTT.RECURRING_SCHEME_SLOW));
        // add hints for all possible colors from adjacent flags
        // except the current_flag_state
        map_of_neighbourhood_states_for_agents.get(agent)
                .stream().filter(state -> !state.equals(current_flag_state))
                .forEach(state -> {
                            signal_pairs.add(get_led_color_for_flag_state(state));
                            signal_pairs.add(get_signal(ADJACENT_COLOR_HINT));
                        }
                );

        return signal_pairs.toArray(new String[]{});
    }

    /**
     * returns the LED color code for a flag state
     *
     * @param state the flag state
     * @return LED color code for a flag state
     */
    private String get_led_color_for_flag_state(String state) {
        return switch (state) {
            case _flag_state_RED -> MQTT.RED;
            case _flag_state_YELLOW -> MQTT.YELLOW;
            case _flag_state_GREEN -> MQTT.GREEN;
            case _flag_state_BLUE -> MQTT.BLUE;
            default -> MQTT.WHITE; // includes PROLOG and NEUTRAL
        };
    }

    @Override
    public String getGameMode() {
        return "meshed";
    }

    @Override
    public void broadcast_score() {
        log.debug(DOTWriter.write(mesh, map_dot_format));
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        model.addAttribute("vizdot", DOTWriter.write(mesh, map_dot_format));
    }
}
