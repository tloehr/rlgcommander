package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.BombTimerJob;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
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
    public static final String _state_DEFUSED = "DEFUSED";
    public static final String _state_FUSED = "FUSED";
    public static final String _state_DEFENDED = "DEFENDED";
    public static final String _state_TAKEN = "TAKEN";
    public static final String _state_OVERTIME = "OVERTIME";

    private final int bomb_timer;
    private final JobKey bombTimerJobkey;
    private final List<Object> capture_points;
    private LocalDateTime estimated_end_time;
    private final Map<String, FSM> cpFSMs;
    private final Map map_of_agents_and_sirens;
    // which CP to take next
    private int active_capture_point;

    public Farcry(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException {
        super(game_parameters, scheduler, mqttOutbound);
        estimated_end_time = null;
        log.debug("    ______\n" +
                "   / ____/___ __________________  __\n" +
                "  / /_  / __ `/ ___/ ___/ ___/ / / /\n" +
                " / __/ / /_/ / /  / /__/ /  / /_/ /\n" +
                "/_/    \\__,_/_/   \\___/_/   \\__, /\n" +
                "                           /____/");
        this.bombTimerJobkey = new JobKey("bomb_timer", uuid.toString());
        this.bomb_timer = game_parameters.getInt("bomb_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))));

        // additional parsing agents and sirens
        map_of_agents_and_sirens = new HashMap<>();
        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList();
        List sirs = game_parameters.getJSONObject("agents").getJSONArray("capture_sirens").toList();
        for (int i = 0; i < capture_points.size(); i++)
            map_of_agents_and_sirens.put(capture_points.get(i), sirs.get(i));

        active_capture_point = 0;
        cpFSMs = new HashMap<>();
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        add_spawn_for("attacker_spawn", "led_red", "Attacker");
        add_spawn_for("defender_spawn", "led_grn", "Defender");
    }


    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) {
            estimated_end_time = end_time;
            // activate next capture point
            cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_RUN);
        }
        if (message.equals(_msg_RESET)) {
            estimated_end_time = null;
            deleteJob(bombTimerJobkey);
            active_capture_point = 0;
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
            mqttOutbound.send("vars", MQTT.toJSON("overtime", ""), roles.get("spawns"));
        }
    }

    @Override
    protected long getRemaining() {
        return estimated_end_time != null ? LocalDateTime.now().until(estimated_end_time, ChronoUnit.SECONDS) + 1 : 0l;
    }

    private void fused(String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("bombid", agent);
        estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
        create_resumable_job(bombTimerJobkey, estimated_end_time, BombTimerJob.class, Optional.of(jdm));

//        // increasing siren signals during bomb time. repeated beeps signals the quarter were in
//        int segment_time = bomb_timer / 4 * 1000;
//        String siren_tick = "on,100;off,100;";
//        String signal = "1:on,2000;off," + (segment_time - 2000) + ";";
//        for (int segment = 2; segment <= 4; segment++) {
//            String pause_time = "off," + (segment_time - 200 * segment) + ";";
//            signal += StringUtils.repeat(siren_tick, segment) + pause_time;
//        }

        addEvent(String.format("Bomb@%s FUSED", agent));
        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("signals", MQTT.toJSON("sir2", Tools.getProgressTickingScheme(bomb_timer * 1000)), map_of_agents_and_sirens.get(agent).toString());
        mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "progress:remaining"), agent);
        mqttOutbound.send("vars", MQTT.toJSON("fused", "fused"), roles.get("spawns"));
    }

    private void defused(String agent) {
        estimated_end_time = end_time;
        deleteJob(bombTimerJobkey);
        addEvent(String.format("Bomb@%s DE-FUSED", agent));
        if (game_fsm.getCurrentState().equalsIgnoreCase(_state_RUNNING)) process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("signals", MQTT.toJSON("sir2", "off"), map_of_agents_and_sirens.get(agent).toString());
        mqttOutbound.send("timers", MQTT.toJSON("remaining", Long.toString(getRemaining())), agents.keySet());
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_grn", "timer:remaining"), agent);
        mqttOutbound.send("vars", MQTT.toJSON("fused", "defused"), roles.get("spawns"));
    }

    private void defended(String agent) {
        game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    private void taken(String agent) {
        addEvent(String.format("Bomb#%s@%s EXPLODED", active_capture_point, agent));
        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        active_capture_point++;
        mqttOutbound.send("signals", MQTT.toJSON("sir2", "off"), map_of_agents_and_sirens.get(agent).toString());
        if (active_capture_point == capture_points.size()) game_fsm.ProcessFSM(_msg_GAME_OVER);
        else cpFSMs.get(capture_points.get(active_capture_point)).ProcessFSM(_msg_RUN);
    }

    private void prolog(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "I will be a", "Capture Point"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_wht", "fast"), agent);
    }


    private FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/farcry.xml"), null);
            fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> prolog(agent));
            fsm.setStatesAfterTransition(_state_DEFUSED, (state, obj) -> defused(agent));
            fsm.setStatesAfterTransition(_state_FUSED, (state, obj) -> fused(agent));
            fsm.setStatesAfterTransition(_state_DEFENDED, (state, obj) -> defended(agent));
            fsm.setStatesAfterTransition(_state_TAKEN, (state, obj) -> taken(agent));
            fsm.setStatesAfterTransition(_state_OVERTIME, (state, obj) -> overtime(agent));
            // DEFUSED => FUSED
            fsm.setAction(_state_FUSED, _msg_BUTTON_01, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    mqttOutbound.send("signals", MQTT.toJSON("sir3", "short"), map_of_agents_and_sirens.get(agent).toString());
                    return true;
                }
            });
            fsm.setAction(_state_PROLOG, _msg_RUN, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    // start siren for the next flag - starting with the second flag
                    if (active_capture_point > 0)
                        mqttOutbound.send("signals", MQTT.toJSON("sir2", "medium"), map_of_agents_and_sirens.get(agent).toString());
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void overtime(String agent) {
        addEvent("REACHED OVERTIME");
        process_message(_msg_IN_GAME_EVENT_OCCURRED);
        mqttOutbound.send("vars", MQTT.toJSON("overtime", "overtime"), roles.get("spawns"));
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
            if (game_fsm.getCurrentState().equals(_state_RUNNING))
                cpFSMs.get(sender).ProcessFSM(item.toLowerCase());
        } else super.process_message(sender, item, message);
    }

    @Override
    protected void respawn(String role, String agent) {

    }

    @Override
    public JSONObject getState() {
        final JSONObject states = new JSONObject();
        cpFSMs.forEach((agentid, fsm) -> states.put(agentid, fsm.getCurrentState()));
        final JSONObject statusObject = super.getState().put("agent_states", states);
        return statusObject;
    }

    @Override
    protected JSONObject getPages() {
        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            return MQTT.page("page0", "Game Over", "Bombs exploded: " + active_capture_point, "", "${overtime}");
        }
        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            return MQTT.page("page0", "Remaining: ${remaining}", capture_points.get(active_capture_point) + ": ${fused}", "", "${overtime}");

        return MQTT.page("page0", game_description);
    }

//
//    public void respawn() {
//        // the last part of this message is a delayed buzzer sound, so its end lines up with the end of the respawn period
//        mqttOutbound.send("signals", MQTT.toJSON("buzzer", String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_period * 1000 - 2925 - 100)), roles.get("spawns"));
//        mqttOutbound.send("timers", MQTT.toJSON("respawn", Long.toString(respawn_period)), roles.get("spawns"));
//    }


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
