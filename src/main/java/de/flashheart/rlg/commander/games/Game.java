package de.flashheart.rlg.commander.games;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.Scheduler;

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
    private final ArrayList<String> game_description;
    private final JSONObject game_parameters;
    Optional<LocalDateTime> pausing_since;
    private boolean prolog, epilog;
    final UUID uuid;
    final Scheduler scheduler;
    final Multimap<String, String> agents, roles;

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        uuid = UUID.randomUUID();
        this.game_parameters = game_parameters;
        prolog = true;
        epilog = false;
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

        log.debug(agents);
        log.debug(roles);

        game_description = new ArrayList<>();
        pausing_since = Optional.empty();
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
    public void react_to(String sender, String item, JSONObject event) throws IllegalStateException {
        if (!isRunning()) {
            //log.info("received event {} from {} but match is not running. ignoring.", event, sender);
            throw new IllegalStateException(String.format("received event %s from %s but match is not running. ignoring.", event, sender));
        }
    }

    public void trigger_internal_event(String event) {
        react_to("_internal", "_internal", new JSONObject().put("message", event));
    }

//    public void react_to(JSONObject event) {
//        react_to("_internal", event);
//    }

    /**
     * before another game is loaded, cleanup first
     */
    public abstract void cleanup();

    /**
     * when the actual game should start. You run this method.
     */
    public void start() throws IllegalStateException {
        if (!prolog) throw new IllegalStateException("Can't start the match. Not in PROLOG.");
        log.debug("\n ___ _   _ _  _\n" +
                "| _ \\ | | | \\| |\n" +
                "|   / |_| | .` |\n" +
                "|_|_\\\\___/|_|\\_|");
        prolog = false;
        epilog = false;
    }

    /**
     * to resume a paused game.
     *
     * @throws IllegalStateException calls to this method without sense are answered with an ISE
     */
    public void resume() throws IllegalStateException {
        if (prolog || epilog) throw new IllegalStateException("Can't resume the match. We are in PROLOG or EPILOG.");
        if (!isPausing()) throw new IllegalStateException("Can't resume the match. We are not PAUSING.");
        log.debug("\n ___ ___ ___ _   _ __  __ ___\n" +
                "| _ \\ __/ __| | | |  \\/  | __|\n" +
                "|   / _|\\__ \\ |_| | |\\/| | _|\n" +
                "|_|_\\___|___/\\___/|_|  |_|___|\n");
        pausing_since = Optional.empty();
        mqttOutbound.send("delpage", new JSONObject().put("page_handles", new JSONArray().put("pause")), agents.keySet());
    }

    public void game_over() {
        log.debug("\n  ___   _   __  __ ___    _____   _____ ___\n" +
                " / __| /_\\ |  \\/  | __|  / _ \\ \\ / / __| _ \\\n" +
                "| (_ |/ _ \\| |\\/| | _|  | (_) \\ V /| _||   /\n" +
                " \\___/_/ \\_\\_|  |_|___|  \\___/ \\_/ |___|_|_\\");
        prolog = false;
        epilog = true;
    }

    /**
     * to pause a running game
     *
     * @throws IllegalStateException calls to this method without sense are answered with an ISE
     */
    public void pause() throws IllegalStateException {
        if (prolog || epilog) throw new IllegalStateException("Can't pause the match. We are in PROLOG or EPILOG.");
        if (isPausing()) throw new IllegalStateException("Can't pause the match. We are in PAUSING already.");
        log.debug("\n ___  _  _   _ ___ ___\n" +
                "| _ \\/_\\| | | / __| __|\n" +
                "|  _/ _ \\ |_| \\__ \\ _|\n" +
                "|_|/_/ \\_\\___/|___/___|\n");
        pausing_since = Optional.of(LocalDateTime.now());
        mqttOutbound.send("paged", MQTT.page("pause", "", "      PAUSE      ", "", ""), agents.keySet());
    }

    public boolean isPausing() {
        return pausing_since.isPresent();
    }

    public boolean isRunning() {
        return !isPausing() && !prolog && !epilog;
    }

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return status information to be sent if we are asked for it
     */
    public JSONObject getStatus() {
        String state = (prolog ? "prolog" : "")+
                (epilog ? "epilog" : "")+
                (isPausing() ? "pausing" : "")+
                (isRunning() ? "running" : "");

        return new JSONObject()
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)))
                .put("class", this.getClass().getName())
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)) : JSONObject.NULL)
                .put("state", state)               ;
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

    public void reset() {
        log.debug("\n ___ ___ ___ ___ _____\n" +
                "| _ \\ __/ __| __|_   _|\n" +
                "|   / _|\\__ \\ _|  | |\n" +
                "|_|_\\___|___/___| |_|\n");
        prolog = true;
        epilog = false;
        mqttOutbound.send("all/init", new JSONObject());
        mqttOutbound.send("paged", MQTT.page("page0", game_description), agents.keySet());
    }

    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

    public JSONObject getGame_parameters() {
        return game_parameters;
    }
}
