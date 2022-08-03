package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.BroadcastScoreJob;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;


@Log4j2
public class CenterFlags extends Timed implements HasScoreBroadcast {

    private final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final List<Object> capture_points;
    private final Map<String, FSM> cpFSMs;
    private final HashMap<String, Long> scores;
    private final JobKey broadcastScoreJobkey;
    private long last_job_broadcast;
    private long score_blue, score_red;

    public CenterFlags(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.info("\n" +
                "   ______           __            ________\n" +
                "  / ____/__  ____  / /____  _____/ ____/ /___ _____ ______\n" +
                " / /   / _ \\/ __ \\/ __/ _ \\/ ___/ /_  / / __ `/ __ `/ ___/\n" +
                "/ /___/  __/ / / / /_/  __/ /  / __/ / / /_/ / /_/ (__  )\n" +
                "\\____/\\___/_/ /_/\\__/\\___/_/  /_/   /_/\\__,_/\\__, /____/\n" +
                "                                            /____/");
        ZonedDateTime ldtTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"), "",
                String.format("Gametime: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))), "");

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();

        scores = new HashMap<>();
        cpFSMs = new HashMap<>();
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));

        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
        // just for score
        add_spawn_for("red_spawn", MQTT.RED, "RedFor");
        add_spawn_for("blue_spawn", MQTT.BLUE, "BlueFor");

    }

    private String get_agent_key(String agent, String state) {
        return String.format("score_%s_%s", state.toLowerCase(Locale.ROOT), agent);
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
                        "I am ${agentname}", "Blue: ${score_blue}", "Red: ${score_red}", "I am a Flag"),
                agent);
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
    }

    private void cp_to_blue(String agent) {
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "normal"), agent);
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "blue"));
    }

    private void cp_to_red(String agent) {
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "normal"), agent);
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "red"));
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RESET)) {
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
        }
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));

            // setup and start bleeding job
            long repeat_every_ms = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000l)).longValue();
            create_job(broadcastScoreJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), BroadcastScoreJob.class);
            broadcast_cycle_counter = 0;
        }
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_PROLOG)) {
            deleteJob(broadcastScoreJobkey);
            broadcast_cycle_counter = 0l;
            last_job_broadcast = 0l;
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
            // reset scores
            score_blue = 0l;
            score_red = 0l;
            scores.clear();
            cpFSMs.keySet().forEach(agent -> {
                scores.put(get_agent_key(agent, "red"), 0l);
                scores.put(get_agent_key(agent, "blue"), 0l);
            });
        }

        if (state.equals(_state_EPILOG)) {
            deleteJob(broadcastScoreJobkey); // this cycle has no use anymore
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM("game_over"));
        }
    }

    @Override
    public void broadcast_score() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
        final long time_to_add = now - last_job_broadcast;
        last_job_broadcast = now;
        cpFSMs.entrySet().forEach(stringFSMEntry -> add_score_for(stringFSMEntry.getKey(), stringFSMEntry.getValue().getCurrentState(), time_to_add));

        broadcast_cycle_counter++;
        if (broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0) {
            mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), roles.get("spawns"));
            //todo: hier ghets weiter

            JSONObject vars = new JSONObject()
                    .put("score_blue", ZonedDateTime.ofInstant(Instant.ofEpochMilli(score_blue), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("mm:ss")))
                    .put("score_red", ZonedDateTime.ofInstant(Instant.ofEpochMilli(score_red), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("mm:ss")));
            for (String key : scores.keySet()) {
                vars = MQTT.merge(vars, MQTT.toJSON(key, ZonedDateTime.ofInstant(Instant.ofEpochMilli(scores.get(key)), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("mm:ss"))));
            }
            mqttOutbound.send("vars", vars, roles.get("spawns"));
        }
    }

    private void add_score_for(String agent, String current_state, long time_to_add) {
        if (!current_state.equals("BLUE") && !current_state.equals("RED")) return;
        if (current_state.equals("BLUE")) score_blue += time_to_add;
        if (current_state.equals("RED")) score_red += time_to_add;
        final long new_score = scores.get(get_agent_key(agent, current_state)) + time_to_add;
        scores.put(get_agent_key(agent, current_state), new_score);
    }

    @Override
    public void process_message(String origin, String source, JSONObject details) {
        if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
        if (!details.getString("button").equalsIgnoreCase("up")) return;
        if (!hasRole(origin, "capture_points")) return;

        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            cpFSMs.get(origin).ProcessFSM(source.toLowerCase());
        else
            super.process_message(origin, source, details);
    }

    @Override
    protected JSONObject getPages() {
        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over", "", "", "");
        }
        if (game_fsm.getCurrentState().equals(_state_RUNNING)) {
            // one page per Agent
            JSONObject pages = MQTT.page("page0",
                    "Restzeit:  ${remaining}",
                    "---------",
                    "Blau: ${score_blue}",
                    "Rot: ${score_red}");
            for (String agent : cpFSMs.keySet().stream().sorted().collect(Collectors.toList())) {
                pages = MQTT.merge(MQTT.page("page_" + agent,
                        "  " + agent + "  ",
                        "Farbe: ${" + agent + "_state}",
                        "Blau: ${score_blue_" + agent + "}",
                        "Rot: ${score_red_" + agent + "}"));
            }
            return pages;
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public void game_time_is_up() {
        log.info("Game time is up");
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_OVER));
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    protected void respawn(String role, String agent) {
        // not managed
    }
}
