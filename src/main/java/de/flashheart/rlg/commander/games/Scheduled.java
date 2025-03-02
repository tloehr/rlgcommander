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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    protected final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS;
    protected final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES;
    protected final long REPEAT_EVERY_MS;
    protected final long NUMBER_OF_BROADCAST_EVENTS_PER_MINUTE; // to execute something once per minute
    final HashMap<JobKey, Trigger> jobs;

    public Scheduled(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);

        BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = Long.parseLong(game_parameters.optString("BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES", "10"));
        SCORE_CALCULATION_EVERY_N_SECONDS = new BigDecimal(game_parameters.optString("SCORE_CALCULATION_EVERY_N_SECONDS","0.5"));
        REPEAT_EVERY_MS = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();
        NUMBER_OF_BROADCAST_EVENTS_PER_MINUTE = BigDecimal.valueOf(60L).divide(SCORE_CALCULATION_EVERY_N_SECONDS, RoundingMode.HALF_UP).longValue();

        jobs = new HashMap<>();
        try {
            scheduler.getContext().put(uuid.toString(), this);
        } catch (SchedulerException se) {
            log.fatal(se);
        }
    }

    protected void deleteJob(JobKey jobKey) {
        if (jobKey == null) return;
        if (!jobs.containsKey(jobKey)) return;
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

        jobs.put(jobKey, trigger);
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

