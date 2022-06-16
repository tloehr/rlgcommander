package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * an abstract superclass for handling any game mode that needs a scheduler
 */
@Log4j2
public abstract class Scheduled extends Game {
    final HashMap<JobKey, Trigger> jobs;

    public Scheduled(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);

        jobs = new HashMap<>();
        try {
            scheduler.getContext().put(uuid.toString(), this);
        } catch (SchedulerException se) {
            log.fatal(se);
        }
    }

    protected void deleteJob(JobKey jobKey) {
        if (jobKey == null) return;
        try {
            log.trace("deleting Job {}", jobKey);
            scheduler.interrupt(jobKey);
            scheduler.deleteJob(jobKey);
            jobs.remove(jobKey);
        } catch (SchedulerException e) {
            log.warn(e);
        }
    }

    protected void create_job(JobKey jobKey, LocalDateTime start_time, Class<? extends Job> clazz, Optional<JobDataMap> jobDataMap) {
        try {
            deleteJob(jobKey);
            JobBuilder jobBuilder = newJob(clazz).withIdentity(jobKey).withIdentity(jobKey)
                    .usingJobData("uuid", uuid.toString());
            if (jobDataMap.isPresent()) jobBuilder.usingJobData(jobDataMap.get());

            Trigger trigger = newTrigger()
                    .withIdentity(jobKey.getName() + "-trigger", uuid.toString())
                    .startAt(JavaTimeConverter.toDate(start_time))
                    .build();

            jobs.put(jobKey, trigger);
            scheduler.scheduleJob(jobBuilder.build(), trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    protected void create_job(JobKey jobKey, SimpleScheduleBuilder ssb, Class<? extends Job> clazz) {
        deleteJob(jobKey);
        JobDetail job = newJob(clazz)
                .withIdentity(jobKey)
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(jobKey.getName() + "-trigger", uuid.toString())
                .startNow()
                .withSchedule(ssb)
                .usingJobData("uuid", uuid.toString()) // where we find the context later
                .build();

        jobs.put(jobKey, trigger);
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    protected void pause_job(JobKey jobKey) {
        try {
            scheduler.pauseJob(jobKey);
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    protected void reschedule_job(JobKey jobKey, long delayed_by_seconds) {
        try {
            if (!scheduler.checkExists(jobKey)) return;
            TriggerKey triggerKey = TriggerKey.triggerKey(jobKey.getName() + "-trigger", uuid.toString());
            Trigger oldTrigger = scheduler.getTrigger(triggerKey);
            LocalDateTime old_start_time = JavaTimeConverter.toJavaLocalDateTime(oldTrigger.getStartTime());

            LocalDateTime new_start_time = JavaTimeConverter.toJavaLocalDateTime(oldTrigger.getStartTime()).plusSeconds(delayed_by_seconds);

            Trigger newTrigger = newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(JavaTimeConverter.toDate(new_start_time))
                    .build();
            scheduler.rescheduleJob(triggerKey, newTrigger);
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    protected void resume_job(JobKey jobKey) {
        try {
            scheduler.resumeJob(jobKey);
        } catch (SchedulerException e) {
            log.error(e);
        }
    }

    protected boolean check_exists(JobKey jobKey) {
        boolean check;
        try {
            check = scheduler.checkExists(jobKey);
        } catch (SchedulerException e) {
            check = false;
        }
        return check;
    }

    @Override
    protected void on_cleanup() {
        jobs.keySet().forEach(job -> {
            try {
                scheduler.interrupt(job);
                scheduler.deleteJob(job);
                scheduler.getContext().remove(uuid.toString());
            } catch (SchedulerException e) {
                log.error(e);
            }
        });
        jobs.clear();
    }

}

