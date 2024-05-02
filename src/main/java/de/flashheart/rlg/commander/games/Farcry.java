package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.Misc1Job;
import de.flashheart.rlg.commander.games.jobs.RespawnTimerJob;
import de.flashheart.rlg.commander.games.traits.HasFlagTimer;
import de.flashheart.rlg.commander.games.jobs.FlagTimerJob;
import de.flashheart.rlg.commander.games.traits.HasMisc1Job;
import de.flashheart.rlg.commander.games.traits.HasTimedRespawn;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;


/**
 * Implementation for the FarCry 1 (2004) Assault Game Mode.
 */
@Log4j2
public class Farcry extends Timed implements HasFlagTimer, HasTimedRespawn {
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_DEFENDED = "DEFENDED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_OVERTIME = "OVERTIME";
    private static final int MAX_CAPTURE_POINTS = 6;

    private final int bomb_timer;

    private final JobKey bombTimerJobkey, respawnTimerJobkey;
    private final List<Object> capture_points;
    private final List<Object> sirs;
    private LocalDateTime estimated_end_time;
    private final Map map_of_agents_and_sirens;
    // which CP to take next
    private int active_capture_point;
    boolean overtime;
    private boolean ran_once_already;

    public Farcry(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ArrayIndexOutOfBoundsException, ParserConfigurationException, IOException, SAXException {
        super(game_parameters, scheduler, mqttOutbound);
        count_respawns = false;
        estimated_end_time = null;

        this.bombTimerJobkey = new JobKey("bomb_timer", uuid.toString());
        this.respawnTimerJobkey = new JobKey("timed_respawn", uuid.toString());
        this.bomb_timer = game_parameters.getInt("bomb_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawn = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_timer), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Spawntime: %s  ${wifi_signal}", ldtRespawn.format(DateTimeFormatter.ofPattern("mm:ss"))));

        // additional parsing agents and sirens
        map_of_agents_and_sirens = new HashMap<>();
        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();
        ran_once_already = false;

        if (capture_points.size() > MAX_CAPTURE_POINTS)
            throw new ArrayIndexOutOfBoundsException("max number of capture points is " + MAX_CAPTURE_POINTS);

        sirs = game_parameters.getJSONObject("agents").getJSONArray("capture_sirens").toList();
        check_segment_sizes();
        for (int i = 0; i < capture_points.size(); i++) {
            map_of_agents_and_sirens.put(capture_points.get(i), sirs.get(i));
            roles.put("sirens", sirs.get(i).toString()); // add these sirens to the siren list
        }
    }

    /**
     * checks whether the spawn segment definitions are valid
     *
     * @throws JSONException
     */
    void check_segment_sizes() throws JSONException {

        // all segments must have the same size

//        if (team_registry.size() != 2) throw new JSONException("we need exactly 2 teams");
//        if (!spawn_segments.containsRow("red_spawn")) throw new JSONException("red_spawn missing");
//        if (!spawn_segments.containsRow("blue_spawn")) throw new JSONException("blue_spawn missing");
        assert_two_teams_red_and_blue();
        // a map of teams and the number of segments per team
        ConcurrentMap<Object, Long> map_teams_to_segment_length = spawn_segments.stream().collect(Collectors.groupingByConcurrent(o -> o.getValue0(), Collectors.counting()));

        if (map_teams_to_segment_length.values().stream().distinct().count() > 1)
            throw new JSONException("all teams must have the same number of spawn segments");

//        if (spawn_segments.rowMap().values().stream().map(integerPairMap -> integerPairMap.keySet().size()).distinct().count() > 1)
//            throw new JSONException("all teams must have the same number of spawn segments");

        long num_of_spawn_segments = new ArrayList<Long>(map_teams_to_segment_length.values()).get(0);

        if (!(num_of_spawn_segments == capture_points.size() && num_of_spawn_segments == sirs.size()))
            throw new JSONException("number of segments mismatch. number of CPs, sirens and spawn_segments must match");
    }


    @Override
    public String getGameMode() {
        return "farcry";
    }

    @Override
    public void on_reset() {
        super.on_reset();
        estimated_end_time = null;
        deleteJob(bombTimerJobkey);
        delete_timed_respawn();
        active_capture_point = 0;
        overtime = false;
        ran_once_already = false;
        send("vars", MQTT.toJSON("overtime", ""), get_all_spawn_agents());
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.LED_ALL, "off"), agent);
    }

    private void fused(String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("bombid", agent);
        estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
        create_resumable_job(bombTimerJobkey, estimated_end_time, FlagTimerJob.class, Optional.of(jdm));
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "fused"));
        play("voice2", AGENT_EVENT_PATH, "selfdestruct", get_active_spawn_agents());
        send("acoustic", Tools.getProgressTickingScheme(MQTT.SIR2, bomb_timer * 1000), map_of_agents_and_sirens.get(agent).toString());
        send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send("visual", new JSONObject().put("progress", "remaining"), agent);
        send("vars", MQTT.toJSON("fused", "hot", "next_cp", get_next_cp()), get_all_spawn_agents());
    }

    private void defused(String agent) {
        estimated_end_time = end_time;
        deleteJob(bombTimerJobkey);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defused"));
        send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send("visual", MQTT.toJSON(MQTT.LED_ALL, "off", MQTT.BLUE, MQTT.NORMAL), agent);
        send("vars", MQTT.toJSON("fused", "cold", "next_cp", get_next_cp()), get_all_spawn_agents());
    }

    private void defended(String agent) {
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defended"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, "off", MQTT.BLUE, MQTT.VERY_FAST), agent);
        send("vars", MQTT.toJSON("overtime", overtime ? "SUDDEN DEATH" : ""), get_all_spawn_agents());
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    private void taken(String agent) {
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "taken"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.LONG), agent);
        active_capture_point++;
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, "off"), map_of_agents_and_sirens.get(agent).toString());

        // activate next CP or end the game when no CPs left
        boolean all_cps_taken = active_capture_point == capture_points.size();
        if (overtime || all_cps_taken) {
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, "off", MQTT.RED, MQTT.VERY_FAST), agent);
            game_fsm.ProcessFSM(_msg_GAME_OVER);
        } else {
            send(MQTT.CMD_VISUAL, new JSONObject(MQTT.LED_ALL, "off", MQTT.RED, MQTT.FLAG_TAKEN), agent);
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR4, MQTT.LONG), map_of_agents_and_sirens.get(agent).toString());
            next_spawn_segment();
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
        }
    }

    @Override
    protected long getRemaining() {
        if (get_current_state().equals(_state_EPILOG)) return super.getRemaining();
        return estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : super.getRemaining();
    }

    private void prolog(String agent) {
        send(MQTT.CMD_VISUAL, show_number_as_leds(capture_points.indexOf(agent) + 1, MQTT.FAST), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, "off"), agent);
    }

    /**
     * @param num 1..5
     * @return signals for led stripes. out of bounds means all off
     */
    JSONObject show_number_as_leds(int num, final String signal) {
        if (num < 1 || num > 5) return MQTT.toJSON(MQTT.LED_ALL, "off");
        JSONObject result = new JSONObject();
        ALL_LEDS.forEach(led -> result.put(led, ALL_LEDS.indexOf(led) < num ? signal : "off"));
        return result;
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/farcry.xml"), null);
            fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> prolog(agent));
            fsm.setStatesAfterTransition(_state_STANDBY, (state, obj) -> standby(agent));
            fsm.setStatesAfterTransition(_state_DEFUSED, (state, obj) -> defused(agent));
            fsm.setStatesAfterTransition(_state_FUSED, (state, obj) -> fused(agent));
            fsm.setStatesAfterTransition(_state_DEFENDED, (state, obj) -> defended(agent));
            fsm.setStatesAfterTransition(_state_TAKEN, (state, obj) -> taken(agent));
            fsm.setStatesAfterTransition(_state_OVERTIME, (state, obj) -> overtime());
            // DEFUSED => FUSED
            fsm.setAction(_state_FUSED, _msg_BUTTON_01, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // the siren is activated on the message NOT on the state, so it won't be activated when the game starts only when the flag has been defused.
                    send("acoustic", MQTT.toJSON(MQTT.SIR2, "off"), map_of_agents_and_sirens.get(agent).toString());
                    send("acoustic", MQTT.toJSON(MQTT.SIR3, MQTT.LONG), map_of_agents_and_sirens.get(agent).toString());
                    play("voice3", AGENT_EVENT_PATH, "shutdown", get_active_spawn_agents());
                    return true;
                }
            });
            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    send("vars", MQTT.toJSON("active_cp", agent), get_all_spawn_agents());
                    add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
                    if (active_capture_point > 0)
                        send("acoustic", MQTT.toJSON(MQTT.SIR2, MQTT.LONG), map_of_agents_and_sirens.get(agent).toString());
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void on_run() {
        super.on_run();
        // create timed respawn if necessary
        if (respawn_timer > 0) {
            create_resumable_job(respawnTimerJobkey,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(respawn_timer).repeatForever(),
                    RespawnTimerJob.class, Optional.empty());
        }
        estimated_end_time = end_time;
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        delete_timed_respawn();
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_RUNNING)) {
            // activate first cp
            log.debug("AT_RUNNING");
            if (!ran_once_already) {
                ran_once_already = true;
                cpFSMs.get(capture_points.get(0)).ProcessFSM(_msg_ACTIVATE);
            }
        }
    }

    private void overtime() {
        add_in_game_event(new JSONObject().put("item", "overtime"));
        send("vars", MQTT.toJSON("overtime", "overtime"), get_all_spawn_agents());
        play("voice1", AGENT_EVENT_PATH, "overtime", get_active_spawn_agents());
        overtime = true;
    }

    @Override
    public void process_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)
                && cpFSMs.get(agent_id).getCurrentState().
                matches(_state_FUSED + "|" + _state_DEFUSED + "|" + _state_OVERTIME)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else
            super.process_external_message(agent_id, source, message);
    }

    @Override
    public void on_time_to_respawn(JobDataMap map) {
//        create_job(misc1JobKey, LocalDateTime.now().plusSeconds(respawn_timer), Misc1Job.class, Optional.empty());
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.LONG), get_active_spawn_agents());
        send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), get_active_spawn_agents());
    }


    @Override
    public JSONObject getState() {
        return super.getState()
                .put("capture_points_taken", active_capture_point)
                .put("max_capture_points", cpFSMs.size());
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        String current_active_agent = capture_points.get(Math.min(
                active_capture_point,
                cpFSMs.size() - 1
        )).toString();
        model.addAttribute("capture_points_taken", active_capture_point);
        model.addAttribute("max_capture_points", cpFSMs.size());
        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            model.addAttribute("current_game_situation", "GAME OVER");
        } else {
            model.addAttribute("current_game_situation", String.format("%s: %s, %s", current_active_agent, cpFSMs.get(current_active_agent).getCurrentState(), get_next_cp()));
        }
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over", "Capture Points taken: ", active_capture_point + " of " + capture_points.size(), "${overtime}");
        }
        if (state.equals(_state_RUNNING))
            return MQTT.page("page0", "Remaining: ${remaining} ${overtime}", "${active_cp}->${fused}", "", respawn_timer > 0 ? "Respawn in: ${respawn}" : "");

        return MQTT.page("page0", game_description);
    }

    protected void delete_timed_respawn() {
        deleteJob(respawnTimerJobkey);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, "off"), get_active_spawn_agents());
        send("timers", MQTT.toJSON("respawn", "0"), get_active_spawn_agents());
    }

    private String get_next_cp() {
        if (capture_points.size() == 1) return "This is the only one";
        return active_capture_point == capture_points.size() - 1 ? "This is the LAST" : "Next: " + capture_points.get(active_capture_point + 1);
    }

    @Override
    public void flag_time_is_up(String bombid) {
        log.info("Bomb has exploded");
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_FLAG_TIME_IS_UP));
    }

    @Override
    public void game_time_is_up() {
        log.info("Game time is up");
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_TIME_IS_UP));
    }

    @Override
    String get_in_game_event_description(JSONObject event) {
        String result = super.get_in_game_event_description(event);
        if (result.isEmpty()) {
            result = "error";
            String type = event.getString("type");

            if (type.equalsIgnoreCase("in_game_state_change")) {
                if (event.getString("item").equals("overtime")) {
                    result = "overtime";
                }
            }
        }

        return result;
    }
}
