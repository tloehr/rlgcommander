package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.DelayedReactionJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Log4j2
public class Stronghold extends Timed {
    private final List<List<String>> defense_rings;
    private int sieged_ring;

    Stronghold(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                " ____  _                         _           _     _\n" +
                "/ ___|| |_ _ __ ___  _ __   __ _| |__   ___ | | __| |\n" +
                "\\___ \\| __| '__/ _ \\| '_ \\ / _` | '_ \\ / _ \\| |/ _` |\n" +
                " ___) | |_| | | (_) | | | | (_| | | | | (_) | | (_| |\n" +
                "|____/ \\__|_|  \\___/|_| |_|\\__, |_| |_|\\___/|_|\\__,_|\n" +
                "                           |___/");
        defense_rings = new ArrayList<>();
        game_parameters.getJSONArray("defense_rings").forEach(ring -> {
            ArrayList<String> this_ring = new ArrayList<>();
            ((JSONArray) ring).forEach(my_agent -> {
                this_ring.add(my_agent.toString());
            });
            defense_rings.add(this_ring);
        });
    }

    @Override
    public FSM create_CP_FSM(String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/stronghold.xml"), null);


            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void run_operations() {
        super.run_operations();
    }

    @Override
    public void reset_operations() {
        super.reset_operations();
        sieged_ring = 0;
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        return null;
    }

    @Override
    public void game_time_is_up() {

    }

    @Override
    protected void on_respawn_signal_received(String spawn_role, String agent) {

    }
}
