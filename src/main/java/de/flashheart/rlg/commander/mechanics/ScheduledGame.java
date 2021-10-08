package de.flashheart.rlg.commander.mechanics;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * an abstract superclass for handling any game mode that needs an scheduler
 */
@Log4j2
public abstract class ScheduledGame extends Game {
    LocalDateTime pause_start_time;
    final Scheduler scheduler;
    final Set<JobKey> jobs;

    public ScheduledGame(String name, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, mqttOutbound);
        this.scheduler = scheduler;
        jobs = new HashSet<>();
        try {
            scheduler.getContext().put(name, this);
        } catch (SchedulerException se) {
            log.fatal(se);
        }
    }

    protected void deleteJob(JobKey jobKey) {
        if (jobKey == null) return;
        try {
            scheduler.interrupt(jobKey);
            scheduler.deleteJob(jobKey);
            jobs.remove(jobKey);
        } catch (SchedulerException e) {
            log.warn(e);
        }
    }

    @Override
    public void stop() {
        jobs.forEach(job -> {
            try {
                scheduler.interrupt(job);
                scheduler.deleteJob(job);
            } catch (SchedulerException e) {
                log.error(e);
            }
        });
        jobs.clear();
    }


    @Override
    public void cleanup() {
        stop();
        try {
            scheduler.getContext().remove(name);
        } catch (SchedulerException se) {
            log.fatal(se);
        }
    }

    /**
     * to resume a paused game.
     */
    public void resume() {
        if (pause_start_time == null) return; // no pause, no resume
    }

    /**
     * to pause a running game
     */
    public void pause() {
        pause_start_time = LocalDateTime.now();
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("pause_start_time", pause_start_time != null ? pause_start_time.format(DateTimeFormatter.ISO_DATE_TIME) : JSONObject.NULL);
    }

}
