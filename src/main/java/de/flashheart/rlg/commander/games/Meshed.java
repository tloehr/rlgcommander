package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

@Log4j2
public class Meshed extends WithRespawns implements HasScoreBroadcast {
    public Meshed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {

            // don't care about 0 and 1
            String[] fsms = new String[]{"", "", "conquest", "meshed3", "meshed4"}; // file names of cp FSMs definitions - see resources/games
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/" + fsms[team_registry.size()] + ".xml"), null);

            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                broadcast_score();
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED", "YELLOW", "GREEN"))), (state, obj) -> {
                switch_cp(agent, state.toLowerCase());
                broadcast_score();
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }

    }

    @Override
    protected void on_respawn_signal_received(String spawn, String agent) {
        super.on_respawn_signal_received(spawn, agent);
        log.debug(team_registry.toString());
    }


    private void cp_to_neutral(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
    }

    private void switch_cp(String agent, String state) {
        String led_color = switch (state) {
            case "red" -> MQTT.RED;
            case "blue" -> MQTT.BLUE;
            case "yellow" -> MQTT.YELLOW;
            case "green" -> MQTT.GREEN;
            default -> "error";
        };

        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", led_color, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject()
                .put("item", "capture_point")
                .put("agent", agent)
                .put("state", state)
        );
    }

    @Override
    public String getGameMode() {
        return null;
    }

    @Override
    public void broadcast_score() {

    }
}
