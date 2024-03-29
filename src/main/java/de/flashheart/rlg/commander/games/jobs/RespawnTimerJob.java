package de.flashheart.rlg.commander.games.jobs;

import de.flashheart.rlg.commander.games.WithRespawns;
import de.flashheart.rlg.commander.games.traits.HasTimedRespawn;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class RespawnTimerJob extends QuartzJobBean implements InterruptableJob {
    public static final String _sender_TIMED_RESPAWN = "_timed_respawn_";

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            HasTimedRespawn game = (HasTimedRespawn) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            game.on_time_to_respawn(jobExecutionContext.getMergedJobDataMap());
        } catch (SchedulerException e) {
            log.error(e);
        }
    }


    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.debug("{} interrupted", getClass().getName());
        // nothing to do here
    }

}
