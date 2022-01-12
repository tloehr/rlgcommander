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
        final Set<JobKey> jobs;

    public ScheduledGame(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);

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

}
