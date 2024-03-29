package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.github.lalyos.jfiglet.FigletFont;
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
    private long broadcast_cycle_counter;
    private final JobKey broadcastScoreJobkey;
    JSONObject vars;

    public TimedOnly(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
        vars = game_parameters.getJSONObject("display");
    }

    @Override
    public void on_reset() {
        super.on_reset();
        deleteJob(broadcastScoreJobkey);
        broadcast_cycle_counter = 0L;
        broadcast_score();
    }

    @Override
    public void on_run() {
        super.on_run();
        long repeat_every_ms = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();
        create_job(broadcastScoreJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), BroadcastScoreJob.class);
        broadcast_cycle_counter = 0L;
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
                            "Blue: ${blue_respawns}",
                            "Red: ${red_respawns}")
            );
            return epilog_pages;
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            JSONObject pages_when_game_runs = MQTT.merge(
                    MQTT.page("page0",
                            "Restzeit: ${remaining}",
                            "${line1}",
                            "${line2}",
                            "${line3}"));
            if (count_respawns) MQTT.merge(pages_when_game_runs,
                    MQTT.page("page3",

                            "Restzeit: ${remaining}",
                            StringUtils.center("Respawns", 20),
                            "Blue: ${blue_respawns}",
                            "Red: ${red_respawns}")
            );
            return pages_when_game_runs;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public void broadcast_score() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
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
    public String getGameMode() {
        return "timed_only";
    }


}
