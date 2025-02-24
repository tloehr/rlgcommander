package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
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
    // intermediate step to separate from activation -> defused transition
    public static final String _state_NEARLY_DEFUSED = "NEARLY_DEFUSED";
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_LOCKED = "LOCKED";
    public static final String _msg_LOCK = "lock";
    public static final String _msg_TAKEN = "taken";
    public static final String _msg_DEFUSE = "defuse";
    private String active_ring;
    private boolean allow_defuse;
    LinkedList<String> rings_in_progress;
    //String[] rings = new String[]{"red", "ylw", "grn", "blu"};
    ArrayList<String> rings_taken, rings_in_use;
    private HashMap<String, String> map_agent_to_ring_color;

    public Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        assert_two_teams_red_and_blue();
        rings_in_progress = new LinkedList<>();
        rings_taken = new ArrayList<>();
        rings_in_use = new ArrayList<>();
        allow_defuse = game_parameters.optBoolean("allow_defuse");
        map_agent_to_ring_color = new HashMap<>();

        List.of("blu", "grn", "ylw", "red").forEach(color -> {
            if (roles.get(color).isEmpty()) return;
            rings_in_use.add(color);
            roles.get(color).forEach(agent ->
                    map_agent_to_ring_color.put(agent, color));
        });

        setGameDescription(game_parameters.getString("comment"),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Number of rings: %s", rings_in_use.size()),
                " ".repeat(18) + "${wifi_signal}"
        );

    }

    @Override
    public FSM create_CP_FSM(String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/stronghold.xml"), null);
            fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> prolog(agent));
            fsm.setStatesAfterTransition(_state_STANDBY, (state, obj) -> standby(agent));
            fsm.setStatesAfterTransition(_state_DEFUSED, (state, obj) -> defused(agent));
            fsm.setStatesAfterTransition(_state_NEARLY_DEFUSED, (state, obj) -> nearly_defused(agent));
            fsm.setStatesAfterTransition(_state_FUSED, (state, obj) -> fused(agent));
            fsm.setStatesAfterTransition(_state_FUSED, (state, obj) -> fused(agent));
            fsm.setStatesAfterTransition(_state_TAKEN, (state, obj) -> taken(agent));

            // DEFUSED => FUSED
//            fsm.setAction(_state_FUSED, _msg_BUTTON_01, new FSMAction() {
//                @Override
//                public boolean action(String curState, String message, String nextState, Object args) {
//                    // the siren is activated on the message NOT on the state, so it won't be activated when the game starts only when the flag has been defused.
//                    send("acoustic", MQTT.toJSON(MQTT.SIR2, MQTT.OFF, MQTT.SIR3, "medium"), roles.get("sirens"));
//                    return true;
//                }
//            });

            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
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
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (game_fsm.getCurrentState().equals(_state_RUNNING) && roles.get(rings_in_progress.getFirst()).contains(agent_id)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else
            super.on_external_message(agent_id, source, message);
    }

    private void taken(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.MEDIUM), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_TAKEN));
    }

    private void fused(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_FUSED));
        if (!allow_defuse) cpFSMs.get(agent).ProcessFSM(_msg_LOCK);
        if (active_ring_taken()) {
            // the others are still locked. Need to be TAKEN now
            roles.get(rings_in_progress.getFirst())
                    .stream()
                    .filter(s -> !cpFSMs.get(s).equals(_state_TAKEN)) // only those which are not already taken
                    .forEach(other_agents_in_this_ring -> cpFSMs.get(other_agents_in_this_ring).ProcessFSM(_msg_TAKEN)
                    );
            add_in_game_event(new JSONObject().put("item", "ring").put("ring", rings_in_progress.getFirst()).put("state", _state_TAKEN));
            rings_taken.add(rings_in_progress.pop());
            if (rings_in_progress.isEmpty()) game_fsm.ProcessFSM(_msg_GAME_OVER); // last ring ? we are done.
            else activate_ring();
        } else {
            send("acoustic", MQTT.toJSON("sir2", "medium"), roles.get("sirens"));
        }
        broadcast_score();
    }


    @Override
    public void on_reset() {
        super.on_reset();
        rings_in_progress.clear();
        rings_taken.clear();
        rings_in_progress.addAll(rings_in_use);
        broadcast_score();
    }

    @Override
    public void on_run() {
        super.on_run();
        activate_ring();
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        broadcast_score();
    }

    private void activate_ring() {
        roles.get(rings_in_progress.getFirst()).forEach(agent -> cpFSMs.get(agent).ProcessFSM(_msg_ACTIVATE));
        if (rings_in_progress.size() < rings_in_use.size() && rings_in_use.size() > 1) // not with the first or only ring
            send("acoustic", MQTT.toJSON(MQTT.SIR4, MQTT.LONG), roles.get("sirens"));
        add_in_game_event(new JSONObject().put("item", "ring").put("ring", rings_in_progress.getFirst()).put("state", "activated"));
        broadcast_score();
    }

    private void defused(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, map_agent_to_ring_color.get(agent), MQTT.RECURRING_SCHEME_FAST), agent);
    }

    private void nearly_defused(String agent) {
        cpFSMs.get(agent).ProcessFSM(_msg_DEFUSE);
        send("acoustic", MQTT.toJSON(MQTT.SIR2, MQTT.OFF, MQTT.SIR3, "medium"), roles.get("sirens"));
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_DEFUSED));
        broadcast_score();
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        //addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_STANDBY));
    }

    private void prolog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, map_agent_to_ring_color.get(agent), MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    private boolean active_ring_taken() {
        return roles.get(rings_in_progress.getFirst()).stream()
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
                            "Broken Rings",
                            "${rings_taken}/${rings_in_use}")
            );
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            JSONObject pages = MQTT.merge(MQTT.page("page0",
                            "Remaining:  ${remaining}",
                            "Integrity of active",
                            "ring: ${active_ring}",
                            "${active_progress}"),
                    MQTT.page("page1",
                            "Remaining:  ${remaining}",
                            "",
                            "Agents still alive",
                            "${stable_agents}")
            ); //todo: make 2 lines for stable_agents
            if (rings_in_use.size() > 1) pages = MQTT.merge(pages,
                    MQTT.page("page2",
                            "Remaining:  ${remaining}",
                            "Integrity",
                            "Total",
                            "${total_progress}")
            );
            return pages;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public void broadcast_score() {
        super.broadcast_score();
        send("vars", get_variables(), get_all_spawn_agents());
    }

    JSONObject get_variables() {
        JSONObject variables = new JSONObject();

        String progress = rings_taken.stream().collect(Collectors.joining(","));
        if (!rings_in_progress.isEmpty())
            progress += " **" + rings_in_progress.getFirst().toUpperCase() + "** ";
        if (rings_in_progress.size() > 1)
            progress += rings_in_progress.subList(1, rings_in_progress.size()).stream().collect(Collectors.joining(","));

        if (!rings_in_progress.isEmpty()) {
            int total_in_this_segment = roles.get(rings_in_progress.getFirst()).size();
            int remain_in_this_segment = Math.toIntExact(roles.get(rings_in_progress.getFirst()).stream()
                    .filter(agent ->
                            cpFSMs.get(agent).getCurrentState()
                                    .matches(_state_FUSED + "|" + _state_LOCKED + "|" + _state_TAKEN))
                    .count());

            variables.put("total_in_this_segment", total_in_this_segment);
            variables.put("remain_in_this_segment", remain_in_this_segment);

            // todo: change to two lines of agents like in CenterFlags Lists.partition
            String stable_agents = roles.get(rings_in_progress.getFirst()).stream()
                    .filter(agent ->
                            cpFSMs.get(agent).getCurrentState()
                                    .matches(_state_DEFUSED))
                    .sorted()
                    .collect(Collectors.joining(","));


            variables.put("active_progress", Tools.get_progress_bar(total_in_this_segment - remain_in_this_segment, total_in_this_segment));
            variables.put("stable_agents", stable_agents);

        }

        variables.put("active_ring", rings_in_progress.isEmpty() ? "" : rings_in_progress.getFirst());
        variables.put("rings_taken", rings_taken.size());
        variables.put("rings_to_go", Math.max(rings_in_progress.size() - 1, 0));
        variables.put("rings_progress", progress);
        variables.put("rings_in_use", rings_in_use.size());

        if (rings_in_use.size() > 1)
            variables.put("total_progress", Tools.get_progress_bar(rings_in_use.size() - rings_taken.size(), rings_in_use.size()));
        else
            variables.put("total_progress", "");

        return variables;
    }

    @Override
    public void zeus(JSONObject params) throws IllegalStateException, JSONException {
        if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return;

        String operation = params.getString("operation").toLowerCase();
        if (operation.equalsIgnoreCase(_msg_DEFUSE)) {
            String agent = params.getString("agent");
            if (!cpFSMs.containsKey(agent)) throw new IllegalStateException(agent + " unknown");
            if (!cpFSMs.get(agent).getCurrentState().toLowerCase().matches("fused|locked"))
                throw new IllegalStateException(agent + " is in state " + cpFSMs.get(agent).getCurrentState() + " must be FUSED or LOCKED");
            cpFSMs.get(agent).ProcessFSM(operation);
            add_in_game_event(new JSONObject()
                    .put("item", "capture_point")
                    .put("agent", agent)
                    .put("state", operation)
                    .put("zeus", "intervention"));
        }
    }

    @Override
    public String getGameMode() {
        return "stronghold";
    }


    @Override
    String get_in_game_event_description(JSONObject event) {
        String result = super.get_in_game_event_description(event);
        if (result.isEmpty()) {
            result = "error";
            String type = event.getString("type");

            if (type.equalsIgnoreCase("in_game_state_change")) {
                String zeus = (event.has("zeus") ? "&nbsp;(by the hand of ZEUS)" : "");
                if (event.getString("item").equals("capture_point")) {
                    return event.getString("agent") + " => " + event.getString("state")
                            + zeus;
                }
                if (event.getString("item").equals("ring")) {
                    return event.getString("ring") + " => " + event.getString("state")
                            + zeus;
                }
                if (event.getString("item").equals("add_seconds")) {
                    String text = event.getLong("amount") >= 0 ? " has been granted %d seconds" : " has lost %d seconds";
                    return "Team " + event.getString("team") + String.format(text, Math.abs(event.getLong("amount")))
                            + zeus;
                }
            }
        }

        return result;
    }

    @Override
    public boolean hasZeus() {
        return true;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        // everything is prepared already. simply copying over.
        JSONObject vars = get_variables();
        model.addAttribute("active_ring", vars.optString("active_ring"));
        model.addAttribute("rings_taken", vars.optInt("rings_taken"));
        model.addAttribute("rings_to_go", vars.optInt("rings_to_go"));
        model.addAttribute("rings_progress", vars.optString("rings_progress"));
        model.addAttribute("total_rings", vars.optInt("rings_in_use"));
        model.addAttribute("total_in_this_segment", vars.optInt("total_in_this_segment"));
        model.addAttribute("remain_in_this_segment", vars.optInt("remain_in_this_segment"));
        model.addAttribute("stable_agents", rings_in_progress.isEmpty() ? new ArrayList<>() : roles.get(
                        rings_in_progress.getFirst()).stream().filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_DEFUSED))
                .sorted()
                .collect(Collectors.toList())
        );
        model.addAttribute("broken_agents", rings_in_progress.isEmpty() ? new ArrayList<>() : roles.get(
                        rings_in_progress.getFirst()).stream().filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_FUSED + "|" + _state_LOCKED + "|" + _state_TAKEN))
                .sorted()
                .collect(Collectors.toList())
        );
    }

    @Override
    public JSONObject getState() {
        //log.debug("getState");
        JSONObject json = super.getState();
        JSONObject played = MQTT.merge(super.getState().getJSONObject("played"), get_variables());
        json.put("played",played);
        return json;
    }
}
