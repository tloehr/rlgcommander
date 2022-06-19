package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.games.Conquest;
import de.flashheart.rlg.commander.games.Game;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * this job runs the current game after a certain amount of time.
 * to implement some countdown timer before the match starts.
 */
@Log4j2
public class RunGameJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            Game game = (Game) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            game.process_message(Game._msg_RUN);
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
