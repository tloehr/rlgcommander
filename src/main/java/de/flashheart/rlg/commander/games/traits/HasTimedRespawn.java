package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

public interface HasTimedRespawn {
    void on_time_to_respawn(JobDataMap map);
}
