package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.StateReachedEvent;
import de.flashheart.rlg.commander.misc.StateReachedListener;
import de.flashheart.rlg.commander.misc.StateTransitionEvent;
import de.flashheart.rlg.commander.misc.StateTransitionListener;
import lombok.extern.log4j.Log4j2;
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
    public static final String _msg_RUN = "run";
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
    public static final String[] _state_ALL_STATES = new String[]{_state_PROLOG, _state_TEAMS_NOT_READY, _state_TEAMS_READY, _state_RESUMING, _state_PAUSING, _state_RESUMING, _state_EPILOG};
    private List<StateTransitionListener> stateTransitionListeners = new ArrayList<>();
    private List<StateReachedListener> stateReachedListeners = new ArrayList<>();

    final MQTTOutbound mqttOutbound;
    //final Multimap<String, Agent> function_to_agents;
    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    protected final ArrayList<String> game_description;
    private final JSONObject game_parameters;
    Optional<LocalDateTime> pausing_since;
    protected final UUID uuid;
    protected final Scheduler scheduler;
    protected final Multimap<String, String> agents, roles;
    // main FSM to control the basic states of every game
    protected final FSM game_fsm;

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException {
        uuid = UUID.randomUUID();
        this.game_parameters = game_parameters;
        this.scheduler = scheduler;
        this.mqttOutbound = mqttOutbound;
        agents = HashMultimap.create();
        roles = HashMultimap.create();
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
        game_description = new ArrayList<>();
        pausing_since = Optional.empty();
        addStateTransistionListener(event -> {
            if (event.getMessage().equals(_msg_PAUSE)) pausing_since = Optional.of(LocalDateTime.now());
            if (event.getMessage().equals(_msg_CONTINUE)) pausing_since = Optional.empty();
        });
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
    protected abstract void at_state(String state);

    /**
     * called on a new transistion within the game_fsm
     *
     * @param old_state before the transition
     * @param message   message triggering the transition
     * @param new_state after the transition
     */
    protected abstract void on_transition(String old_state, String message, String new_state);

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
//        // States
//        fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_TEAMS_NOT_READY, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_TEAMS_READY, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_RUNNING, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_PAUSING, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_RESUMING, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });
//        fsm.setStatesAfterTransition(_state_EPILOG, (state, obj) -> {
//            fireStateReached(new StateReachedEvent(state));
//        });

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
    public JSONObject getStatus() {
        return new JSONObject()
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)))
                .put("class", this.getClass().getName())
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)) : JSONObject.NULL)
                .put("game_state", game_fsm.getCurrentState());
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
}
