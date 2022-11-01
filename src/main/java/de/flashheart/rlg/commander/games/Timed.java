package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.GameTimeIsUpJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.TimeZone;

/**
 * an abstract superclass for handling any game mode that has an (estimated or fixed) end_time We are talking about
 * time, NOT about END-SCORES or RUNNING OUT OF RESOURCES.
 */
@Log4j2
public abstract class Timed extends WithRespawns {
    final String _msg_GAME_TIME_IS_UP = "game_time_is_up";
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
    ZonedDateTime ldt_game_time;

    final JobKey game_timer_jobkey;

    Timed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.game_time = game_parameters.getInt("game_time");
        ldt_game_time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        game_timer_jobkey = new JobKey("gametime", uuid.toString());
        start_time = null;
        end_time = null;
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
//        if (message.equals(_msg_PAUSE)) {
//            deleteJob(game_timer_jobkey);
//        }
        if (message.equals(_msg_RESUME)) {
            // shift the start and end_time by the number of seconds the pause lasted
            long pause_length_in_seconds = pausing_since.get().until(LocalDateTime.now(), ChronoUnit.SECONDS);
            start_time = start_time.plusSeconds(pause_length_in_seconds);
            end_time = start_time.plusSeconds(game_time);
        }
    }

    @Override
    public void run_operations() {
        super.run_operations();
        start_time = LocalDateTime.now();
        end_time = start_time.plusSeconds(game_time);
        create_resumable_job(game_timer_jobkey, end_time, GameTimeIsUpJob.class, Optional.empty());
    }

    @Override
    public void reset_operations() {
        super.reset_operations();
        start_time = null;
        end_time = null;
        deleteJob(game_timer_jobkey);
    }

    @Override
    public void game_over_operations() {
        super.game_over_operations();
        deleteJob(game_timer_jobkey);
    }

    protected long getRemaining() {
        if (start_time == null) return game_time;
        long elapsed_time = start_time.until(pausing_since.orElse(LocalDateTime.now()), ChronoUnit.SECONDS);
        return Math.max(0L, game_time - elapsed_time);
//        } else {
//            return end_time != null ? Math.max(0l, LocalDateTime.now().until(end_time, ChronoUnit.SECONDS) + 1) : 0l;
//        }
    }

    public abstract void game_time_is_up();

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("match_length", game_time)
                .put("start_time", start_time != null ? start_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("end_time", end_time != null ? end_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("remaining", getRemaining());
    }


}
