package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.games.WithRespawns;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class RespawnTimerJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            WithRespawns game = (WithRespawns) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            game.timed_respawn();
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