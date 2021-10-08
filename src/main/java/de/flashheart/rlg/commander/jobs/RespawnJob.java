package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.mechanics.HasRespawn;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class RespawnJob extends QuartzJobBean implements InterruptableJob {
    public static final String name = "respawnjob1";

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            HasRespawn game = (HasRespawn) jobExecutionContext.getScheduler().getContext().get("game");
            game.respawn();
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.debug("{} interrupted", name);
        // nothing to do here
    }

}
