package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.mechanics.Game;
import lombok.extern.log4j.Log4j2;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
@Log4j2
public class GameTimeIsUpJob extends QuartzJobBean implements InterruptableJob {
    public static final String name = "gametimejob1";

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            log.debug(jobExecutionContext.getJobDetail().getKey() + " executed");
            Game game = (Game) jobExecutionContext.getScheduler().getContext().get("game");
            game.react_to("", "TIMES_UP");
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.debug("{} interrupted", name);
        // nothing to do here
    }

}
