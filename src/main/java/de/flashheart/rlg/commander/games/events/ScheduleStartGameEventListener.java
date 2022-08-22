package de.flashheart.rlg.commander.games.events;

import java.time.LocalDateTime;
import java.util.EventListener;

public interface ScheduleStartGameEventListener extends EventListener {
    void createRunGameJob(ScheduleStartGameEvent scheduleStartGameEvent);
}
