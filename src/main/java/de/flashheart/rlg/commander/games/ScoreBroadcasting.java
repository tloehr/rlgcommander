package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public abstract class ScoreBroadcasting extends Pausable{
    public ScoreBroadcasting(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
    }
}
