package de.flashheart.rlg.commander.games;

import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * an abstract superclass for handling any game mode that needs an scheduler
 */
@Log4j2
public abstract class ScheduledGame extends Game {
    Optional<LocalDateTime> pausing_since;
    final Scheduler scheduler;
    final Set<JobKey> jobs;

    public ScheduledGame(String name, Multimap<String, Agent> function_to_agents, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, function_to_agents, mqttOutbound);
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
            log.debug("deleting Job {}", jobKey);
            scheduler.interrupt(jobKey);
            scheduler.deleteJob(jobKey);
            jobs.remove(jobKey);
        } catch (SchedulerException e) {
            log.warn(e);
        }
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
                    .withIdentity(jobKey.getName() + "-trigger", name)
                    .startAt(JavaTimeConverter.toDate(start_time))
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    void create_job(JobKey jobKey, SimpleScheduleBuilder ssb, Class<? extends Job> clazz) {
        deleteJob(jobKey);
        JobDetail job = newJob(clazz)
                .withIdentity(jobKey)
                .build();
        jobs.add(jobKey);

        Trigger trigger = newTrigger()
                .withIdentity(jobKey.getName() + "-trigger", name)
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
    public void cleanup() {
        super.cleanup();
        jobs.forEach(job -> {
            try {
                scheduler.interrupt(job);
                scheduler.deleteJob(job);
                scheduler.getContext().remove(name);
            } catch (SchedulerException e) {
                log.error(e);
            }
        });
        jobs.clear();
    }

    /**
     * to resume a paused game.
     */
    @Override
    public void resume() {
        pausing_since = Optional.empty();
    }

    /**
     * to pause a running game
     */
    @Override
    public void pause() {

        pausing_since = Optional.of(LocalDateTime.now());
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("pause_start_time",  pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ISO_DATE_TIME) : JSONObject.NULL);
    }

}
