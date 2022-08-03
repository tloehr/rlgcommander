package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.RunGameJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;

@Log4j2
/**
 * Games deriving from this class will have team respawn agents with optional countdown functions.
 */
public abstract class WithRespawns extends Pausable {
    public static final String _state_WE_ARE_PREPARING = "WE_ARE_PREPARING";
    public static final String _state_WE_ARE_READY = "WE_ARE_READY";
    public static final String _state_IN_GAME = "IN_GAME";
    public static final String _state_COUNTDOWN_TO_START = "COUNTDOWN_TO_START";
    public static final String _state_COUNTDOWN_TO_RESUME = "COUNTDOWN_TO_RESUME";
    public static final String _msg_START_COUNTDOWN = "start_countdown";
    public static final String _msg_RESPAWN_SIGNAL = "respawn_signal";

    private final JobKey runGameJob;
    private final int starter_countdown;
    private final String intro_mp3_file;
    private final boolean wait4teams2B_ready;
    private final HashMap<String, FSM> all_spawns;
    private final HashMap<String, String> spawnrole_for_this_agent;

    public WithRespawns(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.wait4teams2B_ready = game_parameters.getBoolean("wait4teams2B_ready");
        this.starter_countdown = game_parameters.getInt("starter_countdown");
        this.intro_mp3_file = game_parameters.getString("intro_mp3_file");
        this.runGameJob = new JobKey("run_the_game", uuid.toString());
        this.all_spawns = new HashMap<>();
        this.spawnrole_for_this_agent = new HashMap<>();
    }

    /**
     * adds a spawnagent for a team
     *
     * @param role          spawn role name for this agent
     * @param led_device_id led device to blink for this team (e.g. led_red for team red)
     * @param teamname      Name of the team to show on the LCD
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public void add_spawn_for(String role, String led_device_id, String teamname) throws ParserConfigurationException, IOException, SAXException {
        String agent = roles.get(role).stream().findFirst().get();
        all_spawns.put(role, create_Spawn_FSM(agent, role, led_device_id, teamname));
        spawnrole_for_this_agent.put(agent, role);
        // every spawn is a potential siren
        agents.put(agent, "sirens");
        roles.put("sirens", agent);
    }

    private FSM create_Spawn_FSM(final String agent, String role, final String led_device_id, final String teamname) throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/spawn.xml"), null);
        fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> {
            mqttOutbound.send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), agent);
            mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off", led_device_id, "fast"), agent);
            mqttOutbound.send("paged", MQTT.merge(
                    MQTT.page("page0",
                            "I am ${agentname} and will", "be Your spawn.", "You are " + teamname, "!! Standby !!"),
                    MQTT.page("page1", game_description)), agent
            );
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_PREPARING, (state, obj) -> {
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,200;on,400;off,75;on,100;off,1"), agent);
            mqttOutbound.send("paged", MQTT.page("page0", " !! NOT READY !! ", "Press button", "when Your team", "is ready"), agent);
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_READY, (state, obj) -> {
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,100;on,400;off,1"), agent);
            // if all teams are ready, the GAME is ready to start
            if (all_spawns.values().stream().allMatch(fsm1 -> fsm1.getCurrentState().equals(_state_WE_ARE_READY)))
                game_fsm.ProcessFSM(_msg_READY);
            else
                mqttOutbound.send("paged", MQTT.page("page0", " !! WE ARE READY !! ", "Waiting for others", "If NOT ready:", "Press button again"), agent);
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_START, (state, obj) -> {
            mqttOutbound.send("timers", MQTT.toJSON("countdown", Integer.toString(starter_countdown)), agent);
            mqttOutbound.send("paged", MQTT.page("page0", " The Game starts ", "        in", "       ${countdown}", ""), agent);
            mqttOutbound.send("play", MQTT.toJSON("subpath", "intro", "soundfile", intro_mp3_file), agent);
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_RESUME, (state, obj) -> {
            mqttOutbound.send("timers", MQTT.toJSON("countdown", Integer.toString(resume_countdown)), agent); // sending to everyone
            mqttOutbound.send("paged", MQTT.page("page0", " The Game resumes ", "        in", "       ${countdown}", ""), agent);
        });
        fsm.setStatesAfterTransition(_state_EPILOG, (state, obj) -> {
            mqttOutbound.send("paged", getPages(), agent);
        });
        fsm.setStatesAfterTransition(_state_PAUSING, (state, obj) -> {
            mqttOutbound.send("paged", MQTT.merge(
                            getPages(), MQTT.page("pause", "", "      PAUSE      ", "", "")),
                    agent);
        });
        // making the signal to spawn more abstract. e.g. Conquest spawns on a button, Farcry on a timer
        fsm.setAction(_state_IN_GAME, _msg_RESPAWN_SIGNAL, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return false;
                respawn(role, agent);
                return true;
            }
        });

        return fsm;
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            deleteJob(runGameJob);
            all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));
        }
        if (message.equals(_msg_CONTINUE)) {
            all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_CONTINUE));
        }
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_PROLOG)) {
            deleteJob(runGameJob);
            all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
        }
        if (state.equals(_state_TEAMS_NOT_READY)) {
            if (!wait4teams2B_ready) process_message(_msg_READY);
            else all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_PREPARE));
        }
        if (state.equals(_state_TEAMS_READY)) {
            if (starter_countdown > 0) {
                all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_START_COUNTDOWN));
                create_job(runGameJob, LocalDateTime.now().plusSeconds(starter_countdown), RunGameJob.class, Optional.empty());
            } else {
                process_message(_msg_RUN);
            }
        }
        if (state.equals(_state_RUNNING)) mqttOutbound.send("paged", getPages(), roles.get("spawns"));
        if (state.equals(_state_PAUSING)) all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_PAUSE));
        if (state.equals(_state_EPILOG)) all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_OVER));
    }

    @Override
    protected void on_cleanup() {
        super.on_cleanup();
        spawnrole_for_this_agent.clear();
        all_spawns.clear();
    }

    @Override
    public void process_message(String sender, String item, JSONObject message) {
        if (hasRole(sender, "spawns")) all_spawns.get(spawnrole_for_this_agent.get(sender)).ProcessFSM(item);
    }

    public void timed_respawn(){
        all_spawns.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESPAWN_SIGNAL));
    }

    /**
     * implement this method to react on button presses on a spawn agent
     *
     * @param role  spawn role for this agent
     * @param agent agent id
     */
    protected abstract void respawn(String role, String agent);

    @Override
    public JSONObject getState() {
        final JSONObject statusObject = super.getState()
                .put("wait4teams2B_ready", wait4teams2B_ready)
                .put("starter_countdown", starter_countdown);

        final JSONObject states = new JSONObject();
        all_spawns.forEach((agentid, fsm) -> states.put(agentid, fsm.getCurrentState()));
        statusObject.put("agent_states", states);
        return statusObject;
    }

}
