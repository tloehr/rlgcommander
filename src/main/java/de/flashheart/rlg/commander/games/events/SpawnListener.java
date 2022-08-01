package de.flashheart.rlg.commander.games.events;

import org.json.JSONObject;

import java.util.EventListener;

public interface SpawnListener extends EventListener {
    void onSpawnEventOccured(SpawnEvent event);

    void send(String cmd, JSONObject payload, String agent);
}
