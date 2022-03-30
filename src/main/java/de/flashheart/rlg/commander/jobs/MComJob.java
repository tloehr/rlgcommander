package de.flashheart.rlg.commander.jobs;

import de.flashheart.rlg.commander.games.Conquest;
import de.flashheart.rlg.commander.games.Game;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Log4j2
public class MComJob extends QuartzJobBean implements InterruptableJob {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        try {
            String uuid = jobExecutionContext.getMergedJobDataMap().getString("uuid");
            String agent = jobExecutionContext.getMergedJobDataMap().getString("agent");
            int sector_number = jobExecutionContext.getMergedJobDataMap().getInt("sector");
            Game game = (Game) jobExecutionContext.getScheduler().getContext().get(uuid);
            game.process_message("_bombtimer_", agent,
                    new JSONObject()
                            .put("message", "bomb_time_up")
                            .put("sector", sector_number)
            );
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
