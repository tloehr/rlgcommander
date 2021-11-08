package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.mechanics.Game;
import de.flashheart.rlg.commander.mechanics.TimedGame;
import lombok.extern.log4j.Log4j2;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
@Log4j2
public class OvertimeJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("name_of_the_game");
            TimedGame timedGame = (TimedGame) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            timedGame.overtime();
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.debug("{} interrupted", getClass().getName());
        // nothing to do here
    }

}