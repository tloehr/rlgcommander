package de.flashheart.rlg.commander.controller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

public class MQTT {
    /**
     * helper method to create a set_page command
     *
     * @param page_handle
     * @param content
     * @return
     */
    public static JSONObject page_content(String page_handle, String... content) {
        final JSONArray lines = new JSONArray();
        Arrays.stream(content).forEach(line -> lines.put(line));
        return new JSONObject().put("set_page", new JSONObject().put("handle", page_handle).put("content", lines));
    }


    public static JSONObject init_agent() {
        return new JSONObject()
                .put("init", "");
    }


    public static JSONObject score(String score) {
        return new JSONObject().put("score", score);
    }


}
