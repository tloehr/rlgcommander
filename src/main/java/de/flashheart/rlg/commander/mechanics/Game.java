package de.flashheart.rlg.commander.mechanics;

import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;

@Log4j2
public abstract class Game {
    final String name;
    final MQTTOutbound mqttOutbound;
    final HashSet<String> function_groups;
    final Multimap<String, Agent> function_to_agents;

    Game(String name, Multimap<String, Agent> function_to_agents, MQTTOutbound mqttOutbound) {
        this.name = name;
        this.function_to_agents = function_to_agents;
        this.mqttOutbound = mqttOutbound;
        function_groups = new HashSet<>(function_to_agents.keySet()); // this contains the agent group names : "sirens", "leds", "red_spawn", "green_spawn"´
        // functional subsriptions
        function_groups.forEach(group -> function_to_agents.get(group).forEach(agent -> mqttOutbound.sendCommandTo(agent, new JSONObject().put("subscribe_to", group))));
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

    /**
     * we call this method after constructor is finished
     */
    public abstract void init();


    /**
     * before another game is loaded, cleanup first
     */
    public void cleanup() {
        mqttOutbound.sendCommandTo("all", new JSONObject().put("init", ""));
        function_groups.forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));
    }

    /**
     * when the actual game should start. You run this method.
     */
    public abstract void start();


    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return
     */
    public JSONObject getStatus() {
        return new JSONObject().put("name", name);
    }

    public JSONObject signal(String... signals) {
        return signal(new JSONObject(), signals);
    }

    public JSONObject signal(JSONObject json_to_start_with, String... signals) {
        return envelope("signal", json_to_start_with, signals);
    }

    public JSONObject envelope(String key, JSONObject json_to_start_with, String... signals) {
        return new JSONObject().put(key, fromPairs(json_to_start_with, signals));
    }

    public JSONObject envelope(String key, String... signals) {
        return new JSONObject().put(key, fromPairs(new JSONObject(), signals));
    }

    public JSONObject fromPairs(String... pairs) {
        return fromPairs(new JSONObject(), pairs);
    }

    public JSONObject fromPairs(JSONObject json_to_start_with, String... pairs) {
        if (pairs.length == 0 || pairs.length % 2 != 0) return json_to_start_with;
        for (int s = 0; s < pairs.length; s += 2) {
            json_to_start_with.put(pairs[s], pairs[s + 1]);
        }
        return json_to_start_with;
    }

    public JSONObject LED_ALL_OFF() {
        return new JSONObject()
                .put("led_wht", "off")
                .put("led_red", "off")
                .put("led_ylw", "off")
                .put("led_grn", "off")
                .put("led_blu", "off");
    }

    public JSONObject SIR_ALL_OFF() {
        return new JSONObject()
                .put("sir1", "off")
                .put("sir2", "off")
                .put("sir3", "off");
    }


    public abstract void reset();
}
