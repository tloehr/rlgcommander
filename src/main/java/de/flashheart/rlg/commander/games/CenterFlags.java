package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Log4j2
public class CenterFlags extends Timed {

    private final List<Object> capture_points;
    private final Map<String, FSM> cpFSMs;

    CenterFlags(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("       ______           __            ________\n" +
                "  / ____/__  ____  / /____  _____/ ____/ /___ _____ ______\n" +
                " / /   / _ \\/ __ \\/ __/ _ \\/ ___/ /_  / / __ `/ __ `/ ___/\n" +
                "/ /___/  __/ / / / /_/  __/ /  / __/ / / /_/ / /_/ (__  )\n" +
                "\\____/\\___/_/ /_/\\__/\\___/_/  /_/   /_/\\__,_/\\__, /____/\n" +
                "                                            /____/");
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"), "",
                String.format("Gametime: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))), "");

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();

        cpFSMs = new HashMap<>();
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
    }

    private FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest_cp.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                broadcast_score();
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED"))), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
                broadcast_score();
                process_message(_msg_IN_GAME_EVENT_OCCURRED);
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "I will be a", "Capture Point"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_wht", "normal"), agent);
    }

    private void cp_to_blue(String agent) {
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_blu", "normal", "buzzer", "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "blue"));
    }

    private void cp_to_red(String agent) {
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_red", "normal", "buzzer", "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "red"));
    }

    private void broadcast_score() {

    }

    @Override
    protected JSONObject getPages() {
        return MQTT.page("page0", game_description);
    }

    @Override
    public void game_time_is_up() {

    }

    @Override
    protected void respawn(String role, String agent) {
        // not managed
    }
}
