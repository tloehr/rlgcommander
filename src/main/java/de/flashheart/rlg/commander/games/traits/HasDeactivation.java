package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

public interface HasDeactivation {
    void deactivate(JobDataMap map);
}
