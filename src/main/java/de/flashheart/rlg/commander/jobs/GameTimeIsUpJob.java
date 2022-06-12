package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.games.Timed;
import lombok.extern.log4j.Log4j2;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
@Log4j2
public class GameTimeIsUpJob extends QuartzJobBean implements InterruptableJob {
//    public static final String name = "gametimejob1";

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.trace(jobExecutionContext.getJobDetail().getKey() + " executed");
            String uuid = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            Timed timedGame = (Timed) jobExecutionContext.getScheduler().getContext().get(uuid);
            timedGame.game_time_is_up();
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.trace("{} interrupted", getClass().getName());
        // nothing to do here
    }

}
