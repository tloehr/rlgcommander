package de.flashheart.rlg.commander.games;

import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

@Log4j2
public abstract class Game {
    final String name;
    final MQTTOutbound mqttOutbound;
    final HashSet<String> function_groups;
    final Multimap<String, Agent> function_to_agents;
    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    final ArrayList<String> game_description_display;

    Game(String name, Multimap<String, Agent> function_to_agents, MQTTOutbound mqttOutbound) {
        this.name = name;
        this.function_to_agents = function_to_agents;
        this.mqttOutbound = mqttOutbound;
        function_groups = new HashSet<>(function_to_agents.keySet()); // this contains the agent group names : "sirens", "leds", "red_spawn", "green_spawn"´
        // functional subsriptions
        function_groups.forEach(group -> function_to_agents.get(group).forEach(agent -> mqttOutbound.sendCommandTo(agent, new JSONObject().put("subscribe_to", group))));
        game_description_display = new ArrayList<>();
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
        function_groups.forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));
    }

    /**
     * when the actual game should start. You run this method.
     */
    public abstract void start();

    public abstract void pause();

    public abstract void resume();


    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return
     */
    public JSONObject getStatus() {
        return new JSONObject().put("name", name);
    }

    /**
     * returns a json array with the description of the gamemode in 4 lines by 20 cols.
     *
     * @return
     */
    public String[] getDisplay() {
        return game_description_display.toArray(new String[]{});
    }

    public void reset() {
        mqttOutbound.sendCommandTo("all",
                MQTT.pages(MQTT.page_content("page0", getDisplay())));
        mqttOutbound.sendCommandTo("leds",
                MQTT.signal("led_all", "∞:on,1000;off,1000"));
        mqttOutbound.sendCommandTo("sirens",
                MQTT.merge(MQTT.signal("led_all", "off"), MQTT.signal("sir_all", "off")));

    }
}
