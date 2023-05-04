package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.DelayedReactionJob;
import de.flashheart.rlg.commander.games.traits.HasDelayedReaction;
import de.flashheart.rlg.commander.games.traits.HasScoreBroadcast;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Log4j2
public class Signal extends Timed implements HasDelayedReaction, HasScoreBroadcast {
    public static final int MAX_CAPTURE_POINTS = 2;
    public static final String _msg_LOCK = "lock";
    public static final String _msg_TO_NEUTRAL = "to_neutral";
    private final List<String> capture_points;
    private final Map<String, JobKey> cpLockJobs;
    private long UNLOCK_TIME, LOCK_TIME;
    private JSONObject line_variables;
    private int blue_points, red_points;

    public Signal(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        count_respawns = false;

        log.info("\n" +
                " ____  _                   _\n" +
                "/ ___|(_) __ _ _ __   __ _| |\n" +
                "\\___ \\| |/ _` | '_ \\ / _` | |\n" +
                " ___) | | (_| | | | | (_| | |\n" +
                "|____/|_|\\__, |_| |_|\\__,_|_|\n" +
                "         |___/");

        cpLockJobs = new HashMap<>();
        cpFSMs.forEach((agent, fsm) -> cpLockJobs.put(agent, new JobKey(agent + "_lock_color", uuid.toString())));
        line_variables = new JSONObject();

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList());

        if (capture_points.size() > MAX_CAPTURE_POINTS)
            throw new ArrayIndexOutOfBoundsException("max number of capture points is " + MAX_CAPTURE_POINTS);

        UNLOCK_TIME = game_parameters.optLong("unlock_time");
        LOCK_TIME = game_parameters.optLong("lock_time");
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/signal.xml"), null);
            fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList("PROLOG", "NEUTRAL")), (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList("BLUE", "RED")), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
            });
            fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList("BLUE_LOCKED", "RED_LOCKED")), (state, obj) -> {
                if (state.equals("BLUE_LOCKED")) blue_points++;
                else red_points++;
                JobDataMap map = new JobDataMap();
                map.put("agent", agent);
                // unlock later
                create_job(cpLockJobs.get(agent), LocalDateTime.now().plusSeconds(UNLOCK_TIME), DelayedReactionJob.class, Optional.of(map));
                int index_of_agent = capture_points.indexOf(agent) + 1;
                String color = state.equals("BLUE_LOCKED") ? "BLAU" : "ROT";
                line_variables.put("line" + index_of_agent, agent + ": " + color);
                addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", state));
                broadcast_score();
                String led = state.equals("BLUE_LOCKED") ? MQTT.BLUE : MQTT.RED;
                send("acoustic", MQTT.toJSON(MQTT.BUZZER, "triple_buzz"), get_all_spawn_agents());
                send("visual", MQTT.toJSON(led, "fast"), get_all_spawn_agents());
                // 2,5 seconds sir2 at begin of lock period
                send("acoustic", MQTT.toJSON(MQTT.SIR2, "long"), roles.get("sirens"));
            });

            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void process_external_message(String sender, String source, JSONObject message) {
        if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
        if (!message.getString("button").equalsIgnoreCase("up")) return;

        if (cpFSMs.containsKey(sender) && game_fsm.getCurrentState().equals(_state_RUNNING))
            cpFSMs.get(sender).ProcessFSM(source.toLowerCase());
        else
            super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
    }

    private void cp_to_neutral(String agent) {
        deleteJob(cpLockJobs.get(agent));
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}...", "...and Your Flag", "", ""),
                agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "normal"), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "unlocked"));
        int index_of_agent = capture_points.indexOf(agent) + 1;
        line_variables.put("line" + index_of_agent, "");
        broadcast_score();
    }

    private void cp_to_blue(String agent) {
        deleteJob(cpLockJobs.get(agent));
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "normal"), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        JobDataMap map = new JobDataMap();
        map.put("agent", agent);
        // lock in 2 secs
        create_job(cpLockJobs.get(agent), LocalDateTime.now().plusSeconds(LOCK_TIME), DelayedReactionJob.class, Optional.of(map));
    }

    private void cp_to_red(String agent) {
        deleteJob(cpLockJobs.get(agent));
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "normal"), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, "double_buzz"), agent);
        JobDataMap map = new JobDataMap();
        map.put("agent", agent);
        // lock in 2 secs
        create_job(cpLockJobs.get(agent), LocalDateTime.now().plusSeconds(LOCK_TIME), DelayedReactionJob.class, Optional.of(map));
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over",
                    "Results:",
                    "Red: ${red_points}",
                    "Blue: ${blue_points}");
        }

        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return
                    MQTT.merge(
                            MQTT.page("page0",
                                    "Restzeit:  ${remaining}",
                                    "${line1}",
                                    "${line2}",
                                    "${line3}"),
                            MQTT.page("page1",
                                    "Restzeit:  ${remaining}",
                                    "",
                                    "Red: ${red_points}",
                                    "Blue: ${blue_points}")
                    );
            // we have only 3 lines, so we only use 3 Capture Points
        }

        return MQTT.page("page0", game_description);
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            broadcast_score();
            send("visual", MQTT.toJSON(MQTT.ALL, "off"), get_all_spawn_agents());
        }
    }

    @Override
    public void on_run() {
        super.on_run();
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), get_all_spawn_agents());
    }

    @Override
    public String getGameMode() {
        return "signal";
    }

    @Override
    public void on_reset() {
        super.on_reset();
        // spawn agents are used as display not for a specific team
        empty_lines();
        blue_points = 0;
        red_points = 0;
    }


    void empty_lines() {
        line_variables = MQTT.toJSON("line1", "", "line2", "", "line3", "", "line4", "");
    }

    @Override
    public void delayed_reaction(JobDataMap map) {
        String agent = map.getString("agent");
        String state = cpFSMs.get(agent).getCurrentState().toLowerCase();
        if (state.matches("red_locked|blue_locked")) {
            send("acoustic", MQTT.toJSON(MQTT.SIR3, "long"), roles.get("sirens"));
            send("visual", MQTT.toJSON(MQTT.ALL, "off"), get_all_spawn_agents());
            cpFSMs.get(map.getString("agent")).ProcessFSM(_msg_TO_NEUTRAL);
        } else
            cpFSMs.get(map.getString("agent")).ProcessFSM(_msg_LOCK);
    }

    @Override
    public void broadcast_score() {
        send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), get_all_spawn_agents());
        line_variables.put("red_points", red_points).put("blue_points", blue_points);
        send("vars", line_variables, get_all_spawn_agents());
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("red_points", red_points)
                .put("blue_points", blue_points);
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        model.addAttribute("red_points", red_points);
        model.addAttribute("blue_points", blue_points);
    }

}
