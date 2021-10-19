package de.flashheart.rlg.commander.mechanics;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
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

    Game(String name, MQTTOutbound mqttOutbound) {
        this.name = name;
        this.mqttOutbound = mqttOutbound;
        function_groups = new HashSet<>();
    }

    public HashSet<String> getFunction_groups() {
        return function_groups;
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
     * call after constructor is finished
     */
    public abstract void init();


    /**
     * when the actual game should start. You run this method.
     */
    public abstract void start();

    /**
     * when the game should end NOW. Now questions asked. Simply cleanup Your stuff an unload the game. stop() does it.
     */
    public void stop(){
        mqttOutbound.sendCommandTo("all", new JSONObject().put("init", ""));
    }


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
        if (signals.length == 0 || signals.length % 2 != 0) return new JSONObject();
        JSONObject signal = new JSONObject();
        for (int s = 0; s < signals.length; s += 2) {
            signal.put(signals[s], signals[s + 1]);
        }
        return new JSONObject().put("signal", signal);
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
