package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
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

    public void process_message(String message) {
        fsm.ProcessFSM(message);
    }

    private FSM createFSM() throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/game.xml"), null);
        fsm.setStatesAfterTransition("PROLOG", (state, obj) -> {
            log.debug(state);
            on_reset();
        });
        fsm.setStatesAfterTransition("WAITING_FOR_TEAMS", (state, obj) -> {
            log.debug(state);
        });
        fsm.setStatesAfterTransition("GAME_RUNNING", (state, obj) -> {
            log.debug(state);
            on_start();
        });
        fsm.setStatesAfterTransition("EPILOG", (state, obj) -> {
            log.debug(state);
            on_game_over();
        });
        fsm.setStatesAfterTransition("GAME_PAUSING", (state, obj) -> {
            log.debug(state);
            pausing_since = Optional.of(LocalDateTime.now());
            mqttOutbound.send("paged", MQTT.page("pause", "", "      PAUSE      ", "", ""), agents.keySet());
            on_pause();
        });


        fsm.setAction("GAME_PAUSING", "resume", new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                pausing_since = Optional.empty();
                mqttOutbound.send("delpage", new JSONObject().put("page_handles", new JSONArray().put("pause")), agents.keySet());
                on_resume();
                return true;
            }
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
    public abstract void on_cleanup();

    protected abstract void on_start();

    protected abstract void on_pause();

    protected abstract void on_resume();

    protected abstract void on_game_over();

    protected abstract void on_reset();

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
                .put("state", fsm.getCurrentState());
//         .put("prolog", Boolean.toString(prolog))
//                .put("epilog", Boolean.toString(epilog))
//                .put("pausing", Boolean.toString(isPausing()))
//                .put("running", Boolean.toString(isRunning()))
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
