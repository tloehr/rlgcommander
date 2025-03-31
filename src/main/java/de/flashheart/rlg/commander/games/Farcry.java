package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.RespawnTimerJob;
import de.flashheart.rlg.commander.games.traits.HasFlagTimer;
import de.flashheart.rlg.commander.games.jobs.FlagTimerJob;
import de.flashheart.rlg.commander.games.traits.HasTimedRespawn;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.context.MessageSource;
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
    private final List<Object> capture_points;
    private final List<Object> sirs;
    private LocalDateTime estimated_end_time;
    private final Map map_of_agents_assigned_to_sirens;
    // which CP to take next
    private int active_capture_point;
    boolean overtime;
    private boolean ran_once_already;

    public Farcry(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws GameSetupException, ArrayIndexOutOfBoundsException, ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);

        estimated_end_time = null;

        this.bomb_timer = game_parameters.getInt("bomb_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawn = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_timer), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Spawntime: %s  ${wifi_signal}", ldtRespawn.format(DateTimeFormatter.ofPattern("mm:ss"))));

        // additional parsing agents and sirens
        map_of_agents_assigned_to_sirens = new HashMap<>();
        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();
        ran_once_already = false;

        if (capture_points.size() > MAX_CAPTURE_POINTS)
            throw new GameSetupException("max number of capture points is " + MAX_CAPTURE_POINTS);

        register_job("bomb_timer");
        register_job("timed_respawn");

        sirs = game_parameters.getJSONObject("agents").getJSONArray("capture_sirens").toList();
        check_segment_sizes();
        for (int i = 0; i < capture_points.size(); i++) {
            map_of_agents_assigned_to_sirens.put(capture_points.get(i), sirs.get(i));
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
            throw new GameSetupException("all teams must have the same number of spawn segments");

        long num_of_spawn_segments = new ArrayList<Long>(map_teams_to_segment_length.values()).get(0);

        if (!(num_of_spawn_segments == capture_points.size() && num_of_spawn_segments == sirs.size()))
            throw new GameSetupException("number of segments mismatch. number of CPs, sirens and spawn_segments must match");
    }
    
    @Override
    public String getGameMode() {
        return "farcry";
    }

    @Override
    public void on_reset() {
        super.on_reset();
        estimated_end_time = null;
        deleteJob("bomb_timer");
        delete_timed_respawn();
        active_capture_point = 0;
        overtime = false;
        ran_once_already = false;
        send(MQTT.CMD_VARS, MQTT.toJSON("overtime", ""), get_all_spawn_agents());
    }

    private void standby(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
    }

    private void fused(String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("agent_id", agent);
        estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
        create_job_with_reschedule("bomb_timer", estimated_end_time, FlagTimerJob.class, Optional.of(jdm));
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "fused"));
        play("voice2", AGENT_EVENT_PATH, "selfdestruct", get_active_spawn_agents());
        // send(MQTT.CMD_ACOUSTIC, Tools.getProgressTickingScheme(MQTT.SIR2, bomb_timer * 1000), map_of_agents_and_sirens.get(agent).toString());
        // HURRY UP SIGNAL zweckentfremdet
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.TEAM_HURRY_UP_SIGNAL), map_of_agents_assigned_to_sirens.get(agent).toString());
        send(MQTT.CMD_TIMERS, MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send(MQTT.CMD_VISUAL, new JSONObject().put("progress", "remaining"), agent);
        send(MQTT.CMD_VARS, MQTT.toJSON("fused", "hot", "next_cp", get_next_cp()), get_all_spawn_agents());
    }

    private void defused(String agent) {
        estimated_end_time = end_time;
        deleteJob("bomb_timer");
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defused"));
        send(MQTT.CMD_TIMERS, MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.BLUE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_VARS, MQTT.toJSON("fused", "cold", "next_cp", get_next_cp()), get_all_spawn_agents());
        // the defuse siren is activated here: find SIR3 in create_CP_FSM()
    }

    private void defended(String agent) {
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defended"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.BLUE, MQTT.RECURRING_SCHEME_VERY_FAST), agent);
        send(MQTT.CMD_VARS, MQTT.toJSON("overtime", overtime ? "SUDDEN DEATH" : ""), get_all_spawn_agents());
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    private void taken(String agent) {
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "taken"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.SCHEME_LONG), agent);
        active_capture_point++;
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.OFF), map_of_agents_assigned_to_sirens.get(agent).toString());

        // activate next CP or end the game when no CPs left
        boolean all_cps_taken = active_capture_point == capture_points.size();
        if (overtime || all_cps_taken) {
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_VERY_FAST), agent);
            game_fsm.ProcessFSM(_msg_GAME_OVER);
        } else {
            send(MQTT.CMD_VISUAL, new JSONObject(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.FLAG_TAKEN), agent);
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR4, MQTT.SCHEME_LONG), map_of_agents_assigned_to_sirens.get(agent).toString());
            next_spawn_segment();
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
        }
    }

    @Override
    protected long getRemaining() {
        //if (game_fsm_get_current_state().equals(_state_EPILOG)) return super.getRemaining();
        return estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : super.getRemaining();
    }

    private void prolog(String agent) {
        send(MQTT.CMD_VISUAL, show_number_as_leds(capture_points.indexOf(agent) + 1, MQTT.RECURRING_SCHEME_FAST), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    /**
     * @param num 1..5
     * @return signals for led stripes. out of bounds means all off
     */
    JSONObject show_number_as_leds(int num, final String signal) {
        if (num < 1 || num > 5) return MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF);
        JSONObject result = new JSONObject();
        ALL_LEDS.forEach(led -> result.put(led, ALL_LEDS.indexOf(led) < num ? signal : MQTT.OFF));
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
                    send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.OFF), map_of_agents_assigned_to_sirens.get(agent).toString());
                    send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR3, MQTT.SCHEME_LONG), map_of_agents_assigned_to_sirens.get(agent).toString());
                    play("voice3", AGENT_EVENT_PATH, "shutdown", get_active_spawn_agents());
                    return true;
                }
            });
            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    send(MQTT.CMD_VARS, MQTT.toJSON("active_cp", agent), get_all_spawn_agents());
                    add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
                    if (active_capture_point > 0)
                        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR2, MQTT.SCHEME_LONG), map_of_agents_assigned_to_sirens.get(agent).toString());
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
            create_job_with_suspension("timed_respawn",
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
        send(MQTT.CMD_VARS, MQTT.toJSON("overtime", "overtime"), get_all_spawn_agents());
        play("voice1", AGENT_EVENT_PATH, "overtime", get_active_spawn_agents());
        overtime = true;
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)
                && cpFSMs.get(agent_id).getCurrentState().
                matches(_state_FUSED + "|" + _state_DEFUSED + "|" + _state_OVERTIME)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
        } else
            super.on_external_message(agent_id, source, message);
    }

    @Override
    public void on_time_to_respawn(JobDataMap map) {
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.SCHEME_LONG), get_active_spawn_agents());
        send(MQTT.CMD_TIMERS, MQTT.toJSON("respawn", Integer.toString(respawn_timer)), get_active_spawn_agents());
    }


    @Override
    public JSONObject get_full_state() {
        JSONObject json = super.get_full_state();
        json.getJSONObject("played")
                .put("capture_points_taken", active_capture_point)
                .put("max_capture_points", cpFSMs.size());
        return json;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        String current_active_agent = capture_points.get(Math.min(
                active_capture_point,
                cpFSMs.size() - 1
        )).toString();
        model.addAttribute("capture_points_taken", active_capture_point);
        model.addAttribute("max_capture_points", cpFSMs.size());
        model.addAttribute("current_active_agent", current_active_agent);
        model.addAttribute("current_active_agent_state", cpFSMs.get(current_active_agent).getCurrentState());
        model.addAttribute("next_active_agent", get_next_cp());
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
        deleteJob("timed_respawn");
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.OFF), get_active_spawn_agents());
        send(MQTT.CMD_TIMERS, MQTT.toJSON("respawn", "0"), get_active_spawn_agents());
    }

    private String get_next_cp() {
        if (capture_points.size() == 1) return "This is the only one";
        return active_capture_point >= capture_points.size() - 1 ? "This is the LAST" : "Next: " + capture_points.get(active_capture_point + 1);
    }

    @Override
    public void flag_time_is_up(String agent_id) {
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
