package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.events.StateReachedEvent;
import de.flashheart.rlg.commander.games.events.StateReachedListener;
import de.flashheart.rlg.commander.games.events.StateTransitionEvent;
import de.flashheart.rlg.commander.games.events.StateTransitionListener;
import de.flashheart.rlg.commander.misc.*;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

@Log4j2
public abstract class Game {
    public static final String _msg_RESET = "reset";
    public static final String _msg_PREPARE = "prepare";
    public static final String _msg_READY = "ready";
    public static final String _msg_ADMIN = "admin";
    public static final String _msg_BUTTON_01 = "btn01";
    public static final String _msg_BUTTON_02 = "btn02";
    public static final String _msg_RUN = "run";
    public static final String _msg_IN_GAME_EVENT_OCCURRED = "in_game_event_occurred";
    public static final String _msg_PAUSE = "pause";
    public static final String _msg_RESUME = "resume";
    public static final String _msg_CONTINUE = "continue";
    public static final String _msg_GAME_OVER = "game_over";
    public static final String _state_PROLOG = "PROLOG";
    public static final String _state_TEAMS_NOT_READY = "TEAMS_NOT_READY";
    public static final String _state_TEAMS_READY = "TEAMS_READY";
    public static final String _state_RUNNING = "RUNNING";
    public static final String _state_PAUSING = "PAUSING";
    public static final String _state_RESUMING = "RESUMING";
    public static final String _state_EPILOG = "EPILOG";
    public static final String OUT_LED_WHITE = MQTT.WHITE;
    public static final String OUT_LED_RED = MQTT.RED;
    public static final String OUT_LED_BLUE = MQTT.BLUE;
    public static final String OUT_LED_YELLOW = MQTT.YELLOW;
    public static final String OUT_LED_GREEN = MQTT.GREEN;
    public static final String[] ALL_LEDS = new String[]{OUT_LED_WHITE, OUT_LED_RED, OUT_LED_YELLOW, OUT_LED_GREEN, OUT_LED_BLUE};
    public static final String[] _state_ALL_STATES = new String[]{_state_PROLOG, _state_TEAMS_NOT_READY, _state_TEAMS_READY, _state_RESUMING, _state_PAUSING, _state_RUNNING, _state_EPILOG};
    private List<StateTransitionListener> stateTransitionListeners = new ArrayList<>();
    private List<StateReachedListener> stateReachedListeners = new ArrayList<>();

    // all message must be sent via THIS base classes send method to implement "SILENT_GAME"
    private final MQTTOutbound mqttOutbound;
    private final boolean silent_game;

    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    protected final ArrayList<String> game_description;
    private final JSONObject game_parameters;
    protected final UUID uuid;
    protected final Scheduler scheduler;
    protected final Multimap<String, String> agents, roles;
    // what happened, and when ?
    protected final List<JSONObject> in_game_events;
    // main FSM to control the basic states of every game
    protected final FSM game_fsm;
    protected final Map<String, FSM> cpFSMs;
//    protected final Spawns spawns;

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        uuid = UUID.randomUUID();
        this.game_parameters = game_parameters;
        this.scheduler = scheduler;
        this.mqttOutbound = mqttOutbound;
        this.agents = HashMultimap.create();
        this.roles = HashMultimap.create();
        this.in_game_events = new ArrayList<>();
        this.silent_game = game_parameters.optBoolean("silent_game");
        this.cpFSMs = new HashMap<>();

        JSONObject agts = game_parameters.getJSONObject("agents");
        Set<String> rls = agts.keySet();
        rls.forEach(role ->
                agts.getJSONArray(role).forEach(agent -> {
                            agents.put(agent.toString(), role);
                            roles.put(role, agent.toString());
                        }
                )
        );
        this.game_fsm = createFSM();
        this.game_description = new ArrayList<>();

        send("paged", MQTT.page("page0",
                "I am ${agentname}", "", "", "I am a Siren"), roles.get("sirens")
        );

        // generic creation of capture_points - if needed
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        send("paged", MQTT.page("page0",
                "I am ${agentname}", "", "I will be a", "Capture Point"), roles.get("capture_points"));

    }

    protected void addEvent(JSONObject in_game_event) {
        in_game_event.put("type", "in_game_state_change");
        in_game_events.add(new JSONObject()
                .put("pit", JavaTimeConverter.to_iso8601())
                .put("event", in_game_event)
                .put("new_state", game_fsm.getCurrentState())
        );
        if (game_fsm.getCurrentState().equals(_state_RUNNING)) process_internal_message(_msg_IN_GAME_EVENT_OCCURRED);
    }

    /**
     * adds a listener for state transitions
     *
     * @param toAdd
     */
    public void addStateTransistionListener(StateTransitionListener toAdd) {
        stateTransitionListeners.add(toAdd);
    }

    /**
     * adds a listener when reaching a new state
     *
     * @param toAdd
     */
    public void addStateReachedListener(StateReachedListener toAdd) {
        stateReachedListeners.add(toAdd);
    }

    /**
     * if we have a game mode WITHOUT capture points - simply don't use the "capture_points" key in the game_params json
     * @param agent
     * @return
     */
    public abstract FSM create_CP_FSM(String agent);

    private void fireStateTransition(StateTransitionEvent event) {
        stateTransitionListeners.forEach(stateTransitionListener -> stateTransitionListener.onStateTransition(event));
        log.debug("transition {}:{} == > {}", event.getOldState(), event.getMessage(), event.getNewState());
        if (!event.getMessage().equals(_msg_IN_GAME_EVENT_OCCURRED)) {
            in_game_events.add(new JSONObject()
                    .put("pit", JavaTimeConverter.to_iso8601())
                    .put("event", new JSONObject().put("type", "general_game_state_change").put("message", event.getMessage()))
                    .put("new_state", event.getNewState()));
        }
//            addEvent(event.getMessage(), event.getNewState());
        on_transition(event.getOldState(), event.getMessage(), event.getNewState());
    }

    private void fireStateReached(StateReachedEvent event) {
        stateReachedListeners.forEach(stateReachedListener -> stateReachedListener.onStateReached(event));
        log.debug("new state {}", event.getState());
        at_state(event.getState());
    }

    /**
     * called whenever a new state of the game_fsm is reached.
     *
     * @param state the reached state
     */
    protected void at_state(String state) {
    }

    /**
     * called on a new transistion within the game_fsm
     *
     * @param old_state before the transition
     * @param message   message triggering the transition
     * @param new_state after the transition
     */
    protected void on_transition(String old_state, String message, String new_state) {
        if (message.equals(_msg_RUN)) run_operations();
        if (message.equals(_msg_GAME_OVER)) game_over_operations();
        if (message.equals(_msg_RESET)) reset_operations();
    }


    /**
     * sandwich method to implement silent game function
     *
     * @param cmd
     * @param payload
     * @param agent
     */
    protected void send(String cmd, JSONObject payload, String agent) {
        if (silent_game && cmd.equalsIgnoreCase("acoustic")) return;
        mqttOutbound.send(cmd, payload, agent);
    }

    protected void send(String cmd, JSONObject payload, Collection<String> agents) {
        if (silent_game && cmd.equalsIgnoreCase("acoustic")) return;
        mqttOutbound.send(cmd, payload, agents);
    }

    /**
     * dummy method. will be notified if a REST request is sent to games/admin.
     *
     * @param params contains the details about the admin operation
     * @throws IllegalStateException
     */
    public void zeus(JSONObject params) throws IllegalStateException {
        log.warn("no zeus function implemented. ignoring.");
    }

    public void process_internal_message(String message) throws IllegalStateException {
        if (game_fsm.ProcessFSM(message) == null) {
            throw new IllegalStateException(String.format("message not processed. no transition in FSM from %s on message %s.", game_fsm.getCurrentState(), message));
        }
    }

    /**
     * when something happens, we need to react on it. Implement this method to tell us, WHAT we should do. these
     * messages are usually sent from outside the Game hierarchy. A sender can also be the quartz scheduler.
     *
     * @param message
     * @throws IllegalStateException an event is not important or doesn't make any sense an ISE is thrown
     */
    public abstract void process_external_message(String sender, String item, JSONObject message);

    public void reset_operations() {
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
        send("acoustic", MQTT.toJSON(MQTT.ALL, "off"), roles.get("sirens"));
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), roles.get("sirens"));
    }

    public void run_operations() {
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));
        send("acoustic", MQTT.toJSON(MQTT.SIR1, "game_starts"), roles.get("sirens"));
    }

    public void game_over_operations() {
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_OVER));
        send("acoustic", MQTT.toJSON(MQTT.ALL, "off", MQTT.SIR1, "game_ends"), roles.get("sirens"));
        log.info(getState().toString(4));
    }

    /**
     * mainly boilerplate code to create the game_fsm
     *
     * @return the created FSM
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    private FSM createFSM() throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/game.xml"), null);

        // Transitions
        fsm.setAction(_msg_RESET, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                in_game_events.clear();
                fireStateTransition(new StateTransitionEvent(curState, _msg_RESET, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_RUN, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_RUN, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_IN_GAME_EVENT_OCCURRED, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_IN_GAME_EVENT_OCCURRED, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_READY, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_READY, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_PREPARE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_PREPARE, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_GAME_OVER, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_GAME_OVER, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_PAUSE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_PAUSE, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_RESUME, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_RESUME, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_CONTINUE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_CONTINUE, nextState));
                return true;
            }
        });
        fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList(_state_ALL_STATES)), (state, obj) -> {
            fireStateReached(new StateReachedEvent(state));
        });

        return fsm;
    }

    public Multimap<String, String> getAgents() {
        return agents;
    }

    /**
     * called whenever cleanup() is called from outside
     */
    protected abstract void on_cleanup();

    /**
     * call this to initiate the cleanup process before You destroy a loaded game.
     */
    public void cleanup() {
        stateReachedListeners.clear();
        stateTransitionListeners.clear();
        on_cleanup();
    }

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return status information to be sent if we are asked for it
     */
    public JSONObject getState() {
        JSONObject agent_states = new JSONObject();
        cpFSMs.forEach((agentid, fsm) -> agent_states.put(agentid, fsm.getCurrentState()));
        return new JSONObject(game_parameters.toString())
                .put("class", this.getClass().getName())
                .put("game_state", game_fsm.getCurrentState())
                .put("in_game_events", new JSONArray(in_game_events))
                .put("agent_states", agent_states);
    }

    /**
     * if the commander sends out a screen for the lcd display to show the current set game parameters it will use the
     * data set by this method.
     *
     * @param lines line by line description as we want it to be broadcasted
     */
    public void setGameDescription(String... lines) {
        game_description.clear();
        game_description.addAll(Arrays.asList(lines));
    }

    /**
     * pretty self explanatory
     *
     * @param agent
     * @param role
     * @return
     */
    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

    public JSONObject getGame_parameters() {
        return game_parameters;
    }

    protected JSONObject getSpawnPages(String state) {
        return MQTT.page("page0", game_description);
    }


}
