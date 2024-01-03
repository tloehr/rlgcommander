package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
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

    private final String FLAG_IMMUTABLE = "flag_immutable";
    private final String FLAG_MUTABLE = "flag_mutable";
    private final String ADJACENT_COLOR_HINT = "adjacent_color_hint";

    final MutableGraph<String> mesh;
    private final HashMap<String, String> dot_format_map;
    private final HashMap<String, String> spawn_agent_to_color;


    public Meshed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        dot_format_map = new HashMap<>();
        spawn_agent_to_color = new HashMap<>();

        spawn_segments.column(active_segment)
                .entrySet()
                .forEach(stringPairEntry -> spawn_agent_to_color.put(stringPairEntry.getValue().getLeft(),
                        StringUtils.substringBefore(stringPairEntry.getKey(), "_"))
                );

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

    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            // don't care about 0 and 1
            //String[] fsms = new String[]{"", "", "conquest", "meshed3", "meshed4"}; // file names of cp FSMs definitions - see resources/games
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/meshed3.xml"), null);

            fsm.setStatesAfterTransition(new ArrayList<>(List.of(_flag_state_NEUTRAL, _flag_state_PROLOG)), (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition(new ArrayList<>(_flag_ALL_RUNNING_STATES), (state, obj) -> {
                switch_cp(agent, state);
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
        send("visual", MQTT.toJSON(MQTT.LED_ALL, "off", MQTT.WHITE, get_signal(FLAG_MUTABLE)), agent);
        dot_format_map.remove(agent);
    }

    /**
     * handles everything when a flag is changing its state
     *
     * @param agent
     * @param state
     */
    private void switch_cp(String agent, String state) {
        send("visual", MQTT.toJSON(get_flag_blinking_scheme(agent, state)), agent);
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
        dot_format_map.put(agent, String.format("[fillcolor=%s fontcolor=%s style=filled]", state.toLowerCase(),
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
        List<String> allowed_states = new ArrayList<>(get_states_from_adjacent_flags(agent));
        if (allowed_states.isEmpty()) return "";
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
        Set<String> adjacent = mesh.adjacentNodes(agent);
        // first get allowed states from al adjacent capture points
        final Set<String> allowed_states = adjacent.stream().filter(cpFSMs::containsKey)
                .map(adj_agents -> cpFSMs.get(adj_agents).getCurrentState().toUpperCase()).collect(Collectors.toSet());

        // now we add adjacent spawn colors
        adjacent.stream()
                .filter(adj_agent -> get_active_spawn_agents().contains(adj_agent))
                .forEach(adj_agent -> allowed_states.add(spawn_agent_to_color.get(adj_agent).toUpperCase()));

        log.debug(allowed_states);

        allowed_states.remove("NEUTRAL");

        if (allowed_states.size() == 1 && allowed_states.contains(cpFSMs.get(agent).getCurrentState()))
            allowed_states.clear(); // nothing to change

        return allowed_states;
    }


    @Override
    protected void on_respawn_signal_received(String spawn, String agent_id) {
        super.on_respawn_signal_received(spawn, agent_id);
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
                        send("visual", MQTT.toJSON(get_flag_blinking_scheme(agent, cpFSMs.get(agent).getCurrentState())), agent));
    }

    /**
     * this method creates an array of MQTT visual messages to show the current flag state
     * and all optional colors as a short hint.
     *
     * @param agent
     * @param current_flag_state
     * @return
     */
    private String[] get_flag_blinking_scheme(String agent, String current_flag_state) {
        Set<String> adj_colors = get_states_from_adjacent_flags(agent);
        String active_state_scheme = get_signal(adj_colors.isEmpty() ? FLAG_IMMUTABLE : FLAG_MUTABLE);

        log.debug(agent);

        // construct appropriate blinking scheme for active state
        List<String> signal_pairs = new ArrayList<>(List.of(MQTT.LED_ALL, "off", get_led_color_for_flage_state(current_flag_state), active_state_scheme));
        // add hints for all possible colors from adjacent flags
        // except the current_flag_state
        adj_colors.stream()
                .filter(state -> !state.equals(current_flag_state))
                .forEach(state -> {
                            signal_pairs.add(get_led_color_for_flage_state(state));
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
    private String get_led_color_for_flage_state(String state) {
        return switch (state) {
            case _flag_state_RED -> MQTT.RED;
            case _flag_state_YELLOW -> MQTT.YELLOW;
            case _flag_state_GREEN -> MQTT.GREEN;
            case _flag_state_BLUE -> MQTT.BLUE;
            default -> MQTT.WHITE;
        };
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
