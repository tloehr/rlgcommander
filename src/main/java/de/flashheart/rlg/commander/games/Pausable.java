package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.ContinueGameJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * extend this class if You want Your game to be pausable with resume and countdown.
 */
@Log4j2
public abstract class Pausable extends Scheduled {
    protected final int resume_countdown;
    private final JobKey continueGameJob;
    protected Optional<LocalDateTime> pausing_since;
    // this is a map of all jobs with their trigger time, defined by child classes that
    // needs to be paused and revived when game is continuing.
    protected HashSet<JobKey> jobs_to_reschedule_after_pause;
    protected HashSet<JobKey> jobs_to_suspend_during_pause;

    /**
     * games deriving from this class will be able to pause and resume during game
     *
     * @param game_parameters - requires int "resume_countdown" in game_parameters. if >0 a countdown is provided before
     *                        resuming after PAUSING
     * @param scheduler
     * @param mqttOutbound
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public Pausable(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.resume_countdown = game_parameters.optInt("resume_countdown");
        this.continueGameJob = new JobKey("continue_the_game", uuid.toString());
        this.jobs_to_reschedule_after_pause = new HashSet<>();
        this.jobs_to_suspend_during_pause = new HashSet<>();
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) deleteJob(continueGameJob);
        if (message.equals(_msg_PAUSE)) {
            pausing_since = Optional.of(LocalDateTime.now());
            jobs_to_reschedule_after_pause.forEach(jobKey -> pause_job(jobKey));
            jobs_to_suspend_during_pause.forEach(jobKey -> pause_job(jobKey));
            send("acoustic", MQTT.toJSON(MQTT.SIR1, "game_ends"), roles.get("sirens"));
        }
        if (message.equals(_msg_CONTINUE)) {
            jobs_to_reschedule_after_pause.removeIf(jobKey -> !check_exists(jobKey));
            final long seconds_elapsed = ChronoUnit.SECONDS.between(pausing_since.get(), LocalDateTime.now());
            jobs_to_reschedule_after_pause.forEach(jobKey -> reschedule_job(jobKey, seconds_elapsed));
            jobs_to_suspend_during_pause.forEach(jobKey -> resume_job(jobKey));
            send("acoustic", MQTT.toJSON(MQTT.SIR1, "game_starts"), roles.get("sirens"));
        }
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_PROLOG)) {
            deleteJob(continueGameJob);
            pausing_since = Optional.empty();
        }
        if (state.equals(_state_RUNNING)) {
            pausing_since = Optional.empty();
        }
        if (state.equals(_state_RESUMING)) {
            if (resume_countdown > 0) {
                create_job(continueGameJob, LocalDateTime.now().plusSeconds(resume_countdown - 1), ContinueGameJob.class, Optional.empty());
            } else {
                process_internal_message(_msg_CONTINUE);
            }
        }
    }

    protected void create_resumable_job(JobKey jobKey, LocalDateTime start_time, Class<? extends Job> clazz, Optional<JobDataMap> jobDataMap) {
        create_job(jobKey, start_time, clazz, jobDataMap);
        jobs_to_reschedule_after_pause.add(jobKey);
    }

    protected void create_resumable_job(JobKey jobKey, SimpleScheduleBuilder ssb, Class<? extends Job> clazz, Optional<JobDataMap> jobDataMap) {
        create_job(jobKey, ssb, clazz, jobDataMap);
        jobs_to_reschedule_after_pause.add(jobKey);
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

    @Override
    protected void deleteJob(JobKey jobKey) {
        super.deleteJob(jobKey);
        jobs_to_reschedule_after_pause.remove(jobKey);
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("resume_countdown", resume_countdown)
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)) : JSONObject.NULL);
    }
}
