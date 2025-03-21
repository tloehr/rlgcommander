package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.TimeZone;

/**
 * <h1>Stronghold 2</h1>
 * This is a variation of the original stronghold game mode. Here we have a time limit for every
 * ring.
 *
 * <h2>game_time</h2>
 * <li>we have a time limit per ring which will add up to the total game_time</li>
 * <li>if the last wall falls early - game over</li>
 * <li>if the time runs out on a wall - game is over</li>
 * <li>if a wall is taken, the remaining time is added to the time limit for that wall - so a quick team will have a benefit</li>
 */
@Log4j2
public class Stronghold2 extends Stronghold {
    ArrayList<Long> ring_times;

    public Stronghold2(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        // game_time always equals the time limit of the first ring.
        // the game_time extends as the rings are broken
        // the game_parameters have to be set correctly by the frontend
        this.ring_times = new ArrayList<>();
        rings_total.forEach(color ->
                ring_times.add(game_parameters.getJSONObject("ring_time_limits").getLong(color))
        );
        setGameDescription(game_parameters.getString("comment"),
                String.format("Number of rings: %s", rings_total.size()),
                String.format("Ring-Times: %s", ring_times.toString()),
                " ".repeat(18) + "${wifi_signal}"
        );
    }

    @Override
    protected void activate_ring() {
        super.activate_ring();
        if (rings_to_go.size() < rings_total.size() && rings_total.size() > 1) {  // not with the first or only ring
            extend_game_time(ring_times.get(rings_taken.size()));
        }
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        JSONObject pages = super.getSpawnPages(state);
        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            pages = MQTT.merge(pages, MQTT.page("page0",
                    "Time:  ${remaining}  ${wifi_signal}",
                    "${rings_taken}/${rings_in_use} -> ${active_ring}",
                    "${extended_time_label}",
                    "${extended_time}"));
        }
        return pages;
    }

    @Override
    public String getGameMode() {
        return "stronghold2";
    }


    @Override
    public boolean hasZeus() {
        return false;
    }

}
