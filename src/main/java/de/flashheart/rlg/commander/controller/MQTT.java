package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.misc.Tools;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;

public class MQTT {
    /**
     * helper method to create a set_page command
     *
     * @param page_handle
     * @param content
     * @return
     */
    public static JSONObject page_content(String page_handle, String... content) {
        return new JSONObject().put(page_handle, new JSONArray(content));
    }

    public static JSONObject set_page(JSONObject... pages) {
        return new JSONObject().put("set_page", merge(pages));
    }

    public static JSONObject add_page(String... page_handle) {
        return new JSONObject().put("add_page", new JSONArray(page_handle));
    }

    public static JSONObject del_pages(String... page_handle) {
          return new JSONObject().put("del_pages", new JSONArray(page_handle));
      }


    public static JSONObject init_agent() {
        return new JSONObject()
                .put("init", "");
    }


    public static JSONObject score(String score) {
        return new JSONObject().put("score", score);
    }


    public static JSONObject signal(String... signals) {
        return signal(new JSONObject(), signals);
    }

    public static JSONObject signal(JSONObject json_to_start_with, String... signals) {
        return envelope("signal", json_to_start_with, signals);
    }

    public static JSONObject envelope(String key, JSONObject json_to_start_with, String... signals) {
        return new JSONObject().put(key, fromPairs(json_to_start_with, signals));
    }

    public static JSONObject envelope(String key, String... signals) {
        return new JSONObject().put(key, fromPairs(new JSONObject(), signals));
    }

    public static JSONObject fromPairs(String... pairs) {
        return fromPairs(new JSONObject(), pairs);
    }

    public static JSONObject fromPairs(JSONObject json_to_start_with, String... pairs) {
        if (pairs.length == 0 || pairs.length % 2 != 0) return json_to_start_with;
        for (int s = 0; s < pairs.length; s += 2) {
            json_to_start_with.put(pairs[s], pairs[s + 1]);
        }
        return json_to_start_with;
    }

    public static JSONObject LED_ALL_OFF() {
        return new JSONObject()
                .put("led_wht", "off")
                .put("led_red", "off")
                .put("led_ylw", "off")
                .put("led_grn", "off")
                .put("led_blu", "off");
    }

    public static JSONObject SIR_ALL_OFF() {
        return new JSONObject()
                .put("sir1", "off")
                .put("sir2", "off")
                .put("sir3", "off");
    }

    public static JSONObject merge(JSONObject... jsons) {
        HashMap<String, Object> map = new HashMap<>();
        Arrays.stream(jsons).forEach(json -> map.putAll(json.toMap()));
        return new JSONObject(map);
    }

}
