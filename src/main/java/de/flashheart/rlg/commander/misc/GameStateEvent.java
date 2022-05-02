package de.flashheart.rlg.commander.misc;

import org.json.JSONObject;

public class GameStateEvent {
    protected final JSONObject status;

    public GameStateEvent(JSONObject status) {
        this.status = status;
    }

    public GameStateEvent() {
        this.status = new JSONObject();
    }

    public JSONObject getStatus() {
        return status;
    }
}
