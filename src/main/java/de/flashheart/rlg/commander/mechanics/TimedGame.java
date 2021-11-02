package de.flashheart.rlg.commander.mechanics;

import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.GameTimeIsUpJob;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.service.Agent;
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
    LocalDateTime start_time, estimated_end_time;
    final JobKey gametimeJobKey;
    long remaining;

    TimedGame(String name, Multimap<String, Agent> function_to_agents, int match_length, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, function_to_agents, scheduler, mqttOutbound);
        this.match_length = match_length;
        gametimeJobKey = new JobKey(name + "-" + GameTimeIsUpJob.name, name);
        remaining = 0l;
    }

    public void pause() {
        super.pause();
        deleteJob(gametimeJobKey);
    }

    public void resume() {
        if (pause_start_time == null) return;
        // shift the start and end_time by the number of seconds the pause lasted
        long pause_length_in_seconds = pause_start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS);
        start_time = start_time.plusSeconds(pause_length_in_seconds);
        estimated_end_time = estimated_end_time.plusSeconds(pause_length_in_seconds);

        super.resume();
        monitorRemainingTime();
    }


    @Override
    public void start() {
        start_time = LocalDateTime.now();
    }

    @Override
    public void cleanup() {
        super.cleanup();
        start_time = null;
    }

    void monitorRemainingTime() {
        if (estimated_end_time.compareTo(LocalDateTime.now()) <= 0) { // time is up
            deleteJob(gametimeJobKey);
            react_to("TIMES_UP");
            return;
        }

        try {
            deleteJob(gametimeJobKey);
            JobDetail job = newJob(GameTimeIsUpJob.class)
                    .withIdentity(gametimeJobKey)
                    .usingJobData("name_of_the_game", name) // where we find the context later
                    .build();

            jobs.add(gametimeJobKey);

            Trigger trigger = newTrigger()
                    .withIdentity(GameTimeIsUpJob.name + "-trigger", name)
                    .startAt(JavaTimeConverter.toDate(estimated_end_time))
                    .build();
            scheduler.scheduleJob(job, trigger);

            log.debug("estimated_end_time = {}", estimated_end_time.format(DateTimeFormatter.ISO_DATE_TIME));
            remaining = estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : 0l;
            log.debug("remaining seconds: {}", remaining);
            mqttOutbound.sendCommandTo("all", REMAINING());
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    public JSONObject REMAINING() {
        return new JSONObject().put("remaining", remaining);
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("match_length", match_length)
                .put("estimated_end_time", estimated_end_time != null ? estimated_end_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("remaining", remaining)
                .put("match_starting_time", start_time != null ? start_time.format(DateTimeFormatter.ISO_DATE_TIME) : "not started yet");
    }

}
