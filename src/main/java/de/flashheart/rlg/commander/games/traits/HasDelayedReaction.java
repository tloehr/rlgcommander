package de.flashheart.rlg.commander.games.traits;

import org.quartz.JobDataMap;

public interface HasDelayedReaction {
    void delayed_reaction(JobDataMap map);
}
