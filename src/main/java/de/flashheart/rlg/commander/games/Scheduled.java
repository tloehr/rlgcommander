package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * an abstract superclass for handling any game mode that needs an scheduler
 */
@Log4j2
public abstract class Scheduled extends Game {
    final Set<JobKey> jobs;

    public Scheduled(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException {
        super(game_parameters, scheduler, mqttOutbound);

        jobs = new HashSet<>();
        try {
            scheduler.getContext().put(uuid.toString(), this);
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
        create_job(jobKey, start_time, clazz, Optional.empty());
    }

    void create_job(JobKey jobKey, LocalDateTime start_time, Class<? extends Job> clazz, Optional<JobDataMap> jobDataMap) {
        try {
            deleteJob(jobKey);
            JobBuilder jobBuilder = newJob(clazz).withIdentity(jobKey).withIdentity(jobKey)
                    .usingJobData("uuid", uuid.toString());
            if (jobDataMap.isPresent()) jobBuilder.usingJobData(jobDataMap.get());

            jobs.add(jobKey);

            Trigger trigger = newTrigger()
                    .withIdentity(jobKey.getName() + "-trigger", uuid.toString())
                    .startAt(JavaTimeConverter.toDate(start_time))
                    .build();
            scheduler.scheduleJob(jobBuilder.build(), trigger);
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
                .withIdentity(jobKey.getName() + "-trigger", uuid.toString())
                .startNow()
                .withSchedule(ssb)
                .usingJobData("uuid", uuid.toString()) // where we find the context later
                .build();

        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    @Override
    protected void on_cleanup() {
        jobs.forEach(job -> {
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

