package de.flashheart.rlg.commander.mechanics;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.GameTimeIsUpJob;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * an abstract superclass for handling any game mode that has an end_time. estimated or fixed
 */
@Log4j2
public abstract class TimedGame extends ScheduledGame {

    final int match_length;
    LocalDateTime match_starting_time, estimated_end_time;
    final JobKey gametimeJobKey;

    TimedGame(String name, int match_length, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, scheduler, mqttOutbound);
        this.match_length = match_length;
        gametimeJobKey = new JobKey(name + "-" + GameTimeIsUpJob.name, name);
    }

    public void pause() {
        super.pause();
        deleteJob(gametimeJobKey);
    }

    public void resume() {
        super.resume();
        // shift the end_time by the number of seconds the pause lasted
        long pause_length_in_seconds = pause_start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS);
        estimated_end_time = estimated_end_time.plusSeconds(pause_length_in_seconds);
        pause_start_time = null;
        monitorRemainingTime();
    }


    @Override
    public void start() {
        match_starting_time = LocalDateTime.now();
        estimated_end_time = match_starting_time.plusSeconds(match_length);
        monitorRemainingTime();
    }

    void monitorRemainingTime() {
        if (estimated_end_time.compareTo(LocalDateTime.now()) <= 0) { // time is up
            deleteJob(gametimeJobKey);
            react_to( "TIMES_UP");
            return;
        }

        try {
            deleteJob(gametimeJobKey);
            JobDetail job = newJob(GameTimeIsUpJob.class)
                    .withIdentity(gametimeJobKey)
                    .build();

            jobs.add(gametimeJobKey);

            Trigger trigger = newTrigger()
                    .withIdentity(GameTimeIsUpJob.name + "-trigger", name)
                    .startAt(JavaTimeConverter.toDate(estimated_end_time))
                    .build();
            scheduler.scheduleJob(job, trigger);

            log.debug("estimated_end_time = " + estimated_end_time.format(DateTimeFormatter.ISO_DATE_TIME));
        } catch (SchedulerException e) {
            log.fatal(e);
            System.exit(0);
        }

    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("match_length", match_length)
                .put("estimated_end_time", estimated_end_time != null ? estimated_end_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("remaining", estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) : 0l)
                .put("match_starting_time", match_starting_time != null ? match_starting_time.format(DateTimeFormatter.ISO_DATE_TIME) : "not started yet");
    }

}
