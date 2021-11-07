package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.mechanics.ConquestGame;
import lombok.extern.log4j.Log4j2;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class ConquestTicketBleedingJob extends QuartzJobBean implements InterruptableJob {
//    public static final String name = "TicketBleedingJob";

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            //log.debug(jobExecutionContext.getJobDetail().getKey().getName() + " executed");
            //String gamename = jobExecutionContext.getJobDetail().getKey().getGroup();
            String name_of_the_game = jobExecutionContext.getMergedJobDataMap().getString("name_of_the_game");
            ConquestGame game = (ConquestGame) jobExecutionContext.getScheduler().getContext().get(name_of_the_game);
            game.ticket_bleeding_cycle();
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
