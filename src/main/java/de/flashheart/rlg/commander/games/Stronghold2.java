package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
public class Stronghold2 extends Stronghold {
    private final boolean classic_game_time_mode; // meaning at least one ring had a 0 time limit.
    private Optional<LocalDateTime> estimated_end_time;
    //private final long stronghold_max_game_time;
    ArrayList<Integer> ring_times;

    public Stronghold2(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.estimated_end_time = Optional.empty();
        this.ring_times = new ArrayList<>();
        // at least one limit is 0 - CLASSIC MODE
        classic_game_time_mode = ring_times.stream().anyMatch(integer -> integer == 0);
        //stronghold_max_game_time = ring_times.stream().mapToLong(Integer::longValue).sum();
        rings_in_use.forEach(color ->
                ring_times.add(game_parameters.getJSONObject("ring_time_limit").getInt(color))
        );

    }

    @Override
    protected long getRemaining() {
        if (game_fsm_get_current_state().equals(_state_EPILOG)) return super.getRemaining();
        return estimated_end_time
                .map(localDateTime -> LocalDateTime.now().until(localDateTime, ChronoUnit.SECONDS) + 1)
                .orElseGet(super::getRemaining);
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
