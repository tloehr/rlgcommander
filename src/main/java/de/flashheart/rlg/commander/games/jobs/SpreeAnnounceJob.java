package de.flashheart.rlg.commander.games.jobs;

import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import de.flashheart.rlg.commander.games.traits.HasSpreeAnnouncement;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class SpreeAnnounceJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            HasSpreeAnnouncement game = (HasSpreeAnnouncement) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            game.spree_announce();
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
