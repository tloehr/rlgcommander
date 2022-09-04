package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.RespawnTimerJob;
import de.flashheart.rlg.commander.games.jobs.RunGameJob;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
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
    public static final String _msg_STANDBY = "stand_by";
    public static final String _msg_RESPAWN_SIGNAL = "respawn_signal";
    public static final String SPAWN_TYPE_STATIC = "static";
    public static final String SPAWN_TYPE_ROLLING = "rolling";

    private final JobKey deferredRunGameJob, respawnTimerJobkey;
    private final int starter_countdown;
    private final String intro_mp3_file;
    private final boolean wait4teams2B_ready;
    //    private final HashMap<String, FSM> all_spawns;
//    private final HashMap<String, String> spawnrole_for_this_agent;
    // the key is a combination of team:segment
//    private final SetMultimap<String, Pair<String, FSM>> spawn_segments;
    // Table of Teams in cols, segments in rows
    // cells contain pairs of agent names and FSMs
    private final Table<String, Integer, Pair<String, FSM>> spawn_segments;
    protected final int respawn_timer;
    protected int active_segment;
    private final JSONObject spawn_parameters;

    /**
     * "spawns": {
     * "type": "static",
     * "wait4teams2B_ready": true,
     * "intro_mp3_file": "<random>",
     * "starter_countdown": 30,
     * "resume_countdown": 0,
     * "respawn_time": 0,
     * "teams": [
     * {
     * "role": "red_spawn",
     * "led": "red",
     * "name": "Team Red",
     * "agents": [
     *      ["ag30"]
     * ]
     * },
     * {
     * "role": "blue_spawn",
     * "led": "blu",
     * "name": "Team Blue",
     * "agents": [
     *      ["ag31"]
     * ]
     * }
     * ]
     * },
     * <p>
     */
    public WithRespawns(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.spawn_parameters = game_parameters.getJSONObject("spawns");
        this.respawn_timer = spawn_parameters.optInt("respawn_time");
        this.wait4teams2B_ready = spawn_parameters.optBoolean("wait4teams2B_ready");
        this.starter_countdown = spawn_parameters.optInt("starter_countdown");
        this.intro_mp3_file = spawn_parameters.optString("intro_mp3_file","<none>");
        this.deferredRunGameJob = new JobKey("run_the_game", uuid.toString());
        this.respawnTimerJobkey = new JobKey("timed_respawn", uuid.toString());
//        this.all_spawns = new HashMap<>();
//        this.spawnrole_for_this_agent = new HashMap<>();
        this.spawn_segments = HashBasedTable.create();
        active_segment = 0;

        spawn_parameters.getJSONArray("teams").forEach(j -> {
                    JSONObject teams = (JSONObject) j;
                    final String role = teams.getString("role");
                    final String led = teams.getString("led");
                    final String team = teams.getString("team");

                    // this is a list of a list of spawn agents
                    // every inner list combines all agents for a section
                    //
                    MutableInt section_number = new MutableInt(0);
                    teams.getJSONArray("agents").forEach(o -> {
                                JSONArray section = (JSONArray) o;
                                section.forEach(spawn_agent_in_this_section -> {
                                    section_number.increment();
                                    String agent = spawn_agent_in_this_section.toString();
                                    spawn_segments.put(role, section_number.intValue(),
                                            new ImmutablePair<>(
                                                    agent,
                                                    create_Spawn_FSM(agent, role, led, team)
                                            )
                                    );
                                    // every spawn is a potential siren
                                    agents.put(agent, "sirens");
                                    roles.put("sirens", agent);
                                });
                            }
                    );
                }
        );
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
//        String agent = roles.get(role).stream().findFirst().get();
//        all_spawns.put(role, create_Spawn_FSM(agent, role, led_device_id, teamname));
//        spawnrole_for_this_agent.put(agent, role);
//        // every spawn is a potential siren
//        agents.put(agent, "sirens");
//        roles.put("sirens", agent);
    }


    @SneakyThrows
    private FSM create_Spawn_FSM(final String agent, String role, final String led_device_id, final String teamname) {
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
            // if ALL teams are ready, the GAME is READY to start
            // we filter on all agents in the active segment for both teams
            if (spawn_segments.column(active_segment).values().stream()
                    .allMatch(stringFSMPair -> stringFSMPair.getValue().getCurrentState().equals(_state_WE_ARE_READY)))
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
            mqttOutbound.send("paged", getSpawnPages(), agent);
        });
        fsm.setStatesAfterTransition(_state_PAUSING, (state, obj) -> {
            mqttOutbound.send("paged", MQTT.merge(
                            getSpawnPages(), MQTT.page("pause", "", "      PAUSE      ", "", "")),
                    agent);
        });
        // making the signal to spawn more abstract. e.g. Conquest spawns on a button, Farcry on a timer
        fsm.setAction(_state_IN_GAME, _msg_RESPAWN_SIGNAL, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return false;
                on_respawn_signal_received(role, agent);
                return true;
            }
        });

        return fsm;
    }


    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            deleteJob(deferredRunGameJob);
            // create timed respawn if necessary
            if (respawn_timer > 0) {
                create_resumable_job(respawnTimerJobkey,
                        SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(respawn_timer).repeatForever(),
                        RespawnTimerJob.class, Optional.empty());
                mqttOutbound.send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), roles.get("spawns"));
            }
            send_message_to_agents_in_segment(active_segment, _msg_RUN);
        }
        if (message.equals(_msg_RESET)) delete_timed_respawn();

        if (message.equals(_msg_CONTINUE)) {
            send_message_to_agents_in_segment(active_segment, _msg_CONTINUE);
        }
    }

    void send_message_to_agents_in_segment(int segment, String message) {
        spawn_segments.column(segment).values().forEach(stringFSMPair -> stringFSMPair.getValue().ProcessFSM(message));
    }

    void send_message_to_agent_in_segment(int segment, String agent, String message) {
        spawn_segments.column(segment).values()
                .stream().filter(stringFSMPair -> stringFSMPair.getLeft().equals(agent))
                .forEach(stringFSMPair -> stringFSMPair.getValue().ProcessFSM(message)
                );
    }

    void send_message_to_all_agents(String message) {
        for (int segment = 0; segment < spawn_segments.size(); segment++) {
            send_message_to_agents_in_segment(segment, message);
        }
    }

    void send_message_to_all_inactive_segments(String message) {
        for (int segment = 0; segment < spawn_segments.size(); segment++) {
            if (segment != active_segment)
                send_message_to_agents_in_segment(segment, message);
        }
    }

    void next_segment() {
        active_segment++;
        if (active_segment == spawn_segments.size()) active_segment = 0;
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_PROLOG)) {
            deleteJob(deferredRunGameJob);
            delete_timed_respawn();
            send_message_to_all_inactive_segments(_msg_STANDBY);
            send_message_to_agents_in_segment(active_segment, _msg_RESET);
        }
        if (state.equals(_state_TEAMS_NOT_READY)) {
            if (!wait4teams2B_ready) process_message(_msg_READY);
            else send_message_to_agents_in_segment(active_segment, _msg_PREPARE);
        }
        if (state.equals(_state_TEAMS_READY)) {
            if (starter_countdown > 0) {
                send_message_to_agents_in_segment(active_segment, _msg_START_COUNTDOWN);
                create_job(deferredRunGameJob, LocalDateTime.now().plusSeconds(starter_countdown), RunGameJob.class, Optional.empty());
            } else {
                process_message(_msg_RUN);
            }
        }
        if (state.equals(_state_RUNNING)) mqttOutbound.send("paged", getSpawnPages(), roles.get("spawns"));
        if (state.equals(_state_PAUSING)) send_message_to_agents_in_segment(active_segment, _msg_PAUSE);
        if (state.equals(_state_EPILOG)) send_message_to_all_agents(_msg_START_COUNTDOWN);
    }

    @Override
    protected void on_cleanup() {
        super.on_cleanup();
        spawn_segments.clear();
    }

    @Override
    public void process_message(String sender, String item, JSONObject message) {
        if (hasRole(sender, "spawns"))
            send_message_to_agent_in_segment(active_segment, sender, item);
    }

    public void timed_respawn() {
        send_message_to_agents_in_segment(active_segment, _msg_RESPAWN_SIGNAL);
    }

    /**
     * implement this method to react on button presses on a spawn agent
     *
     * @param team  spawn role for this agent
     * @param agent agent id
     */
    protected void on_respawn_signal_received(String team, String agent) {
    }

    protected void delete_timed_respawn() {
        if (respawn_timer <= 0) return;
        deleteJob(respawnTimerJobkey);
        mqttOutbound.send("timers", MQTT.toJSON("respawn", "0"), roles.get("spawns"));
    }

    @Override
    public JSONObject getState() {
        final JSONObject statusObject = super.getState()
                .put("wait4teams2B_ready", wait4teams2B_ready)
                .put("starter_countdown", starter_countdown);

        final JSONObject states = new JSONObject();
        spawn_segments.column(active_segment).values().forEach(stringFSMPair -> states.put(stringFSMPair.getLeft(), stringFSMPair.getRight().getCurrentState()));
        statusObject.put("agent_states", states);
        return statusObject;
    }

}
