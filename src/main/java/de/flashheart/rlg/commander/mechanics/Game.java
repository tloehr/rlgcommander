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
    final Multimap<String, Agent> agent_roles;

    Game(String name, Multimap<String, Agent> agent_roles, MQTTOutbound mqttOutbound) {
        this.name = name;
        this.agent_roles = agent_roles;
        this.mqttOutbound = mqttOutbound;
        function_groups = new HashSet<>(agent_roles.keySet()); // this contains the agent group names : "sirens", "leds", "red_spawn", "green_spawn"´
//        // clearing all additional subscriptions from agents
//        mqttOutbound.sendCommandTo("all", new JSONObject().put("init", ""));
//        // reset old retained messages if conflicting
//        function_groups.forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));
        // functional subsriptions
        function_groups.forEach(group -> agent_roles.get(group).forEach(agent -> mqttOutbound.sendCommandTo(agent, new JSONObject().put("subscribe_to", group))));
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

    /**
     * simple envelope for a signal (former pin_scheme)
     *
     * @param jsonObject
     * @return
     */
    public JSONObject signal(JSONObject jsonObject) {
        return new JSONObject().put("signal", jsonObject);
    }

    public JSONObject signal(String... signals) {
        return signal(new JSONObject(), signals);
    }

    public JSONObject signal(JSONObject json_to_start_with, String... signals) {
        if (signals.length == 0 || signals.length % 2 != 0) return new JSONObject();
        for (int s = 0; s < signals.length; s += 2) {
            json_to_start_with.put(signals[s], signals[s + 1]);
        }
        return new JSONObject().put("signal", json_to_start_with);
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

    public JSONObject score(String score) {
        return new JSONObject().put("score", score);
    }

    /**
     * helper method to create a set_page command
     *
     * @param page_handle
     * @param content
     * @return
     */
    public JSONObject page_content(String page_handle, String... content) {
        final JSONArray lines = new JSONArray();
        Arrays.stream(content).forEach(line -> lines.put(line));
        return new JSONObject().put("set_page", new JSONObject().put("handle", page_handle).put("content", lines));
    }
}
