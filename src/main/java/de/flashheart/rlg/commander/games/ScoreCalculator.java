package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.ScoreJob;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

public abstract class ScoreCalculator extends Pausable {
    protected final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS = BigDecimal.valueOf(0.5f);
    protected final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = 10L;
    private final long REPEAT_EVERY_MS;
//    protected final long NUMBER_OF_BROADCAST_EVENTS_PER_MINUTE; // to execute something once per minute
    private long score_cycle_counter;
    private long last_job;
    protected long time_passed_since_last_calculation;

    public ScoreCalculator(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        register_job("calculate_score");
        REPEAT_EVERY_MS = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();
//        NUMBER_OF_BROADCAST_EVENTS_PER_MINUTE = BigDecimal.valueOf(60L).divide(SCORE_CALCULATION_EVERY_N_SECONDS, RoundingMode.HALF_UP).longValue();

    }

    @Override
    public void on_run() {
        super.on_run();
        create_job_with_suspension("calculate_score", simpleSchedule().withIntervalInMilliseconds(REPEAT_EVERY_MS).repeatForever(), ScoreJob.class, Optional.empty());
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_EPILOG)) {
            score_cycle(); // one last time
            broadcast_score();
        }
    }

    @Override
    public void on_reset() {
        super.on_reset();
        score_cycle_counter = 0L;
        last_job = 0L;
        time_passed_since_last_calculation = 0L;
        score_cycle();
        broadcast_score();
    }

    protected void broadcast_score() {
        send(MQTT.CMD_VARS, get_broadcast_vars(), agents.keySet());
    }

    public void score_cycle() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
        time_passed_since_last_calculation = now - last_job;
        last_job = now;
        score_cycle_counter++;
        calculate_score();
        if (score_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0) {
            broadcast_score();
        }
    }

    protected void calculate_score() {}

    protected JSONObject get_broadcast_vars() {
        return new JSONObject();
    }
}
