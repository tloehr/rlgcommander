package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

public interface HasTimeOut {
    void timeout(JobDataMap map);
}
