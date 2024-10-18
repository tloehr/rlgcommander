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
    public static final String LED_ALL = "led_all";
    // public static final String LED_ALL = "led_all";
    public static final String SIR_ALL = "sir_all";
    public static final String VISUALS = WHITE + "|" + RED + "|" + YELLOW + "|" + GREEN + "|" + BLUE;

    public static final String SIR1 = "sir1"; // START/STOP
    public static final String SIR2 = "sir2"; // ACTIVATE
    public static final String SIR3 = "sir3"; // DEACTIVATE
    public static final String SIR4 = "sir4"; // NEXT
    public static final String START_STOP_SIREN = SIR1;
    public static final String ALERT_SIREN = SIR2;
    public static final String SHUTDOWN_SIREN = SIR3;
    public static final String EVENT_SIREN = SIR4;
    public static final String BUZZER = "buzzer";
    public static final String ACOUSTICS = SIR1 + "|" + SIR2 + "|" + SIR3 + "|" + SIR4 + "|" + BUZZER;


    public static final String SCHEME_VERY_LONG = "very_long";
    public static final String LONG = "long";
    public static final String MEDIUM = "medium";
    public static final String SCHEME_SHORT = "short";
    public static final String SCHEME_VERY_SHORT = "very_short";
    public static final String RECURRING_SCHEME_VERY_SLOW = "very_slow";
    public static final String RECURRING_SCHEME_SLOW = "slow";
    public static final String RECURRING_SCHEME_NORMAL = "normal";
    public static final String RECURRING_SCHEME_FAST = "fast";
    public static final String OFF = "off";
    public static final String RECURRING_SCHEME_VERY_FAST = "very_fast";
    public static final String RECURRING_SCHEME_MEGA_FAST = "mega_fast";
    public static final String RECURRING_SCHEME_SIGNAL_STRENGTH = "signal_strength";
    public static final String RECURRING_SCHEME_NO_WIFI = "no_wifi";
    public static final String SINGLE_BUZZ = "single_buzz";
    public static final String DOUBLE_BUZZ = "double_buzz";
    public static final String TRIPLE_BUZZ = "triple_buzz";
    public static final String FLAG_TAKEN = "flag_taken";
    public static final String RESPAWN_SIGNAL = "respawn_signal";
    public static final String PREPARE_TEAM_SIGNAL = "prepare_team_signal";
    public static final String TEAM_HURRY_UP_SIGNAL = "team_hurry_up_signal";
    public static final String TEAM_READY_SIGNAL = "team_ready_signal";

    public static final String GAME_STARTS = "game_starts";
    public static final String GAME_ENDS = "game_ends";

    public static final String CMD_ACOUSTIC = "acoustic";
    public static final String CMD_PLAY = "play";
    public static final String CMD_VISUAL = "visual";
    public static final String CMD_DISPLAY = "paged";
    public static final String CMD_TIMERS = "timers";
    public static final String CMD_PAGED = "paged";

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
