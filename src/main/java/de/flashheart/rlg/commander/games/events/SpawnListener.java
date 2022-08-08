package de.flashheart.rlg.commander.games.events;

import java.time.LocalDateTime;
import java.util.EventListener;

public interface SpawnListener extends EventListener {
    void handleRespawnEvent(SpawnEvent event);
    void createRunGameJob(LocalDateTime start_time);
}
