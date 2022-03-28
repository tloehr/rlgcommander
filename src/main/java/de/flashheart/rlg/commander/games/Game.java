package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.github.ankzz.dynamicfsm.states.FSMStateAction;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
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

    final MQTTOutbound mqttOutbound;
    //final Multimap<String, Agent> function_to_agents;
    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    protected final ArrayList<String> game_description;
    private final JSONObject game_parameters;
    Optional<LocalDateTime> pausing_since;
    final UUID uuid;
    final Scheduler scheduler;
    final Multimap<String, String> agents, roles;
    final FSM fsm;

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
        this.fsm = createFSM();
        game_description = new ArrayList<>();
        pausing_since = Optional.empty();
    }

    public void process_message(String message) throws IllegalStateException {
        if (fsm.ProcessFSM(message) == null) {
            throw new IllegalStateException(String.format("message not processed. no transition in FSM from %s on message %s.", fsm.getCurrentState(), message));
        }
    }

    private FSM createFSM() throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/game.xml"), null);

        fsm.setAction("reset", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_reset();
                return true;
            }
        });

        fsm.setAction("start", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_start();
                return true;
            }
        });

        fsm.setAction("run", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_run();
                return true;
            }
        });

        fsm.setAction("ready", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_ready();
                return true;
            }
        });

        fsm.setAction("game_over", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_game_over();
                return true;
            }
        });

        fsm.setAction("pause", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                pausing_since = Optional.of(LocalDateTime.now());
                //todo: klappt so nicht. Überschreibt alle seiten. muss man anders machen
                mqttOutbound.send("paged", MQTT.page("pause", "", "      PAUSE      ", "", ""), agents.keySet());
                on_pause();
                return true;
            }
        });

        fsm.setAction("resume", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                pausing_since = Optional.empty();
                mqttOutbound.send("delpage", new JSONObject().put("page_handles", new JSONArray().put("pause")), agents.keySet());
                on_resume();
                return true;
            }
        });

        fsm.setAction("continue", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                log.debug("msg: {}", message);
                on_continue();
                return true;
            }
        });

        fsm.setStatesAfterTransition("PROLOG", (state, obj) -> {
            log.debug("state: {}", state);
            at_prolog();
        });

        fsm.setStatesAfterTransition("PREPARE", (state, obj) -> {
            log.debug("state: {}", state);
            at_prepare();
        });

        fsm.setStatesAfterTransition("READY", (state, obj) -> {
            log.debug("state: {}", state);
            at_ready();
        });

        fsm.setStatesAfterTransition("RUNNING", (state, obj) -> {
            log.debug("state: {}", state);
            at_running();
        });

        fsm.setStatesAfterTransition("PAUSING", (state, obj) -> {
            log.debug("state: {}", state);
            at_pausing();
        });

        fsm.setStatesAfterTransition("RESUMING", (state, obj) -> {
            log.debug("state: {}", state);
            at_resuming();
        });

        fsm.setStatesAfterTransition("EPILOG", (state, obj) -> {
            log.debug("state: {}", state);
            at_epilog();
        });

        return fsm;
    }

    public Multimap<String, String> getAgents() {
        return agents;
    }

    /**
     * when something happens, we need to react on it. Implement this method to tell us, WHAT we should do.
     *
     * @param event
     * @throws IllegalStateException an event is not important or doesn't make any sense an ISE is thrown
     */
    public abstract void react_to(String sender, String item, JSONObject event);

    /**
     * before another game is loaded, cleanup first
     */
    protected abstract void on_cleanup();


    protected void at_prolog() {
    }

    protected void at_prepare() {
    }

    protected void at_ready() {
    }

    protected void at_running() {
    }

    protected void at_pausing() {
    }

    protected void at_resuming() {
    }

    protected void at_epilog() {
    }

    protected void on_start() {
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
                .put("game_state", fsm.getCurrentState());
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

    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

    public JSONObject getGame_parameters() {
        return game_parameters;
    }
}
