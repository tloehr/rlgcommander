package de.flashheart.rlg.commander.games.jobs;

import de.flashheart.rlg.commander.games.traits.HasBombtimer;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class BombTimerJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            String uuid = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            HasBombtimer game = (HasBombtimer) jobExecutionContext.getScheduler().getContext().get(uuid);
            game.bomb_time_is_up(jobExecutionContext.getMergedJobDataMap().getString("bombid"));
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.trace("{} interrupted", getClass().getName());
        // nothing to do here
    }

}
