package de.flashheart.rlg.commander.controller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class MQTT {
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

//    public static JSONObject del_pages(String... page_handle) {
//        return new JSONObject().put("del_pages", new JSONArray(page_handle));
//    }


//    public static JSONObject init_agent() {
//        return new JSONObject()
//                .put("init", "");
//    }

//    public static JSONObject signal(String... signals) {
//        return signal(new JSONObject(), signals);
//    }
//
//    public static JSONObject signal(JSONObject template, String... signals) {
//        return toJSON("signal", template, signals);
//    }

//    public static JSONObject envelope(String key, JSONObject template, String... signals) {
//        return new JSONObject().put(key, fromPairs(template, signals));
//    }

//    public static JSONObject toJSON(String key, String... signals) {
//        return new JSONObject().put(key, fromPairs(new JSONObject(), signals));
//    }

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

//    public static JSONObject LED_ALL_OFF() {
//        return new JSONObject()
//                .put("led_wht", "off")
//                .put("led_red", "off")
//                .put("led_ylw", "off")
//                .put("led_grn", "off")
//                .put("led_blu", "off");
//    }
//
//    public static JSONObject SIR_ALL_OFF() {
//        return new JSONObject()
//                .put("sir1", "off")
//                .put("sir2", "off")
//                .put("sir3", "off");
//    }
//
//    public static JSONObject merge(JSONObject... jsons) {
//        HashMap<String, Object> map = new HashMap<>();
//        Arrays.stream(jsons).forEach(json -> map.putAll(json.toMap()));
//        return new JSONObject(map);
//    }

//    public static JSONObject VARS(String... vars) {
//           return toJSON("vars", vars);
//       }


}
