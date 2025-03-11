package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * an abstract superclass for handling any game mode that needs a scheduler
 */
@Log4j2
public abstract class Scheduled extends Game {
    private final HashMap<JobKey, Trigger> jobkey_trigger_map;
    protected final HashMap<String, JobKey> jobs;

    public Scheduled(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        jobkey_trigger_map = new HashMap<>();
        jobs = new HashMap<>();
        try {
            scheduler.getContext().put(uuid.toString(), this);
        } catch (SchedulerException se) {
            log.fatal(se);
        }
        setup_scheduler_jobs();
    }

    protected void setup_scheduler_jobs() {
    }

    protected void register_job(String job_key){
        jobs.put(job_key, new JobKey(job_key, uuid.toString()));
    }

    protected void deleteJob(String jobkey) {
        deleteJob(jobs.get(jobkey));
    }

    protected void deleteJob(JobKey jobKey) {
        if (jobKey == null) return;
        if (!jobkey_trigger_map.containsKey(jobKey)) return;
        try {
            log.trace("deleting Job {}", jobKey);
            scheduler.interrupt(jobKey);
            scheduler.deleteJob(jobKey);
            jobkey_trigger_map.remove(jobKey);
        } catch (SchedulerException e) {
            log.warn(e);
        }
    }

    private void delete_all_jobs(){
        jobs.values().forEach(this::deleteJob);
    };

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

            jobkey_trigger_map.put(jobKey, trigger);
            scheduler.scheduleJob(jobBuilder.build(), trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    protected void create_job(JobKey jobKey, SimpleScheduleBuilder ssb, Class<? extends Job> clazz, Optional<JobDataMap> jobDataMap) {
        deleteJob(jobKey);
        JobDetail job = newJob(clazz)
                .withIdentity(jobKey)
                .setJobData(jobDataMap.orElse(new JobDataMap()))
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(jobKey.getName() + "-trigger", uuid.toString())
                .startNow()
                .withSchedule(ssb)
                .usingJobData("uuid", uuid.toString()) // where we find the context later
                .build();

        jobkey_trigger_map.put(jobKey, trigger);
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
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
    public void on_reset() {
        super.on_reset();
        delete_all_jobs();
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        delete_all_jobs();
    }

    @Override
    protected void on_cleanup() {
        delete_all_jobs();
        jobkey_trigger_map.keySet().forEach(job -> {
            try {
                scheduler.interrupt(job);
                scheduler.deleteJob(job);
                scheduler.getContext().remove(uuid.toString());
            } catch (SchedulerException e) {
                log.error(e);
            }
        });
        jobkey_trigger_map.clear();
    }

}

