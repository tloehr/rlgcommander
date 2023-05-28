package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasFlagTimer;
import de.flashheart.rlg.commander.games.jobs.FlagTimerJob;
import de.flashheart.rlg.commander.misc.Tools;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;


/**
 * Implementation for the FarCry 1 (2004) Assault Game Mode.
 */
@Log4j2
public class Farcry extends Timed implements HasFlagTimer {
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_DEFENDED = "DEFENDED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_OVERTIME = "OVERTIME";
    private static final int MAX_CAPTURE_POINTS = 6;

    private final int bomb_timer;

    private final JobKey bombTimerJobkey;
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

        log.info("\n    ______\n" +
                "   / ____/___ __________________  __\n" +
                "  / /_  / __ `/ ___/ ___/ ___/ / / /\n" +
                " / __/ / /_/ / /  / /__/ /  / /_/ /\n" +
                "/_/    \\__,_/_/   \\___/_/   \\__, /\n" +
                "                           /____/");
        this.bombTimerJobkey = new JobKey("bomb_timer", uuid.toString());
        this.bomb_timer = game_parameters.getInt("bomb_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawn = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_timer), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldt_game_time.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Respawn every: %s", ldtRespawn.format(DateTimeFormatter.ofPattern("mm:ss"))));

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
        log.debug(spawn_segments);
        // all segments must have the same size

        if (!spawn_segments.containsRow(RED_SPAWN)) throw new JSONException(RED_SPAWN + " missing");
        if (!spawn_segments.containsRow(BLUE_SPAWN)) throw new JSONException(BLUE_SPAWN + " missing");
        if (spawn_segments.rowMap().values().stream().map(integerPairMap -> integerPairMap.keySet().size()).distinct().count() > 1)
            throw new JSONException("all teams must have the same number of spawn segments");

        int num_of_spawn_segments = spawn_segments.row(RED_SPAWN).size();

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
        active_capture_point = 0;
        overtime = false;
        ran_once_already = false;
        send("vars", MQTT.toJSON("overtime", ""), get_all_spawn_agents());
    }

    private void standby(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
    }

    private void fused(String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("bombid", agent);
        estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
        create_resumable_job(bombTimerJobkey, estimated_end_time, FlagTimerJob.class, Optional.of(jdm));
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "fused"));
        send("play", MQTT.toJSON("subpath", "announce", "soundfile", "selfdestruct"), get_active_spawn_agents());
        send("acoustic", MQTT.toJSON("sir2", Tools.getProgressTickingScheme(bomb_timer * 1000)), map_of_agents_and_sirens.get(agent).toString());
        send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send("visual", MQTT.toJSON(MQTT.ALL, "progress:remaining"), agent);
        send("vars", MQTT.toJSON("fused", "hot", "next_cp", get_next_cp()), get_all_spawn_agents());
    }

    private void defused(String agent) {
        estimated_end_time = end_time;
        deleteJob(bombTimerJobkey);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defused"));
        send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "timer:remaining"), agent);
        send("vars", MQTT.toJSON("fused", "cold", "next_cp", get_next_cp()), get_all_spawn_agents());
    }

    private void defended(String agent) {
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defended"));
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "very_fast"), agent);
        send("vars", MQTT.toJSON("overtime", overtime ? "SUDDEN DEATH" : ""), get_all_spawn_agents());
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    private void taken(String agent) {
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "taken"));
        active_capture_point++;
        send("acoustic", MQTT.toJSON(MQTT.SIR2, "off"), map_of_agents_and_sirens.get(agent).toString());

        // activate next CP or end the game when no CPs left
        boolean all_cps_taken = active_capture_point == capture_points.size();
        if (overtime || all_cps_taken) {
            send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "very_fast"), agent);
            game_fsm.ProcessFSM(_msg_GAME_OVER);
        } else {
            send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "10:on,250;off,250"), agent);
            send("acoustic", MQTT.toJSON(MQTT.SIR4, "long"), map_of_agents_and_sirens.get(agent).toString());
            next_spawn_segment();
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
        }
    }

    @Override
    protected long getRemaining() {
        return estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : super.getRemaining();
    }

    private void prolog(String agent) {
        send("visual", show_number_as_leds(capture_points.indexOf(agent) + 1, "fast"), agent);
    }

    /**
     * @param num 1..5
     * @return signals for led stripes. out of bounds means all off
     */
    JSONObject show_number_as_leds(int num, final String signal) {
        if (num < 1 || num > 5) return MQTT.toJSON(MQTT.ALL, "off");
        List<String> leds_to_use = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(ALL_LEDS, 0, num)));
        List<String> leds_to_set_off = new ArrayList<>(Arrays.asList(ALL_LEDS));
        leds_to_set_off.removeAll(leds_to_use); // set difference

        JSONObject result = new JSONObject();
        leds_to_use.forEach(led -> result.put(led, signal));
        leds_to_set_off.forEach(led -> result.put(led, "off"));

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
                    send("acoustic", MQTT.toJSON(MQTT.SIR3, "1:on,2000;off,1"), map_of_agents_and_sirens.get(agent).toString());
                    send("play", MQTT.toJSON("subpath", "announce", "soundfile", "shutdown"), get_active_spawn_agents());
                    return true;
                }
            });
            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    send("vars", MQTT.toJSON("active_cp", agent), get_all_spawn_agents());
                    addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
                    if (active_capture_point > 0)
                        send("acoustic", MQTT.toJSON(MQTT.SIR2, "long"), map_of_agents_and_sirens.get(agent).toString());
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
        estimated_end_time = end_time;
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
        addEvent(new JSONObject().put("item", "overtime"));
        send("vars", MQTT.toJSON("overtime", "overtime"), get_all_spawn_agents());
        send("play", MQTT.toJSON("subpath", "announce", "soundfile", "overtime"), get_active_spawn_agents());
        overtime = true;
    }

    @Override
    public void process_external_message(String sender, String source, JSONObject message) {
        if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
        if (!message.getString("button").equalsIgnoreCase("up")) return;

        if (cpFSMs.containsKey(sender) && game_fsm.getCurrentState().equals(_state_RUNNING)
                && cpFSMs.get(sender).getCurrentState().
                matches(_state_FUSED + "|" + _state_DEFUSED + "|" + _state_OVERTIME))
            cpFSMs.get(sender).ProcessFSM(source.toLowerCase());
        else
            super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
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
            return MQTT.page("page0", "Remaining: ${remaining}", "Actv: ${active_cp}->${fused}", "${next_cp}", "${overtime}", respawn_timer > 0 ? "Next respawn: ${respawn}" : "");

        return MQTT.page("page0", game_description);
    }

    @Override
    protected void on_respawn_signal_received(String role, String agent) {
        // todo: think this over
//        send("acoustic", MQTT.toJSON(MQTT.BUZZER, String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_timer * 1000 - 2925 - 100)), get_active_spawn_agents());
//        send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), get_active_spawn_agents());
    }

    @Override
    protected void delete_timed_respawn() {
        super.delete_timed_respawn();
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
