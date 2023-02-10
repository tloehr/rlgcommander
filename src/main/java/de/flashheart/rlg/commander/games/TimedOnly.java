package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.BroadcastScoreJob;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Log4j2
public class TimedOnly extends Timed implements HasScoreBroadcast {
    private final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final boolean count_respawns;
    private int blue_respawns, red_respawns;
    private final JobKey broadcastScoreJobkey;
    private long last_job_broadcast;
    JSONObject vars;

    TimedOnly(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        count_respawns = game_parameters.optBoolean("count_respawns");
        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
        vars = game_parameters.getJSONObject("display");
    }

    @Override
    public void reset_operations() {
        super.reset_operations();
        deleteJob(broadcastScoreJobkey);
        broadcast_cycle_counter = 0l;
        last_job_broadcast = 0l;
        blue_respawns = 0;
        red_respawns = 0;
        broadcast_score();
    }

    @Override
    public void run_operations() {
        super.run_operations();
        long repeat_every_ms = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();
        create_job(broadcastScoreJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), BroadcastScoreJob.class);
        broadcast_cycle_counter = 0;
    }


    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {
        if (!count_respawns) return;

        if (spawn_role.equals(RED_SPAWN)) {
            red_respawns++;
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "single_buzz"), agent);
            send("visual", MQTT.toJSON(MQTT.WHITE, "single_buzz"), agent);
            addEvent(new JSONObject().put("item", "respawn").put("agent", agent).put("team", "red").put("value", red_respawns));
        }
        if (spawn_role.equals(BLUE_SPAWN)) {
            blue_respawns++;
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "single_buzz"), agent);
            send("visual", MQTT.toJSON(MQTT.WHITE, "single_buzz"), agent);
            addEvent(new JSONObject().put("item", "respawn").put("agent", agent).put("team", "blue").put("value", blue_respawns));
        }
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            JSONObject epilog_pages = MQTT.page("page0",
                    "Game Over",
                    "${line1}",
                    "${line2}",
                    "${line3}");
            if (count_respawns) MQTT.merge(epilog_pages,
                    MQTT.page("page1",
                            "Game Over",
                            "Respawns",
                            "Blau: ${blue_respawns}",
                            "Rot: ${red_respawns}")
            );
            return epilog_pages;
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            JSONObject pages_when_game_runs = MQTT.merge(
                    MQTT.page("page0",
                            "Restzeit: ${remaining}",
                            "${line1}",
                            "${line2}",
                            "${line3}")
            );
            if (count_respawns) MQTT.merge(pages_when_game_runs,
                    MQTT.page("page3",
                            "Restzeit: ${remaining}",
                            StringUtils.center("Respawns", 20),
                            "Blau: ${blue_respawns}",
                            "Rot: ${red_respawns}")
            );
            return pages_when_game_runs;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public void broadcast_score() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
        last_job_broadcast = now;

        broadcast_cycle_counter++;
        if (!game_fsm.getCurrentState().equals(_state_RUNNING) || broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0) {
            send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), get_active_spawn_agents());
            send("vars", vars, agents.keySet());
        }

    }

    @Override
    public FSM create_CP_FSM(String agent) {
        // we don't need CPs in TimedOnly games.
        return null;
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("red_respawns", red_respawns)
                .put("blue_respawns", blue_respawns);
    }

}
