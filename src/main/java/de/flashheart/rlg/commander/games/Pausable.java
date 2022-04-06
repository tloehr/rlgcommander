package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ContinueGameJob;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;

/**
 * extend this class if You want Your game to be pausable with resume and countdown.
 */
public abstract class Pausable extends Scheduled {
    protected final int resume_countdown;
    private final JobKey continueGameJob;
    protected Optional<LocalDateTime> pausing_since;

    /**
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
        this.resume_countdown = game_parameters.getInt("resume_countdown");
        this.continueGameJob = new JobKey("continue_the_game", uuid.toString());
        pausing_since = Optional.empty();
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        if (message.equals(_msg_RUN)) {
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_START, "led_all", "off"), roles.get("sirens"));
            deleteJob(continueGameJob);
        }
        if (message.equals(_msg_PAUSE)) {
            pausing_since = Optional.of(LocalDateTime.now());
        }
        if (message.equals(_msg_CONTINUE)) {
            pausing_since = Optional.empty();
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_START, "led_all", "off"), roles.get("sirens"));
        }
    }

    @Override
    protected void at_state(String state) {
        if (state.equals(_state_PROLOG)) {
            mqttOutbound.send("signals", MQTT.toJSON("led_all", "off"), roles.get("sirens"));
            mqttOutbound.send("paged",
                    MQTT.page("page0",
                            "I am ${agentname}", "", "I will be a", "Siren"), roles.get("sirens"));
            deleteJob(continueGameJob);
        }
        if (state.equals(_state_RESUMING)) {
            if (resume_countdown > 0) {
                create_job(continueGameJob, LocalDateTime.now().plusSeconds(resume_countdown - 1), ContinueGameJob.class);
            } else {
                process_message(_msg_CONTINUE);
            }
        }
        if (state.equals(_state_PAUSING))
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_STOP), roles.get("sirens"));

        if (state.equals(_state_EPILOG))
            mqttOutbound.send("signals", MQTT.toJSON("sir1", _signal_AIRSIREN_STOP), roles.get("sirens"));
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("resume_countdown", resume_countdown)
                .put("pause_start_time", pausing_since.isPresent() ? pausing_since.get().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)) : JSONObject.NULL);
    }
}
