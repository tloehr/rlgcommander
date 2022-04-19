package de.flashheart.rlg.commander.jobs;

import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class RespawnJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
//        try {
//            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
//            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("uuid");
//            HasRespawn game = (HasRespawn) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
//            game.respawn();
//        } catch (SchedulerException e) {
//            log.error(e);
//        }
    }


    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.debug("{} interrupted", getClass().getName());
        // nothing to do here
    }

}
