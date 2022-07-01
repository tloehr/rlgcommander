package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.*;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

@Log4j2
public abstract class Game {
    public static final String _msg_RESET = "reset";
    public static final String _msg_PREPARE = "prepare";
    public static final String _msg_READY = "ready";
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
    public static final String[] _state_ALL_STATES = new String[]{_state_PROLOG, _state_TEAMS_NOT_READY, _state_TEAMS_READY, _state_RESUMING, _state_PAUSING, _state_RUNNING, _state_EPILOG};
    public String _signal_AIRSIREN_START = "very_long";
    public String _signal_AIRSIREN_STOP = "1:on,1500;off,750;on,1500;off,750;on,5000;off,1";
    private List<StateTransitionListener> stateTransitionListeners = new ArrayList<>();
    private List<StateReachedListener> stateReachedListeners = new ArrayList<>();

    final MQTTOutbound mqttOutbound;
    //final Multimap<String, Agent> function_to_agents;
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

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        uuid = UUID.randomUUID();
        this.game_parameters = game_parameters;
        this.scheduler = scheduler;
        this.mqttOutbound = mqttOutbound;
        this.agents = HashMultimap.create();
        this.roles = HashMultimap.create();
        this.in_game_events = new ArrayList<>();
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
    }

    protected void addEvent(JSONObject in_game_event) {
        in_game_event.put("type", "in_game_state_change");
        in_game_events.add(new JSONObject()
                .put("pit", JavaTimeConverter.to_iso8601())
                .put("event", in_game_event)
                .put("new_state", game_fsm.getCurrentState())
        );
    }

    private void addEvent(String message, String state) {
        in_game_events.add(new JSONObject()
                .put("pit", JavaTimeConverter.to_iso8601())
                .put("event", new JSONObject().put("type", "general_game_state_change").put("message", message))
                .put("new_state", state)
        );
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


    private void fireStateTransition(StateTransitionEvent event) {
        stateTransitionListeners.forEach(stateTransitionListener -> stateTransitionListener.onStateTransition(event));
        log.debug("transition {}:{} == > {}", event.getOldState(), event.getMessage(), event.getNewState());
        if (!event.getMessage().equals(_msg_IN_GAME_EVENT_OCCURRED))
            addEvent(event.getMessage(), event.getNewState());
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
        if (state.equals(_state_PROLOG)) {
            mqttOutbound.send("signals", MQTT.toJSON("led_all", "off"), roles.get("sirens"));
            mqttOutbound.send("paged",
                    MQTT.page("page0",
                            "I am ${agentname}", "", "I will be a", "Siren"), roles.get("sirens"));
        }
    }

    /**
     * called on a new transistion within the game_fsm
     *
     * @param old_state before the transition
     * @param message   message triggering the transition
     * @param new_state after the transition
     */
    protected void on_transition(String old_state, String message, String new_state) {
        if (message.equals(_msg_RUN))
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_START), roles.get("sirens"));
        if (message.equals(_msg_GAME_OVER))
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_STOP), roles.get("sirens"));
        if (message.equals(_msg_RESET)) {
            mqttOutbound.send("signals", MQTT.toJSON("sir_all", "off", "buzzer", "off"), roles.get("sirens"));
            mqttOutbound.send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), roles.get("spawns"));
        }
    }

    public void process_message(String message) throws IllegalStateException {
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
    public abstract void process_message(String sender, String item, JSONObject message);


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
        return new JSONObject(game_parameters.toString())
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)))
                .put("class", this.getClass().getName())
                .put("game_state", game_fsm.getCurrentState())
                .put("in_game_events", new JSONArray(in_game_events));
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
        log.debug(game_description);
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

    protected abstract JSONObject getPages();


}
