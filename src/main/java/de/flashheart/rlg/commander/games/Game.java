package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
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
    }

    public void addStateTransistionListener(StateTransitionListener toAdd) {
        stateTransitionListeners.add(toAdd);
    }

    public void addStateReachedListener(StateReachedListener toAdd) {
        stateReachedListeners.add(toAdd);
    }


    private void fireStateTransition(StateTransitionEvent event) {
        stateTransitionListeners.forEach(stateTransitionListener -> stateTransitionListener.onStateTransition(event));
    }

    private void fireStateReached(StateReachedEvent event) {
        stateReachedListeners.forEach(stateReachedListener -> stateReachedListener.onStateReached(event));
    }

    public void process_message(String message) throws IllegalStateException {
        if (game_fsm.ProcessFSM(message) == null) {
            throw new IllegalStateException(String.format("message not processed. no transition in FSM from %s on message %s.", game_fsm.getCurrentState(), message));
        }
    }


    /**
     * when something happens, we need to react on it. Implement this method to tell us, WHAT we should do.
     *
     * @param message
     * @throws IllegalStateException an event is not important or doesn't make any sense an ISE is thrown
     */
    public abstract void process_message(String sender, String item, JSONObject message);


    private FSM createFSM() throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/game.xml"), null);
        fsm.setAction("reset", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: reset");
                fireStateTransition(new StateTransitionEvent(curState, "reset", nextState));
                on_reset();
                return true;
            }
        });

        fsm.setAction("run", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: run");
                fireStateTransition(new StateTransitionEvent(curState, "run", nextState));
                on_run();
                return true;
            }
        });

        fsm.setAction("ready", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: teams ready");
                fireStateTransition(new StateTransitionEvent(curState, "ready", nextState));
                on_ready();
                return true;
            }
        });

        fsm.setAction("prepare", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, "prepare", nextState));
                log.debug("msg: prepare => teams not ready");
                on_prepare();
                return true;
            }
        });

        fsm.setAction("game_over", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: game_over");
                fireStateTransition(new StateTransitionEvent(curState, "game_over", nextState));
                on_game_over();
                return true;
            }
        });

        fsm.setAction("pause", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: pause");
                pausing_since = Optional.of(LocalDateTime.now());
                on_pause();
                return true;
            }
        });

        fsm.setAction("resume", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: resume");
                pausing_since = Optional.empty();
                on_resume();
                return true;
            }
        });

        fsm.setAction("continue", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: continue");
                on_continue();
                return true;
            }
        });

        fsm.setStatesAfterTransition("PROLOG", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_prolog();
        });

        fsm.setStatesAfterTransition("TEAMS_NOT_READY", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_teams_not_ready();
        });

        fsm.setStatesAfterTransition("TEAMS_READY", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_teams_ready();
        });

        fsm.setStatesAfterTransition("RUNNING", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_running();
        });

        fsm.setStatesAfterTransition("PAUSING", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_pausing();
        });

        fsm.setStatesAfterTransition("RESUMING", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_resuming();
        });

        fsm.setStatesAfterTransition("EPILOG", (state, obj) -> {
            log.debug("state: {}", state);
            fireStateReached(new StateReachedEvent(state));
            at_epilog();
        });

        return fsm;
    }

    protected void on_prepare() {
    }

    public Multimap<String, String> getAgents() {
        return agents;
    }

    /**
     * before another game is loaded, cleanup first
     */
    protected abstract void on_cleanup();


    protected void at_prolog() {
    }

    protected void at_teams_not_ready() {
    }

    protected void at_teams_ready() {
    }

    protected void at_running() {
    }

    protected void at_pausing() {
    }

    protected void at_resuming() {
    }

    protected void at_epilog() {
    }

    protected void on_ready() {
    }

    protected void on_run() {
    }

    protected void on_pause() {
    }

    protected void on_resume() {
    }

    protected void on_continue() {
    }

    protected void on_game_over() {
    }

    protected void on_reset() {
    }

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
     * if a superclass has also a page to display, we can put it in here and inherit to the children.
     *
     * @return
     */
    protected JSONObject get_pages_to_display() {
        return game_fsm.getCurrentState().equals("PAUSING") ? MQTT.page("pause", "", "      PAUSE      ", "", "") : new JSONObject();
    }

    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

    public JSONObject getGame_parameters() {
        return game_parameters;
    }
}
