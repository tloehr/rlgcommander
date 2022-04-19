package de.flashheart.rlg.commander.misc;

import org.json.JSONObject;

public class GameStateEvent {
    protected final JSONObject state;

    public GameStateEvent(JSONObject state) {
        this.state = state;
    }

    public GameStateEvent() {
        this.state = new JSONObject();
    }

    public JSONObject getState() {
        return state;
    }
}
