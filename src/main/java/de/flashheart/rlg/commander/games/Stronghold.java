package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


/**
 * <h1>Stronghold</h1>
 * defense rings have to be taken in order blue -> green -> yellow -> red
 * maximum number of rings: 4 if we need only one
 * ring we start with red then red, yellow and so on
 */
@Log4j2
public class Stronghold extends Timed {
    // intermediate step to separate from activation -> defused transition
    public static final String _state_NEARLY_DEFUSED = "NEARLY_DEFUSED";
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_LOCKED = "LOCKED";
    public static final String _msg_LOCK = "lock";
    public static final String _msg_TAKEN = "taken";
    public static final String _msg_DEFUSE = "defuse";
    private final boolean allow_defuse;
    protected final LinkedList<String> rings_to_go;
    protected final ArrayList<String> rings_taken, rings_total;
    protected final HashMap<String, String> map_agent_to_ring_color;

    public Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        assert_two_teams_red_and_blue();
        rings_to_go = new LinkedList<>();
        rings_taken = new ArrayList<>();
        rings_total = new ArrayList<>();

        allow_defuse = game_parameters.optBoolean("allow_defuse");
        map_agent_to_ring_color = new HashMap<>();

        List.of("blu", "grn", "ylw", "red").forEach(color -> {
            if (roles.get(color).isEmpty()) return;
            rings_total.add(color);

            roles.get(color).forEach(agent ->
                    map_agent_to_ring_color.put(agent, color));
        });

        setGameDescription(game_parameters.getString("comment"),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Number of rings: %s", rings_total.size()),
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
            fsm.setStatesAfterTransition(_state_TAKEN, (state, obj) -> taken(agent));

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
        if (game_fsm.getCurrentState().equals(_state_RUNNING) && roles.get(rings_to_go.getFirst()).contains(agent_id)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else
            super.on_external_message(agent_id, source, message);
    }

    private void taken(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.SCHEME_MEDIUM), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_TAKEN));
    }

    private void fused(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_FUSED));
        if (!allow_defuse) cpFSMs.get(agent).ProcessFSM(_msg_LOCK);
        if (active_ring_taken()) {
//            // the others are still locked. Need to be TAKEN now
//            roles.get(rings_to_go.getFirst())
//                    .stream()
//                    .filter(s -> !cpFSMs.get(s).getCurrentState().equals(_state_TAKEN)) // only those which are not already taken
//                    .forEach(other_agents_in_this_ring -> cpFSMs.get(other_agents_in_this_ring).ProcessFSM(_msg_TAKEN));
            add_in_game_event(new JSONObject().put("item", "ring").put("ring", rings_to_go.getFirst()).put("state", _state_TAKEN));
            rings_taken.add(rings_to_go.pop());
            if (rings_to_go.isEmpty()) game_fsm.ProcessFSM(_msg_GAME_OVER); // last ring ? we are done.
            else activate_ring();
        } else {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON("sir2", "medium"), roles.get("sirens"));
        }
        broadcast_score();
    }


    @Override
    public void on_reset() {
        super.on_reset();
        rings_to_go.clear();
        rings_taken.clear();
        rings_to_go.addAll(rings_total);
    }

    @Override
    public void on_run() {
        super.on_run();
        activate_ring();
    }

    // activate the current ring
    protected void activate_ring() {
        roles.get(rings_to_go.getFirst()).forEach(agent -> cpFSMs.get(agent).ProcessFSM(_msg_ACTIVATE));
        if (rings_to_go.size() < rings_total.size() && rings_total.size() > 1) // not with the first or only ring
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR4, MQTT.SCHEME_LONG), roles.get("sirens"));
        add_in_game_event(new JSONObject().put("item", "ring").put("ring", rings_to_go.getFirst()).put("state", "activated"));
        broadcast_score();
    }

    private void defused(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, map_agent_to_ring_color.get(agent), MQTT.RECURRING_SCHEME_FAST), agent);
    }

    private void nearly_defused(String agent) {
        cpFSMs.get(agent).ProcessFSM(_msg_DEFUSE);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.OFF, MQTT.SIR3, "medium"), roles.get("sirens"));
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_DEFUSED));
        broadcast_score();
    }

    private void standby(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        //addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", _state_STANDBY));
    }

    private void prolog(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, map_agent_to_ring_color.get(agent), MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    private boolean active_ring_taken() {
        return roles.get(rings_to_go.getFirst()).stream()
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
                            "Rings broken:",
                            "${rings_taken}/${rings_in_use}")
            );
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            JSONObject pages = MQTT.merge(
                    MQTT.page("page0",
                            "Time:  ${remaining}  ${wifi_signal}",
                            "${rings_taken}/${rings_in_use} -> ${active_ring}",
                            "Alive:",
                            "${stable_agents}")
            ); //todo: make 2 lines for stable_agents
//            if (rings_total.size() > 1) pages = MQTT.merge(pages,
//                    MQTT.page("page2",
//                            "Remaining:  ${remaining}",
//                            "Integrity",
//                            "Total",
//                            "${total_progress}")
//            );
//            MQTT.page("page0",
//                    "Remaining:  ${remaining}",
//                    "Integrity of active",
//                    "ring: ${active_ring}",
//                    "${active_progress}"),
            return pages;
        }
        return MQTT.page("page0", game_description);
    }

    protected int total_in_this_segment() {
        if (rings_to_go.isEmpty()) return 0;
        return roles.get(rings_to_go.getFirst()).size();
    }

    protected int remaining_agents_in_this_segment() {
        if (rings_to_go.isEmpty()) return 0;
        return Math.toIntExact(roles.get(rings_to_go.getFirst()).stream()
                .filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_FUSED + "|" + _state_LOCKED + "|" + _state_TAKEN))
                .count());

    }

    @Override
    protected JSONObject get_broadcast_vars() {
        JSONObject variables = super.get_broadcast_vars();

        String progress = String.join(",", rings_taken);
        if (!rings_to_go.isEmpty())
            progress += " **" + rings_to_go.getFirst().toUpperCase() + "** ";
        if (rings_to_go.size() > 1)
            progress += String.join(",", rings_to_go.subList(1, rings_to_go.size()));

        if (!rings_to_go.isEmpty()) {
            variables.put("total_in_this_segment", total_in_this_segment());
            int remain_in_this_segment = remaining_agents_in_this_segment();
            variables.put("remain_in_this_segment", remain_in_this_segment);

            variables.put("active_progress", Tools.get_progress_bar(total_in_this_segment() - remain_in_this_segment, total_in_this_segment()));
            variables.put("stable_agents", stable_agents());
        }

        variables.put("active_ring", rings_to_go.isEmpty() ? "" : rings_to_go.getFirst());
        variables.put("rings_taken", rings_taken.size());
        variables.put("rings_to_go", Math.max(rings_to_go.size() - 1, 0));
        variables.put("rings_progress", progress);
        variables.put("rings_in_use", rings_total.size());

        if (rings_total.size() > 1)
            variables.put("total_progress", Tools.get_progress_bar(rings_total.size() - rings_taken.size(), rings_total.size()));
        else
            variables.put("total_progress", "");

        return variables;
    }

    protected ArrayList<String> stable_agents() {
        if (rings_to_go.isEmpty()) return new ArrayList<>();
        return roles.get(rings_to_go.getFirst()).stream()
                .filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_DEFUSED))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
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
        JSONObject vars = get_broadcast_vars();
        model.addAttribute("active_ring", vars.optString("active_ring"));
        model.addAttribute("rings_taken", vars.optInt("rings_taken"));
        model.addAttribute("rings_to_go", rings_to_go);
        model.addAttribute("rings_total", rings_total);
        model.addAttribute("rings_progress", vars.optString("rings_progress"));
        model.addAttribute("total_rings", vars.optInt("rings_in_use"));
        model.addAttribute("total_in_this_segment", vars.optInt("total_in_this_segment"));
        model.addAttribute("remain_in_this_segment", vars.optInt("remain_in_this_segment"));
        model.addAttribute("stable_agents", rings_to_go.isEmpty() ? new ArrayList<>() : roles.get(
                        rings_to_go.getFirst()).stream().filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_DEFUSED))
                .sorted()
                .collect(Collectors.toList())
        );
        model.addAttribute("broken_agents", rings_to_go.isEmpty() ? new ArrayList<>() : roles.get(
                        rings_to_go.getFirst()).stream().filter(agent ->
                        cpFSMs.get(agent).getCurrentState()
                                .matches(_state_FUSED + "|" + _state_LOCKED + "|" + _state_TAKEN))
                .sorted()
                .collect(Collectors.toList())
        );
    }

    @Override
    public JSONObject get_full_state() {
        //log.debug("getState");
        JSONObject json = super.get_full_state();
        JSONObject played = MQTT.merge(super.get_full_state().getJSONObject("played"), get_broadcast_vars());
        json.put("played", played);
        return json;
    }
}
