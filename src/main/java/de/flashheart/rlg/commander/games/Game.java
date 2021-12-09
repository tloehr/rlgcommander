package de.flashheart.rlg.commander.games;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
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
    final String name;
    final MQTTOutbound mqttOutbound;
    //final Multimap<String, Agent> function_to_agents;
    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    private final ArrayList<String> game_description;
    Optional<LocalDateTime> pausing_since;

    final Scheduler scheduler;
//    final JSONObject agents;
//    final Set<String> roles;
//    HashSet<Pair<String, String>> agent_roles;


    final Multimap<String, String> agents, roles;

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        this.name = game_parameters.getString("name");
        this.scheduler = scheduler;
        this.mqttOutbound = mqttOutbound;
        agents = HashMultimap.create();
        roles = HashMultimap.create();
        JSONObject agts = game_parameters.getJSONObject("agents");
        Set<String> rls = agents.keySet();


        rls.forEach(role ->
                agts.getJSONArray(role).forEach(agent -> {
                            agents.put(agent.toString(), role);
                            roles.put(role, agent.toString());
                            mqttOutbound.sendCommandTo(agent.toString(), new JSONObject().put("subscribe_to", role));
                        }
                )
        );

        game_description = new ArrayList<>();
        pausing_since = Optional.empty();
    }

    /**
     * when something happens, we need to react on it. Implement this method to tell us, WHAT we should do.
     *
     * @param event
     */
    public abstract void react_to(String sender, JSONObject event);

    public void react_to(String event) {
        react_to("_internal", new JSONObject().put("message", event));
    }

    public void react_to(JSONObject event) {
        react_to("_internal", event);
    }

    public String getName() {
        return name;
    }

    /**
     * before another game is loaded, cleanup first
     */
    public void cleanup() {
        mqttOutbound.sendCommandTo("all", MQTT.init_agent());
        // cleanup the retained messages
        roles.keys().forEach(role -> mqttOutbound.sendCommandTo(role, new JSONObject()));
    }

    /**
     * when the actual game should start. You run this method.
     */
    public abstract void start();


    /**
     * to resume a paused game.
     */
    public void resume() {
        pausing_since = Optional.empty();
    }

    /**
     * to pause a running game
     */
    public void pause() {
        pausing_since = Optional.of(LocalDateTime.now());
    }

    public boolean isPaused() {
        return pausing_since.isPresent();
    }

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return
     */
    public JSONObject getStatus() {
        return new JSONObject()
                .put("name", name)
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ISO_DATE_TIME) : JSONObject.NULL);
    }

    /**
     * the game description should contain all relevant parameters which influence the game mode. it is send out to all
     * displays during a reset()
     *
     * @param lines
     */
    public void setGameDescription(String... lines) {
        game_description.clear();
        game_description.addAll(Arrays.asList(lines));
    }

    public void reset() {
        mqttOutbound.sendCommandTo("all",
                MQTT.signal("all", "off"),
                MQTT.page0(game_description));
    }

    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

}
