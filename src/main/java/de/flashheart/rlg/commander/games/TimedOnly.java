package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@Log4j2
public class TimedOnly extends Timed {

    public TimedOnly(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
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
    protected JSONObject get_broadcast_vars() {
        return MQTT.merge(super.get_broadcast_vars(), getGame_parameters().getJSONObject("display"));
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
