package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Helper game mode for Patrick's mag_fed tournament
 */
@Log4j2
public class MagSpeed extends TimedOnly {
    private final String winning_token_uid;

    public MagSpeed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.count_respawns = false;
        this.winning_token_uid = game_parameters.getString("winning_token_uid");
    }


    @Override
    protected void on_spawn_rfid_scanned( String agent, String uid) {
        super.on_spawn_rfid_scanned(agent, uid);

        roles.get(agent);
        // if (uid.equals(winning_token_uid)) game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    public String getGameMode() {
        return "mag_speed";
    }
}
