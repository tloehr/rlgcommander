package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.traits.HasBombtimer;
import de.flashheart.rlg.commander.games.jobs.BombTimerJob;
import de.flashheart.rlg.commander.games.jobs.RespawnTimerJob;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
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
public class Farcry extends Timed implements HasBombtimer {
    public static final String _state_STANDBY = "STANDBY";
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_DEFENDED = "DEFENDED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_OVERTIME = "OVERTIME";
    public static final String _msg_ACTIVATE = "activate";

    private final int bomb_timer;
    private final int respawn_timer;
    private final JobKey bombTimerJobkey, respawnTimerJobkey;
    private final List<Object> capture_points;
    private LocalDateTime estimated_end_time;
    private final Map<String, FSM> cpFSMs;
    private final Map map_of_agents_and_sirens;
    // which CP to take next
    private int active_capture_point;
    boolean overtime;

    public Farcry(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ArrayIndexOutOfBoundsException, ParserConfigurationException, IOException, SAXException {
        super(game_parameters, scheduler, mqttOutbound);
        estimated_end_time = null;
        log.info("    ______\n" +
                "   / ____/___ __________________  __\n" +
                "  / /_  / __ `/ ___/ ___/ ___/ / / /\n" +
                " / __/ / /_/ / /  / /__/ /  / /_/ /\n" +
                "/_/    \\__,_/_/   \\___/_/   \\__, /\n" +
                "                           /____/");
        this.bombTimerJobkey = new JobKey("bomb_timer", uuid.toString());
        this.respawnTimerJobkey = new JobKey("respawn_timer", uuid.toString());
        this.bomb_timer = game_parameters.getInt("bomb_time");
        this.respawn_timer = game_parameters.getInt("respawn_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawn = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_timer), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Respawn every: %s", ldtRespawn.format(DateTimeFormatter.ofPattern("mm:ss"))));

        // additional parsing agents and sirens
        map_of_agents_and_sirens = new HashMap<>();
        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();
        if (capture_points.size() > 5) throw new ArrayIndexOutOfBoundsException("max number of capture points is 5");
        List sirs = game_parameters.getJSONObject("agents").getJSONArray("capture_sirens").toList();
        for (int i = 0; i < capture_points.size(); i++) {
            map_of_agents_and_sirens.put(capture_points.get(i), sirs.get(i));
        }

        cpFSMs = new HashMap<>();
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        add_spawn_for("attacker_spawn", MQTT.RED, "Attacker");
        add_spawn_for("defender_spawn", MQTT.BLUE, "Defender");
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_EPILOG)) {
            deleteJob(respawnTimerJobkey);
            // to prevent respawn signals AFTER game_over
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, "off"), roles.get("spawns"));
            mqttOutbound.send("timers", MQTT.toJSON("respawn", "0"), roles.get("spawns"));
        }
        if (state.equals(_state_PROLOG)) {
            active_capture_point = 0;
            overtime = false;
        }
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) {
            estimated_end_time = end_time;
            // create timed respawn if necessary
            if (respawn_timer > 0) {
                create_resumable_job(respawnTimerJobkey,
                        SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(respawn_timer).repeatForever(),
                        RespawnTimerJob.class, Optional.empty());
                mqttOutbound.send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), roles.get("spawns"));
            }

            // all CPs to standby
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));
            // activate first cp
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
        }
        if (message.equals(_msg_RESET)) {
            estimated_end_time = null;
            deleteJob(bombTimerJobkey);
            deleteJob(respawnTimerJobkey);
            active_capture_point = 0;
            overtime = false;
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
            mqttOutbound.send("vars", MQTT.toJSON("overtime", ""), roles.get("spawns"));
        }
        //todo: pause and resume missing
    }

    private void standby(String agent) {
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
//        mqttOutbound.send("signals", MQTT.toJSON("sir_all", "off"), map_of_agents_and_sirens.get(agent).toString());
    }


    private void fused(String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("bombid", agent);
        estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
        create_resumable_job(bombTimerJobkey, estimated_end_time, BombTimerJob.class, Optional.of(jdm));
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "fused"));
//        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("play", MQTT.toJSON("subpath", "announce", "soundfile", "selfdestruct"), roles.get("spawns"));
        mqttOutbound.send("acoustic", MQTT.toJSON("sir2", Tools.getProgressTickingScheme(bomb_timer * 1000)), map_of_agents_and_sirens.get(agent).toString());
        mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "progress:remaining"), agent);
        mqttOutbound.send("vars", MQTT.toJSON("fused", "hot", "next_cp", get_next_cp()), roles.get("spawns"));
    }

    private void defused(String agent) {
        estimated_end_time = end_time;
        deleteJob(bombTimerJobkey);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defused"));
//        if (game_fsm.getCurrentState().equalsIgnoreCase(_state_RUNNING)) process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "timer:remaining"), agent);
        mqttOutbound.send("vars", MQTT.toJSON("fused", "cold", "next_cp", get_next_cp()), roles.get("spawns"));
    }

    private void defended(String agent) {
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "defended"));
        game_fsm.ProcessFSM(_msg_GAME_OVER);
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.BLUE, "very_fast"), agent);
        mqttOutbound.send("vars", MQTT.toJSON("overtime", overtime ? "SUDDEN DEATH" : ""), roles.get("spawns"));
    }

    private void taken(String agent) {
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "taken"));
//        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        active_capture_point++;
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR2, "off"), map_of_agents_and_sirens.get(agent).toString());

        // activate next CP or end the game when no CPs left
        boolean all_cps_taken = active_capture_point == capture_points.size();
        if (overtime || all_cps_taken) {
            game_fsm.ProcessFSM(_msg_GAME_OVER);
            mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "very_fast"), agent);
        } else {
            mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.RED, "10:on,250;off,250"), agent);
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR4, "long"), map_of_agents_and_sirens.get(agent).toString());
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_ACTIVATE);
        }
    }

    @Override
    protected long getRemaining() {
        return estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : super.getRemaining();
    }

    private void prolog(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "I will be a", "Capture Point"),
                agent);

        //mqttOutbound.send("signals", MQTT.toJSON(MQTT.LED_ALL, "off", MQTT.WHITE, "fast"), agent);
        mqttOutbound.send("visual", show_number_as_leds(capture_points.indexOf(agent) + 1, "fast"), agent);

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


    private FSM create_CP_FSM(final String agent) {
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
                    mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR2, "off"), map_of_agents_and_sirens.get(agent).toString());
                    mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR3, "1:on,2000;off,1"), map_of_agents_and_sirens.get(agent).toString());
                    mqttOutbound.send("play", MQTT.toJSON("subpath", "announce", "soundfile", "shutdown"), roles.get("spawns"));
                    return true;
                }
            });
            fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    mqttOutbound.send("vars", MQTT.toJSON("active_cp", agent), roles.get("spawns"));
                    addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "activated"));
                    if (active_capture_point > 0)
                        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR2, "long"), map_of_agents_and_sirens.get(agent).toString());
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void overtime() {
        addEvent(new JSONObject().put("item", "overtime"));
//        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("vars", MQTT.toJSON("overtime", "overtime"), roles.get("spawns"));
        mqttOutbound.send("play", MQTT.toJSON("subpath", "announce", "soundfile", "overtime"), roles.get("spawns"));
        overtime = true;
    }

    @Override
    public void process_message(String sender, String item, JSONObject message) {
        if (!item.equalsIgnoreCase(_msg_BUTTON_01)) {
            log.trace("no button message. discarding.");
            return;
        }
        if (!message.getString("button").equalsIgnoreCase("up")) {
            log.trace("only reacting on button UP. discarding.");
            return;
        }
        if (hasRole(sender, "capture_points")) {
            if (cpFSMs.get(sender).getCurrentState().matches(_state_FUSED + "|" + _state_DEFUSED + "|" + _state_OVERTIME))
                cpFSMs.get(sender).ProcessFSM(item.toLowerCase());
//        } else if (hasRole(sender, "spawns")) {
//            super.process_message(sender, _msg_RESPAWN_SIGNAL, message);
        } else super.process_message(sender, item, message);
    }


    @Override
    public JSONObject getState() {
        final JSONObject statusObject = super.getState()
                .put("capture_points_taken", active_capture_point)
                .put("max_capture_points", cpFSMs.size());
        cpFSMs.forEach((agentid, fsm) -> statusObject.getJSONObject("agent_states").put(agentid, fsm.getCurrentState()));
        // todo: integrate states from the spawn agents ?
        //statusObject.put("agent_states", states);
        return statusObject;
    }

    @Override
    protected JSONObject getSpawnPages() {
        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over", "Capture Points taken: ", active_capture_point + " of " + capture_points.size(), "${overtime}");
        }
        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            return MQTT.page("page0", "Remaining: ${remaining}", "Actv: ${active_cp}->${fused}", "${next_cp} ${overtime}", respawn_timer > 0 ? "Next respawn: ${respawn}" : "");

        return MQTT.page("page0", game_description);
    }


    @Override
    protected void respawn(String role, String agent) {
        // the last part of this message is a delayed buzzer sound, so its end lines up with the end of the respawn period
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_timer * 1000 - 2925 - 100)), roles.get("spawns"));
        mqttOutbound.send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), roles.get("spawns"));
    }

    private String get_next_cp() {
        if (capture_points.size() == 1) return "";
        return active_capture_point == capture_points.size() - 1 ? "This is the LAST" : "Next: " + capture_points.get(active_capture_point + 1);
    }


    @Override
    public void game_time_is_up() {
        log.info("Game time is up");
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_TIME_IS_UP));
    }

    @Override
    public void bomb_time_is_up(String bombid) {
        log.info("Bomb has exploded");
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_BOMB_TIME_IS_UP));
    }
}
