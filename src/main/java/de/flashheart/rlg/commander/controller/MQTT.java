package de.flashheart.rlg.commander.controller;

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
    public static final String START_STOP_SIREN = SIR1;
    public static final String ALERT_SIREN = SIR2;
    public static final String SHUTDOWN_SIREN = SIR3;
    public static final String EVENT_SIREN = SIR4;
    public static final String BUZZER = "buzzer";
    public static final String ACOUSTICS = SIR1 + "|" + SIR2 + "|" + SIR3 + "|" + SIR4 + "|" + BUZZER;


    public static final String SCHEME_VEY_LONG = "very_long";
    public static final String SCHEME_LONG = "long";
    public static final String SCHEME_MEDIUM = "medium";
    public static final String SCHEME_SHORT = "short";
    public static final String SCHEME_VERY_SHORT = "very_short";
    public static final String RECURRING_SCHEME_VERY_SLOW = "very_slow";
    public static final String RECURRING_SCHEME_SLOW = "slow";
    public static final String RECURRING_SCHEME_NORMAL = "normal";
    public static final String RECURRING_SCHEME_FAST = "fast";
    public static final String RECURRING_SCHEME_VERY_FAST = "very_fast";
    public static final String RECURRING_SCHEME_NETSTATUS = "netstatus";
    public static final String SINGLE_BUZZ = "single_buzz";
    public static final String DOUBLE_BUZZ = "double_buzz";
    public static final String TRIPLE_BUZZ = "triple_buzz";

    public static final String GAME_STARTS = "game_starts";
    public static final String GAME_ENDS = "game_ends";

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
