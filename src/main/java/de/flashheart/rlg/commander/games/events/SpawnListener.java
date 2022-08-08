package de.flashheart.rlg.commander.games.events;

import java.util.EventListener;

public interface SpawnListener extends EventListener {
    void handleRespawnEvent(SpawnEvent event);
    void createRunGameJob(SpawnEvent event);
}
