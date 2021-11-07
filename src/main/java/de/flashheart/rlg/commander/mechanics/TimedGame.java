package de.flashheart.rlg.commander.mechanics;

import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.GameTimeIsUpJob;
import de.flashheart.rlg.commander.jobs.RegularGameTimeIsUpJob;
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
 * an abstract superclass for handling any game mode that has an (estimated or fixed) end_time We are talking about
 * time, NOT about END-SCORES or RUNNING OUT OF RESOURCES.
 */
@Log4j2
public abstract class TimedGame extends ScheduledGame {

    final int match_length;
    LocalDateTime start_time, estimated_end_time, regular_end_time;
    final JobKey gametimeJobKey, regularGametimeJobKey;
    long remaining;

    TimedGame(String name, Multimap<String, Agent> function_to_agents, int match_length, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, function_to_agents, scheduler, mqttOutbound);
        this.match_length = match_length;
        gametimeJobKey = new JobKey(name + "-" + GameTimeIsUpJob.class.getName(), name);
        regularGametimeJobKey = new JobKey(name + "-" + RegularGameTimeIsUpJob.name, name);
        remaining = 0l;
        estimated_end_time = null;
    }

    public void pause() {
        super.pause();
        deleteJob(gametimeJobKey);
    }

    public void resume() {
        // shift the start and end_time by the number of seconds the pause lasted
        long pause_length_in_seconds = pause_start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS);
        start_time = start_time.plusSeconds(pause_length_in_seconds);
        estimated_end_time = start_time.plusSeconds(match_length); //estimated_end_time.plusSeconds(pause_length_in_seconds);
        regular_end_time = estimated_end_time;
        super.resume();
        monitorRemainingTime();
    }


    @Override
    public void start() {
        start_time = LocalDateTime.now();
        regular_end_time = start_time.plusSeconds(match_length);

    }

    @Override
    public void cleanup() {
        super.cleanup();
        start_time = null;
    }

    public abstract void game_over();

    public abstract void regular_time_up();

    void monitorRemainingTime() {
        log.debug("estimated_end_time = {}", estimated_end_time.format(DateTimeFormatter.ISO_TIME));
        if (estimated_end_time.compareTo(LocalDateTime.now()) <= 0) { // time is up - endtime has passed already
            log.debug("time is up");
            deleteJob(gametimeJobKey);
            mqttOutbound.sendCommandTo("all", TIMERS("remaining", "-1"));
            game_over();
            return;
        }


        create_job(gametimeJobKey, estimated_end_time, GameTimeIsUpJob.class);
        remaining = estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : 0l;
        log.debug("remaining seconds: {}", remaining);
        mqttOutbound.sendCommandTo("all", TIMERS("remaining", Long.toString(remaining)));

    }

    public JSONObject TIMERS(String... timers) {
        return envelope("timers", timers);
    }

    void create_job(JobKey jobKey, LocalDateTime start_time, Class<? extends Job> clazz) {
        try {
            deleteJob(jobKey);
            JobDetail job = newJob(clazz)
                    .withIdentity(jobKey)
                    .usingJobData("name_of_the_game", name) // where we find the context later
                    .build();

            jobs.add(jobKey);

            Trigger trigger = newTrigger()
                    .withIdentity(clazz.getName() + "-trigger", name)
                    .startAt(JavaTimeConverter.toDate(start_time))
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
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
