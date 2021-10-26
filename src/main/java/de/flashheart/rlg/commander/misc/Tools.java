package de.flashheart.rlg.commander.misc;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;

public class Tools {
    public static JSONObject merge(JSONObject... jsons) {
        HashMap<String, Object> map = new HashMap<>();
        Arrays.stream(jsons).forEach(json -> map.putAll(json.toMap()));
        return new JSONObject(map);
    }
}
