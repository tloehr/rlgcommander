package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

public interface HasActivation {
    void activate(JobDataMap map);
}
