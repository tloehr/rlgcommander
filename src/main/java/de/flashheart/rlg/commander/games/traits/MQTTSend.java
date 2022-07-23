package de.flashheart.rlg.commander.games.traits;

import org.json.JSONObject;

public interface MQTTSend {
    void send(String cmd, JSONObject payload, String agent);
}
