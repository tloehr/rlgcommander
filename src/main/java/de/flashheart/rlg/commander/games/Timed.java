package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.GameTimeIsUpJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * an abstract superclass for handling any game mode that has an (estimated or fixed) end_time We are talking about
 * time, NOT about END-SCORES or RUNNING OUT OF RESOURCES.
 */
@Log4j2
public abstract class Timed extends WithRespawns {

    /**
     * the length of the match in seconds. Due to the nature of certain gamemodes (like farcry) this value is rather a
     * proposal than a constant value
     */
    final int game_time;
    /**
     * start_time is the timestamp when the game started. it will shift forward when the game was paused and is
     * resumed.
     */
    LocalDateTime start_time;
    /**
     * estimated_end_time is the timestamp when the game will possibly end. can change due to game situations like in
     * Farcry (last moment flag activations or quick attacker)
     */
    LocalDateTime end_time;

    final JobKey game_timer_jobkey;

    Timed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.game_time = game_parameters.getInt("game_time");
        game_timer_jobkey = new JobKey("gametime", uuid.toString());
        start_time = null;
        end_time = null;
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
//        if (message.equals("pause")){
//            deleteJob(timer_job_key);
//        }
//        if (message.equals("resume")){
//            // shift the start and end_time by the number of seconds the pause lasted
//            long pause_length_in_seconds = pausing_since.get().until(LocalDateTime.now(), ChronoUnit.SECONDS);
//            start_time = start_time.plusSeconds(pause_length_in_seconds);
//            estimated_end_time = start_time.plusSeconds(match_length);
//            regular_end_time = estimated_end_time;
//            create_job(overtimeJobKey, regular_end_time, OvertimeJob.class);
//            monitorRemainingTime();
//        }

        if (message.equals(_msg_RESET)) {
            start_time = null;
            end_time = null;
        }

        if (message.equals(_msg_RUN)) {
            start_time = LocalDateTime.now();
            end_time = start_time.plusSeconds(game_time);
            create_job(game_timer_job_key, end_time, GameTimeIsUpJob.class);
        }
    }


    @Override
    public void on_cleanup() {
        super.on_cleanup();
        start_time = null;
    }

    protected long getRemaining() {
        return end_time != null ? LocalDateTime.now().until(end_time, ChronoUnit.SECONDS) + 1 : 0l;
    }

    public abstract void time_is_up();
//
//    void monitorRemainingTime() {
//        log.debug("estimated_end_time = {}, regular_end_time = {}", estimated_end_time.format(DateTimeFormatter.ISO_TIME), regular_end_time.format(DateTimeFormatter.ISO_TIME));
//        create_job(gametimeJobKey, estimated_end_time, GameTimeIsUpJob.class);
//        remaining = estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : 0l;
//        log.debug("remaining seconds: {}", remaining);
//        mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(remaining)), agents.keySet()); // sending to everyone
//    }


    @Override
    public JSONObject getState() {
        return super.getState()
                .put("match_length", game_time)
                .put("start_time", start_time != null ? start_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("end_time", end_time != null ? end_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("remaining", getRemaining());
    }


}
