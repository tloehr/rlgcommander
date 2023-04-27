package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashBasedTable;
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
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Log4j2
/**
 * Games deriving from this class will have team respawn agents with optional countdown functions.
 */
public abstract class WithRespawns extends Pausable {
    public static final String _state_WE_ARE_PREPARING = "WE_ARE_PREPARING";
    public static final String _state_STANDBY = "STAND_BY";
    public static final String _state_WE_ARE_READY = "WE_ARE_READY";
    public static final String _state_IN_GAME = "IN_GAME";
    public static final String _state_COUNTDOWN_TO_START = "COUNTDOWN_TO_START";
    public static final String _state_COUNTDOWN_TO_RESUME = "COUNTDOWN_TO_RESUME";
    public static final String _msg_START_COUNTDOWN = "start_countdown";
    public static final String _msg_STANDBY = "stand_by";
    public static final String _msg_ACTIVATE = "activate";
    public static final String _msg_RESPAWN_SIGNAL = "respawn_signal";
    public static final String RED_SPAWN = "red_spawn";
    public static final String BLUE_SPAWN = "blue_spawn";

    public static final String[] SPREE_ANNOUNCEMENTS = new String[]{"doublekill", "triplekill", "quadrakill", "pentakill", "spree"};
    public static final String[] ENEMY_SPREE_ANNOUNCEMENTS = new String[]{"enemydoublekill", "enemytriplekill", "enemyquadrakill", "enemypentakill", "enemyspree"};

    private final JobKey deferredRunGameJob, respawnTimerJobkey;
    private final int starter_countdown;
    private final String intro_mp3;
    private final boolean game_lobby;

    // Table of Teams in rows, segments in cols
    // cells contain pairs of agent names and FSMs
    protected final Table<String, Integer, Pair<String, FSM>> spawn_segments;
    protected final int respawn_timer;
    protected int active_segment;
    private final JSONObject spawn_parameters;

    public WithRespawns(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        this.spawn_parameters = game_parameters.getJSONObject("spawns");
        this.respawn_timer = spawn_parameters.optInt("respawn_time");
        this.game_lobby = spawn_parameters.optBoolean("game_lobby");
        this.starter_countdown = spawn_parameters.optInt("starter_countdown");
        this.intro_mp3 = spawn_parameters.optString("intro_mp3", "<none>");
        this.deferredRunGameJob = new JobKey("run_the_game", uuid.toString());
        this.respawnTimerJobkey = new JobKey("timed_respawn", uuid.toString());
        this.spawn_segments = HashBasedTable.create();
        active_segment = 0;

        // split up spawn description by teams
        // one definition for each side
        spawn_parameters.getJSONArray("teams").forEach(j -> {
                    JSONObject teams = (JSONObject) j;
                    final String spawn_role = teams.getString("role");
                    final String led = teams.getString("led");
                    final String team_name = teams.getString("name");

                    // this is a list of a list of spawn agents
                    // every inner list combines all agents for a section
                    MutableInt section_number = new MutableInt(-1);
                    teams.getJSONArray("agents").forEach(o -> {
                                JSONArray section = (JSONArray) o;
                                section.forEach(spawn_agent_in_this_section -> {
                                    section_number.increment();
                                    String agent = spawn_agent_in_this_section.toString();
                                    spawn_segments.put(spawn_role, section_number.intValue(),
                                            new ImmutablePair<>(
                                                    agent,
                                                    create_Spawn_FSM(agent, spawn_role, led, team_name)
                                            )
                                    );
                                    // every spawn is a potential siren
                                    // we ignore a STANDBY state here, as it is only used for start stop signals
                                    agents.put(agent, "sirens");
                                    roles.put("sirens", agent);
                                });
                            }
                    );
                }
        );
    }

    @SneakyThrows
    private FSM create_Spawn_FSM(final String agent, String spawn_role, final String led_device_id, final String teamname) {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/spawn.xml"), null);
        fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> {
            send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), agent);
            send("visual", MQTT.toJSON(MQTT.ALL, "off", led_device_id, "fast"), agent);
            send("paged", MQTT.merge(
                    MQTT.page("page0",
                            "I am ${agentname} and will", "be Your spawn.", "You are " + teamname, "!! Standby !!"),
                    MQTT.page("page1", game_description)), agent
            );
        });
        fsm.setStatesAfterTransition(_state_STANDBY, (state, obj) -> {
            send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), agent);
            send("acoustic", MQTT.toJSON("all", "off"), agent);
            send("visual", MQTT.toJSON("all", "off"), agent);
            send("paged", MQTT.merge(
                    MQTT.page("page0", "", "THIS SPAWN IS", "", "INACTIVE"),
                    MQTT.page("page1", "THIS SPAWN IS", "", "INACTIVE", "")), agent);
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_PREPARING, (state, obj) -> {
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,200;on,400;off,75;on,100;off,1"), agent);
            send("paged", MQTT.merge(
                    MQTT.page("page0", " !! GAME LOBBY !! ", "", "Press button", "when ready"),
                    MQTT.page("page1", " !! GAME LOBBY !! ", "", "", "")
            ), agent);
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_READY, (state, obj) -> {
            send("acoustic", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,100;on,400;off,1"), agent);
            // if ALL teams are ready, the GAME is READY to start
            // we filter on all agents in the active segment for both teams
            if (spawn_segments.column(active_segment).values().stream()
                    .allMatch(stringFSMPair -> stringFSMPair.getValue().getCurrentState().equals(_state_WE_ARE_READY)))
                game_fsm.ProcessFSM(_msg_READY);
            else
                send("paged", MQTT.merge(
                        MQTT.page("page0", " !! GAME LOBBY !! ", "WE ARE READY", "", ""),
                        MQTT.page("page1", " !! GAME LOBBY !! ", "WE ARE READY", "WAITING FOR", "OTHER TEAM")
                ), agent);
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_START, (state, obj) -> {
            send("timers", MQTT.toJSON("countdown", Integer.toString(starter_countdown)), agent);
            send("paged", MQTT.page("page0", " The Game starts ", "        in", "       ${countdown}", ""), agent);
            send("play", MQTT.toJSON("subpath", "intro", "soundfile", intro_mp3), agent);
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_RESUME, (state, obj) -> {
            send("timers", MQTT.toJSON("countdown", Integer.toString(resume_countdown)), agent); // sending to everyone
            send("paged", MQTT.page("page0", " The Game resumes ", "        in", "       ${countdown}", ""), agent);
        });
        fsm.setStatesAfterTransition(_state_IN_GAME, (state, obj) -> {
            send("visual", MQTT.toJSON(MQTT.ALL, "off", led_device_id, "fast"), agent);
            send("paged", getSpawnPages(_state_RUNNING), agent);
        });
        fsm.setStatesAfterTransition(_state_PAUSING, (state, obj) -> send("paged", MQTT.merge(getSpawnPages(state), MQTT.page("pause", "", "      PAUSE      ", "", "")), agent));
        fsm.setStatesAfterTransition(_state_EPILOG, (state, obj) -> send("paged", getSpawnPages(state), agent));
        fsm.setAction(_state_IN_GAME, _msg_RESPAWN_SIGNAL, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return false;
                on_respawn_signal_received(spawn_role, agent);
                return true;
            }
        });

        return fsm;
    }

    @Override
    public void on_run() {
        super.on_run();
        deleteJob(deferredRunGameJob);
        // create timed respawn if necessary
        if (respawn_timer > 0) {
            create_resumable_job(respawnTimerJobkey,
                    SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(respawn_timer).repeatForever(),
                    RespawnTimerJob.class, Optional.empty());
            send("timers", MQTT.toJSON("respawn", Integer.toString(respawn_timer)), get_all_spawn_agents());
        }
        send_message_to_agents_in_segment(active_segment, _msg_RUN);
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        send_message_to_all_agents(_msg_GAME_OVER);
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_PAUSE)) send("timers", MQTT.toJSON("_clearall", ""), get_active_spawn_agents());
        if (message.equals(_msg_CONTINUE)) send_message_to_agents_in_segment(active_segment, _msg_CONTINUE);
    }

    @Override
    public void on_reset() {
        super.on_reset();
        send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), get_active_spawn_agents());
        active_segment = 0;
        deleteJob(deferredRunGameJob);
        delete_timed_respawn();
        send_message_to_all_agents(_msg_STANDBY);
        send_message_to_agents_in_segment(active_segment, _msg_RESET);
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_TEAMS_NOT_READY)) {
            if (!game_lobby) process_internal_message(_msg_READY);
            else send_message_to_agents_in_segment(active_segment, _msg_PREPARE);
        }
        if (state.equals(_state_TEAMS_READY)) {
            if (starter_countdown > 0) {
                send_message_to_agents_in_segment(active_segment, _msg_START_COUNTDOWN);
                create_job(deferredRunGameJob, LocalDateTime.now().plusSeconds(starter_countdown), RunGameJob.class, Optional.empty());
            } else {
                process_internal_message(_msg_RUN);
            }
        }
        if (state.equals(_state_PAUSING)) send_message_to_agents_in_segment(active_segment, _msg_PAUSE);
    }

    void send_message_to_agents_in_segment(int segment, String message) {
        spawn_segments.column(segment).values().forEach(stringFSMPair -> stringFSMPair.getValue().ProcessFSM(message));
    }

    void send_message_to_agent_in_segment(int segment, String agent, String message) {
        spawn_segments.column(segment).values()
                .stream().filter(stringFSMPair -> stringFSMPair.getLeft().equals(agent))
                .forEach(stringFSMPair -> {
                    log.debug(stringFSMPair);
                    stringFSMPair.getValue().ProcessFSM(message);
                });
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

    void next_spawn_segment() {
        send_message_to_agents_in_segment(active_segment, _msg_STANDBY);
        active_segment++;
        if (active_segment == spawn_segments.size()) active_segment = 0;
        send_message_to_agents_in_segment(active_segment, _msg_ACTIVATE);
    }


    @Override
    protected void on_cleanup() {
        super.on_cleanup();
        spawn_segments.clear();
    }

    @Override
    public void process_external_message(String sender, String item, JSONObject message) {
        if (sender.equals(RespawnTimerJob._sender_TIMED_RESPAWN))
            send_message_to_agents_in_segment(active_segment, _msg_RESPAWN_SIGNAL);
        else
            // there is no other class above us which cares about external messages
            // so we simply consume it
            send_message_to_agent_in_segment(active_segment, sender, item);
    }

    /**
     * implement this method to react on button presses on a spawn agent
     *
     * @param spawn_role spawn role for this agent
     * @param agent      agent id
     */
    protected void on_respawn_signal_received(String spawn_role, String agent) {
    }

    ;

    protected void delete_timed_respawn() {
        if (respawn_timer <= 0) return;
        deleteJob(respawnTimerJobkey);
    }

    @Override
    public JSONObject getState() {
        final JSONObject statusObject = super.getState()
                .put("game_lobby", game_lobby)
                .put("starter_countdown", starter_countdown)
                .put("active_segment", active_segment);

        spawn_segments.column(active_segment)
                .values()
                .forEach(stringFSMPair -> statusObject.getJSONObject("agent_states").put(stringFSMPair.getLeft(), stringFSMPair.getRight().getCurrentState()));

        return statusObject;
    }

    protected Collection<String> get_active_spawn_agents() {
        return spawn_segments.column(active_segment).values().stream().map(Pair::getKey).collect(Collectors.toSet());
    }

    protected Collection<String> get_all_spawn_agents() {
        return spawn_segments.values().stream().map(Pair::getKey).collect(Collectors.toSet());
    }

    protected Collection<String> get_active_spawn_agents(String team) {
        return spawn_segments.row(team).values().stream().map(Pair::getKey).collect(Collectors.toSet());
    }

    protected String get_opposing_team(String spawn) {
        return spawn.equals(RED_SPAWN) ? BLUE_SPAWN : RED_SPAWN;
    }


}
