package de.flashheart.rlg.commander.mechanics;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

@Log4j2
public abstract class Game {
    final String name;
    final MQTTOutbound mqttOutbound;

    Game(String name, MQTTOutbound mqttOutbound) {
        this.name = name;
        this.mqttOutbound = mqttOutbound;
    }

    /**
     * when something happens we need to react on it. Implement this method to tell us, WHAT we should do.
     *
     * @param message
     */
    //todo: extend with payload for later use of rfids
    public abstract void react_to(String sender, String message);

    /**
     * call after constructor is finished
     */
    public abstract void init();

    /**
     * just before be load another game
     */
    public abstract void cleanup();

    /**
     * when the actual game should start. You run this method.
     */
    public abstract void start();

    /**
     * when the game should end NOW. Now questions asked. Simply cleanup Your stuff an unload the game. stop() does it.
     */
    public abstract void stop();

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return
     */
    public JSONObject getStatus() {
        return new JSONObject().put("name", name);
    }

    /**
     * simple envelope for a signal (former pin_scheme)
     * @param jsonObject
     * @return
     */
    public JSONObject getSignal(JSONObject jsonObject){
        return new JSONObject().put("signal", jsonObject);
    }

}
