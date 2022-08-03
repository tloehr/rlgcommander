package de.flashheart.rlg.commander.controller;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class MQTT {
    public static final String WHITE = "wht";
    public static final String RED = "red";
    public static final String YELLOW = "ylw";
    public static final String GREEN = "grn";
    public static final String BLUE = "blu";
    public static final String ALL = "all";
    public static final String VISUALS = WHITE + "|" + RED + "|" + YELLOW + "|" + GREEN + "|" + BLUE;

    public static final String SIR1 = "sir1";
    public static final String SIR2 = "sir2";
    public static final String SIR3 = "sir3";
    public static final String SIR4 = "sir4";
    public static final String BUZZER = "buzzer";
    public static final String ACOUSTICS = SIR1 + "|" + SIR2 + "|" + SIR3 + "|" + SIR4 + "|" + BUZZER;


    /**
     * helper method to create a set_page command
     *
     * @param page_handle
     * @param content
     * @return
     */
    public static JSONObject page(String page_handle, String... content) {
        return new JSONObject().put(page_handle, new JSONArray(content));
    }

    public static JSONObject page(String page_handle, Collection<String> content) {
        return new JSONObject().put(page_handle, new JSONArray(content));
    }

    public static JSONObject toJSON(String... pairs) {
        return toJSON(new JSONObject(), pairs);
    }

    public static JSONObject toJSON(JSONObject template, String... pairs) {
        if (pairs.length == 0 || pairs.length % 2 != 0) return template;
        for (int s = 0; s < pairs.length; s += 2) {
            template.put(pairs[s], pairs[s + 1]);
        }
        return template;
    }

    public static JSONObject merge(JSONObject... jsons) {
        HashMap<String, Object> map = new HashMap<>();
        Arrays.stream(jsons).forEach(json -> map.putAll(json.toMap()));
        return new JSONObject(map);
    }

}
