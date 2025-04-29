package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.GameTimeIsUpJob;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
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
     * the length of the match in seconds. Due to the nature of certain game_modes (like farcry or stronghold2)
     * this value is rather an assumption than a constant value
     * The Timed class will call the game_time_is_up() method at the end of this period,
     * UNLESS a subclass calls the extend_game_time method first.
     */
    private final long game_time;
    /**
     * start_time is the timestamp when the game started. it will shift forward when the game was paused and is
     * resumed.
     */
    private LocalDateTime start_time;
    /**
     * estimated_end_time is the timestamp when the game will possibly end. can change due to game situations like in
     * Farcry (last moment flag activations or quick attacker)
     */
    protected LocalDateTime end_time;
    protected ZonedDateTime ldt_game_time;
    private long last_extended_time;

    Timed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        this.game_time = game_parameters.getLong("game_time");
        ldt_game_time = seconds_to_zdt(game_time);
        last_extended_time = 0L;
        start_time = null;
        end_time = null;
        register_job("game_timer");
        setGameDescription(game_parameters.getString("comment"),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))), "",
                " ".repeat(18) + "${wifi_signal}");
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RESUME)) {
            // shift the start and end_time by the number of seconds the pause lasted
            long pause_length_in_seconds = pausing_since.get().until(LocalDateTime.now(), ChronoUnit.SECONDS);
            start_time = start_time.plusSeconds(pause_length_in_seconds);
            end_time = start_time.plusSeconds(game_time);
        }
    }

    @Override
    public void on_run() {
        super.on_run();
        start_time = LocalDateTime.now();
        end_time = start_time.plusSeconds(game_time);
        create_job_with_reschedule("game_timer", end_time, GameTimeIsUpJob.class, Optional.empty());
    }

    protected void extend_game_time(long seconds) {
        if (end_time == null) return;
        last_extended_time = seconds;
        reschedule_job("game_timer", seconds);
        add_in_game_event(new JSONObject().put("item", "extended_timed").put("time", seconds_to_zdt(last_extended_time).format(DateTimeFormatter.ofPattern("mm:ss"))));
        end_time = end_time.plusSeconds(seconds);
    }

    @Override
    String get_in_game_event_description(JSONObject event) {
        String result = super.get_in_game_event_description(event);
        if (result.isEmpty()) {
            String type = event.getString("type");
            if (type.equalsIgnoreCase("in_game_state_change")) {
                if (event.getString("item").equals("extended_timed")) {
                    return i18n("active.timed.extended_game_time_by") + " " + event.getString("time");
                }
            }
        }
        return result;
    }

    @Override
    public void on_reset() {
        super.on_reset();
        last_extended_time = 0L;
        start_time = null;
        end_time = null;
    }

    @Override
    protected void broadcast_score() {
        super.broadcast_score();
        send(MQTT.CMD_TIMERS, MQTT.toJSON("remaining", Long.toString(getRemaining())), get_active_spawn_agents());
    }

    protected long getRemaining() {
        if (start_time == null) return game_time;
        if (game_fsm_get_current_state().equals(_state_EPILOG)) return 0L;
        return LocalDateTime.now().until(end_time, ChronoUnit.SECONDS) + 1;
    }

    public void game_time_is_up() {
        log.info("Game time is up");
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    protected JSONObject get_broadcast_vars() {
        JSONObject vars = super.get_broadcast_vars();
        vars.put("extended_time_label", last_extended_time > 0 ? "Game Time extended by" : "");
        vars.put("extended_time", last_extended_time > 0 ? seconds_to_zdt(last_extended_time).format(DateTimeFormatter.ofPattern("mm:ss")) : "");
        return vars;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        model.addAttribute("game_time", JavaTimeConverter.format(Instant.ofEpochSecond(game_time)));
        model.addAttribute("remaining", JavaTimeConverter.format(Instant.ofEpochSecond(getRemaining())));
    }

    @Override
    public JSONObject get_full_state() {
        JSONObject json = super.get_full_state();
        json.getJSONObject("played")
                .put("game_time", game_time)
                .put("start_time", start_time != null ? start_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("end_time", end_time != null ? end_time.format(DateTimeFormatter.ISO_DATE_TIME) : "don't know yet")
                .put("remaining", getRemaining())
                .put("extended_time", last_extended_time == 0 ? "" : seconds_to_zdt(last_extended_time).format(DateTimeFormatter.ofPattern("mm:ss")))
                .put("extended_time_label", last_extended_time == 0 ? "" : "extended time by:");

        return json;
    }

    protected ZonedDateTime seconds_to_zdt(long seconds) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds), TimeZone.getTimeZone("UTC").toZoneId());
    }


}
