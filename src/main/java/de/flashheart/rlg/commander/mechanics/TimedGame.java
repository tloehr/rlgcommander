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

    /**
     * the length of the match in seconds. Due to the nature of certain gamemodes (like farcry) this value is rather a
     * proposal than a constant value
     */
    final int match_length;
    /**
     * start_time is the timestamp when the game started. it will shift forward when the game was paused and is
     * resumed.
     */
    LocalDateTime start_time;
    /**
     * estimated_end_time is the timestamp when the game will possibly end. can change due to game situations like in
     * Farcry (last moment flag activations or quick attacker)
     */
    LocalDateTime estimated_end_time;
    /**
     * this is the expected end timestamp of the game. will shift with the start_time due to pauses.
     */
    LocalDateTime regular_end_time;

    /**
     * the remaining time in seconds. from now to the end (estimated)
     */
    long remaining;

    final JobKey gametimeJobKey, regularGametimeJobKey;

    TimedGame(String name, Multimap<String, Agent> function_to_agents, int match_length, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, function_to_agents, scheduler, mqttOutbound);
        this.match_length = match_length;
        gametimeJobKey = new JobKey(name + "-" + GameTimeIsUpJob.class.getName(), name);
        regularGametimeJobKey = new JobKey(name + "-" + RegularGameTimeIsUpJob.class.getName(), name);
        remaining = 0l;
        estimated_end_time = null;
    }

    @Override
    public void pause() {
        super.pause();
        deleteJob(gametimeJobKey);
        deleteJob(regularGametimeJobKey);
    }

    @Override
    public void resume() {
        // shift the start and end_time by the number of seconds the pause lasted
        long pause_length_in_seconds = pause_start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS);
        start_time = start_time.plusSeconds(pause_length_in_seconds);
        estimated_end_time = start_time.plusSeconds(match_length);
        regular_end_time = estimated_end_time;
        create_job(regularGametimeJobKey, regular_end_time, RegularGameTimeIsUpJob.class);
        super.resume();
        monitorRemainingTime();
    }


    @Override
    public void start() {
        start_time = LocalDateTime.now();
        regular_end_time = start_time.plusSeconds(match_length);
        create_job(regularGametimeJobKey, regular_end_time, RegularGameTimeIsUpJob.class);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        start_time = null;
    }

    public void game_over() {
        log.debug("time is up");
        mqttOutbound.sendCommandTo("all", TIMERS("remaining", "-1"));
    }

    public void overtime() {
        log.debug("overtime");
        overtime();
    }

    void monitorRemainingTime() {
        log.debug("estimated_end_time = {}, regular_end_time = {}", estimated_end_time.format(DateTimeFormatter.ISO_TIME), regular_end_time.format(DateTimeFormatter.ISO_TIME));

        create_job(gametimeJobKey, estimated_end_time, GameTimeIsUpJob.class);
        remaining = estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : 0l;
        log.debug("remaining seconds: {}", remaining);
        mqttOutbound.sendCommandTo("all", TIMERS("remaining", Long.toString(remaining)));

    }

    JSONObject TIMERS(String... timers) {
        return envelope("timers", timers);
    }

    void create_job(JobKey jobKey, LocalDateTime start_time, Class<? extends Job> clazz) {
        try {
            deleteJob(jobKey);
            JobDetail job = newJob(clazz)
                    .withIdentity(jobKey)
                    .usingJobData("name_of_the_game", name)
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

    void create_job(JobKey jobKey, SimpleScheduleBuilder ssb, Class<? extends Job> clazz) {

        JobDetail job = newJob(clazz)
                .withIdentity(jobKey)
                .build();
        jobs.add(jobKey);

        Trigger trigger = newTrigger()
                .withIdentity(clazz.getName() + "-trigger", name)
                .startNow()
                .withSchedule(ssb)
                .usingJobData("name_of_the_game", name) // where we find the context later
                .build();


        try {
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
