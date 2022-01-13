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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

@Log4j2
public abstract class Game {
    final String id;
    final MQTTOutbound mqttOutbound;
    //final Multimap<String, Agent> function_to_agents;
    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    private final ArrayList<String> game_description;
    Optional<LocalDateTime> pausing_since;
    private boolean prolog, epilog;

    final Scheduler scheduler;

    final Multimap<String, String> agents, roles;

    Game(String id, JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        this.id = id;
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
     */
    public void react_to(String sender, JSONObject event) {
        if (isPausing()) {
            log.info("received event {} from {} during pause. ignoring.", event, sender);
            throw new IllegalStateException();
        }
    }

    public void trigger_internal_event(String event) {
        react_to("_internal", new JSONObject().put("message", event));
    }

//    public void react_to(JSONObject event) {
//        react_to("_internal", event);
//    }

    public String getId() {
        return id;
    }

    /**
     * before another game is loaded, cleanup first
     */
    public abstract void cleanup();

    /**
     * when the actual game should start. You run this method.
     */
    public void start() {
        if (!prolog) throw new IllegalStateException();
        prolog = false;
        epilog = false;
    }

    /**
     * to resume a paused game.
     */
    public void resume() {
        if (prolog || epilog) throw new IllegalStateException();
        pausing_since = Optional.empty();
        mqttOutbound.send("delpage", new JSONObject().put("page_handles", new JSONArray().put("pause")), agents.keySet());
    }

    public void game_over() {
        prolog = false;
        epilog = true;
    }

    /**
     * to pause a running game
     */
    public void pause() {
        if (prolog || epilog) throw new IllegalStateException();
        pausing_since = Optional.of(LocalDateTime.now());
        mqttOutbound.send("paged", MQTT.page("pause", "", "      PAUSE      ", "", ""), agents.keySet());
    }

    public boolean isPausing() {
        return pausing_since.isPresent();
    }

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return status information to be sent if we are asked for it
     */
    public JSONObject getStatus() {
        return new JSONObject()
                .put("name", id)
                .put("class", this.getClass().getName())
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ISO_DATE_TIME) : JSONObject.NULL);
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
        prolog = true;
        epilog = false;
        mqttOutbound.send("signals", MQTT.toJSON("all", "off"), agents.keySet());
        mqttOutbound.send("paged", MQTT.page("page0", game_description), agents.keySet());
    }

    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

}
