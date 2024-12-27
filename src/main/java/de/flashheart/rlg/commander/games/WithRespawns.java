package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Team;
import de.flashheart.rlg.commander.games.jobs.DelayedAudioJob;
import de.flashheart.rlg.commander.games.jobs.RunGameJob;
import de.flashheart.rlg.commander.games.traits.HasDelayedAudio;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.javatuples.Quartet;
import org.json.JSONArray;
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

//todo: when message "ready" is sent manually, the spawn agents do not move to the appropriate state.
@Log4j2
 /*
  Games deriving from this class will have team respawn agents with optional countdown functions.
 */
public abstract class WithRespawns extends Pausable implements HasDelayedAudio {
    public static final String _state_WE_ARE_PREPARING = "WE_ARE_PREPARING";
    public static final String _state_STANDBY = "STAND_BY";
    public static final String _state_WE_ARE_READY = "WE_ARE_READY";
    public static final String _state_HURRY_UP = "HURRY_UP";
    public static final String _state_IN_GAME = "IN_GAME";
    public static final String _state_COUNTDOWN_TO_START = "COUNTDOWN_TO_START";
    public static final String _state_COUNTDOWN_TO_RESUME = "COUNTDOWN_TO_RESUME";
    public static final String _msg_START_COUNTDOWN = "start_countdown";
    public static final String _msg_STANDBY = "stand_by";
    public static final String _msg_ACTIVATE = "activate";
    public static final String _msg_REINIT = "reinit";
    public static final String _msg_ANOTHER_TEAM_IS_READY = "another_team_is_ready";
//    public static final String _msg_BUTTON_01 = "respawn_signal";

    public static final String[] SPREE_ANNOUNCEMENTS = new String[]{"doublekill", "triplekill", "quadrakill", "pentakill", "spree"};
    public static final String[] ENEMY_SPREE_ANNOUNCEMENTS = new String[]{"enemydoublekill", "enemytriplekill", "enemyquadrakill", "enemypentakill", "enemyspree"};
    private static final long DELAY_IN_MS_MUSIC_2_COUNTDOWN_VOICE = 6080;

    // will be set in javascript automatically by voice selection
    private final int starter_countdown;
    private final String intro_mp3, intro_voice, pause_mp3;
    private final boolean game_lobby;
    private final boolean announce_sprees;
    private int team1_killing_spree_length;
    private int team2_killing_spree_length;
    private String TEAM1, TEAM2;
    private Optional<Pair<String, Integer>> team_on_a_spree;
    private boolean announced_long_spree_already;
    private final JobKey delayed_announcement_jobkey, deferredRunGameJob;
    //    private final HashMap<String, JobKey> audio_jobs;
    protected boolean count_respawns;

    // Table of Teams in rows, segments in cols
    // cells contain pairs of agent names and FSMs
    //protected final Table<String, Integer, Pair<String, FSM>> spawn_segments1;
    // SPAWN_ROLE, SEGMENT, AGENT, FSM
    protected final ArrayList<Quartet<String, Integer, String, FSM>> spawn_segments;


    //    protected final HashMap<MultiKey<String>, FSM> spawn_segments2;
    protected HashMap<String, Team> team_registry;
    protected final int respawn_timer;
    protected int active_segment;

    public WithRespawns(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);

        final JSONObject spawn_parameters = game_parameters.getJSONObject("spawns");
        this.respawn_timer = spawn_parameters.optInt("respawn_time");
        this.game_lobby = spawn_parameters.optBoolean("game_lobby");
        this.starter_countdown = spawn_parameters.optInt("starter_countdown");
        this.intro_mp3 = spawn_parameters.optString("intro_mp3", "<none>");
        this.pause_mp3 = spawn_parameters.optString("pause_mp3", "in-the-air-tonight");
        this.intro_voice = spawn_parameters.optString("intro_voice", "<none>");
        this.count_respawns = spawn_parameters.optBoolean("count_respawns");

        this.deferredRunGameJob = new JobKey("run_the_game", uuid.toString());
        this.delayed_announcement_jobkey = new JobKey("delayedannounce", uuid.toString());

        //this.spawn_segments = HashBasedTable.create();
        this.spawn_segments = new ArrayList<>();
        active_segment = 0;
        team_registry = new HashMap<>();

        // for every team
        spawn_parameters.getJSONArray("teams").forEach(j -> {
            JSONObject teams = (JSONObject) j;
            final String spawn_role = teams.getString("role");
            final String led = teams.getString("led");
            final String team_name = teams.getString("name");

            team_registry.put(spawn_role, new Team(spawn_role, team_name, led));

            // this is a list of a list of spawn agents
            // every inner list combines all agents for a section
            MutableInt section_number = new MutableInt(-1);
            // for every section
            teams.getJSONArray("agents").forEach(o -> {
                        JSONArray section = (JSONArray) o;
                        section_number.increment();
                        section.forEach(spawn_agent_in_this_section -> {
                            String agent = spawn_agent_in_this_section.toString();
                            FSM fsm = create_Spawn_FSM(agent, spawn_role, led, team_name);
                            spawn_segments.add(
                                    new Quartet<>(
                                            spawn_role,
                                            section_number.intValue(),
                                            agent,
                                            fsm
                                    )
                            );
                            // every spawn is a potential audio device
                            // we ignore a STANDBY state here, as it is only used for start stop signals
                            agents.put(agent, "audio");
                            roles.put("audio", agent);
                        });
                    }
            );
        });

        // announce_sprees only works for two teams RED and BLUE. the game has to make sure
        // that this condition is met
        this.announce_sprees = team_registry.size() == 2 && spawn_parameters.optBoolean("announce_sprees");
        if (announce_sprees) {
            ArrayList<String> t = new ArrayList<>(team_registry.keySet());
            TEAM1 = t.get(0);
            TEAM2 = t.get(1);
        }
    }

    @SneakyThrows
    private FSM create_Spawn_FSM(final String agent, String spawn_role, final String led_device_id, final String teamname) {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/spawn.xml"), null);
//        audio_jobs.put(agent, new JobKey(agent, uuid.toString()));
        fsm.setStatesAfterTransition(_state_PROLOG, (state, obj) -> {
            play("", "", "", agent);
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, led_device_id, MQTT.RECURRING_SCHEME_NORMAL), agent);
            send(MQTT.CMD_DISPLAY, MQTT.merge(
                    MQTT.page("page0",
                            "I am ${agentname}. I will", "be Your spawn.", "You are " + teamname, "!! Standby !!     ${wifi_signal}"),
                    MQTT.page("page1", game_description)), agent
            );
        });
        fsm.setStatesAfterTransition(_state_STANDBY, (state, obj) -> {
            play("", "", "", agent);
            //send(CMD_PLAY, MQTT.toJSON("channel", "all", "subpath", AGENT_MUSIC_PATH, "soundfile", "<none>"), agent);
//            send(CMD_ACOUSTIC, MQTT.toJSON("sir_all", MQTT.OFF), agent);
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
            send(MQTT.CMD_DISPLAY, MQTT.merge(
                    MQTT.page("page0", "", "THIS SPAWN IS", "", "INACTIVE"),
                    MQTT.page("page1", "THIS SPAWN IS", "", "INACTIVE", "")), agent);
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_PREPARING, (state, obj) -> {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.MEDIUM), agent);
            // play("music", AGENT_PAUSE_PATH, pause_mp3, agent);
            send(MQTT.CMD_DISPLAY, MQTT.merge(
                    MQTT.page("page0", " !! GAME LOBBY !! ", "", "Press button", "when ready"),
                    MQTT.page("page1", " !! GAME LOBBY !! ", "", "", "")
            ), agent);
        });
        fsm.setStatesAfterTransition(_state_HURRY_UP, (state, obj) -> {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.TEAM_HURRY_UP_SIGNAL), agent);
            // as this is played only on THIS agent, we won't use the "audio" group here
            // these sounds are TEAM-specific. So each team will hear a different sound
            // "audio" agents are all playing the same - not appropriate here
            //send(CMD_PLAY, MQTT.toJSON("channel", "sound1", "subpath", AGENT_EVENT_PATH, "soundfile", "bell"), agent);
            send(MQTT.CMD_DISPLAY, MQTT.merge(
                    MQTT.page("page0", " !! GAME LOBBY !! ", "Hurry up!", "Press button", "when ready"),
                    MQTT.page("page1", " !! GAME LOBBY !! ", "", "The other Team", "is waiting....")
            ), agent);
        });
        fsm.setStatesAfterTransition(_state_WE_ARE_READY, (state, obj) -> {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.MEDIUM), agent);
            if (spawn_segments.stream()
                    .filter(o -> o.getValue1() == active_segment)
                    .allMatch(o2 -> o2.getValue3().getCurrentState().equals(_state_WE_ARE_READY))
            ) game_fsm.ProcessFSM(_msg_READY); // if ALL teams are ready, the GAME is READY to start

            else {
                send(MQTT.CMD_DISPLAY, MQTT.merge(
                        MQTT.page("page0", " !! GAME LOBBY !! ", "WE ARE READY", "", ""),
                        MQTT.page("page1", " !! GAME LOBBY !! ", "WE ARE READY", "WAITING FOR", "OTHER TEAM")
                ), agent);
                // inform the other spawn agents, that we are ready.
                // this will move those agents to the HURRY_UP state
                spawn_segments.stream()
                        .filter(o -> o.getValue1() == active_segment)
                        // but only those who are not ready yet
                        .filter(o1 -> o1.getValue3().getCurrentState().equals(_state_WE_ARE_PREPARING))
                        .forEach(o2 -> o2.getValue3().ProcessFSM(_msg_ANOTHER_TEAM_IS_READY));
            }
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_START, (state, obj) -> {
            send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.OFF), agent);
            send(MQTT.CMD_TIMERS, MQTT.toJSON("countdown", Integer.toString(starter_countdown)), agent);
            send(MQTT.CMD_DISPLAY, MQTT.page("page0", " The Game starts ", "        in", "       ${countdown}", ""), agent);
        });
        fsm.setStatesAfterTransition(_state_COUNTDOWN_TO_RESUME, (state, obj) -> {
            send(MQTT.CMD_TIMERS, MQTT.toJSON("countdown", Integer.toString(resume_countdown)), agent); // sending to everyone
            send(MQTT.CMD_DISPLAY, MQTT.page("page0", " The Game resumes ", "        in", "       ${countdown}", ""), agent);
        });
        fsm.setStatesAfterTransition(_state_PAUSING, (state, obj) -> send(MQTT.CMD_DISPLAY, MQTT.merge(getSpawnPages(_state_RUNNING), MQTT.page("pause", "", "      PAUSE      ", "", "")), agent));
        fsm.setStatesAfterTransition(_state_EPILOG, (state, obj) -> {
            send(MQTT.CMD_DISPLAY, getSpawnPages(state), agent);
            send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, led_device_id, MQTT.RECURRING_SCHEME_NORMAL), agent);

        });

        fsm.setAction(_state_IN_GAME, _msg_BUTTON_01, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return false;
                on_spawn_button_pressed(spawn_role, agent);
                return true;
            }
        });
        fsm.setAction(_state_IN_GAME, _msg_REINIT, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return false;
                send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, led_device_id, MQTT.RECURRING_SCHEME_NORMAL), agent);
                return true;
            }
        });

        fsm.setAction(_state_STANDBY, _msg_ACTIVATE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                send(MQTT.CMD_VISUAL, new JSONObject()
                                .put("led_all", MQTT.OFF)
                                .put(led_device_id, "fast"),
                        agent);
                send(MQTT.CMD_DISPLAY, getSpawnPages(_state_RUNNING), agent);
                return true;
            }
        });

        fsm.setAction(new ArrayList<>(List.of(_state_WE_ARE_PREPARING, _state_HURRY_UP, _state_WE_ARE_READY, _state_PROLOG, _state_STANDBY, _state_COUNTDOWN_TO_START)), _msg_RUN, new FSMAction() {
            @Override
            public boolean action(String s, String s1, String s2, Object o) {
                send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.OFF), agent);
                send(MQTT.CMD_DISPLAY, getSpawnPages(_state_RUNNING), agent);
                return true;
            }
        });

        fsm.setAction(new ArrayList<>(List.of(_state_PAUSING, _state_COUNTDOWN_TO_RESUME)), _msg_CONTINUE, new FSMAction() {
            @Override
            public boolean action(String s, String s1, String s2, Object o) {
                send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.OFF), agent);
                send(MQTT.CMD_DISPLAY, getSpawnPages(_state_RUNNING), agent);
                return true;
            }
        });

        return fsm;
    }

    @Override
    public void on_run() {
        super.on_run();
        deleteJob(deferredRunGameJob);
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
        if (count_respawns) {
            team_registry.forEach((s, team) -> team.reset_respawns());
            if (announce_sprees) {
                team1_killing_spree_length = 0;
                team2_killing_spree_length = 0;
                team_on_a_spree = Optional.empty();
                announced_long_spree_already = false;
            }
        }
        active_segment = 0;
        deleteJob(deferredRunGameJob);

        send_message_to_all_inactive_segments(_msg_STANDBY);
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
                // central audio handling via role "audio"
                // the intro music and countdown are the same for all players. Hence, the "audio" group
                play("music", AGENT_MUSIC_PATH, intro_mp3);
                play("countdown", AGENT_VOICE_PATH, intro_voice);
                create_job(deferredRunGameJob, LocalDateTime.now().plusSeconds(starter_countdown), RunGameJob.class, Optional.empty());
            } else {
                process_internal_message(_msg_RUN);
            }
        }
        if (state.equals(_state_PAUSING)) send_message_to_agents_in_segment(active_segment, _msg_PAUSE);
    }

    void send_message_to_agents_in_segment(int segment, String message) {
        spawn_segments.stream()
                .filter(o -> o.getValue1() == segment)
                .forEach(o1 -> {
                    log.debug("{} -> {} ", o1.getValue2(), message);
                    o1.getValue3().ProcessFSM(message);
                });
    }

    void send_message_to_agent_in_segment(int segment, String agent, String message) {
        spawn_segments.stream()
                .filter(o -> o.getValue1() == segment)
                .filter(o1 -> o1.getValue2().equalsIgnoreCase(agent))
                .forEach(o2 -> o2.getValue3().ProcessFSM(message));
    }

    void send_message_to_all_agents(final String message) {
        new HashSet<>(spawn_segments)
                .forEach(o -> o.getValue3().ProcessFSM(message));
    }

    void send_message_to_all_inactive_segments(final String message) {
        spawn_segments.stream()
                .filter(o -> o.getValue1() != active_segment)
                .collect(Collectors.toSet())
                .forEach(o1 -> o1.getValue3().ProcessFSM(message));
    }

    /**
     * will progress to the next spawn segment. it cycles through the segments,
     * hence returning to the beginning, when progressing beyond the end of the list.
     * the agents belonging to the current segment will be sent to STANDBY, the
     * now active agents will be activated.
     */
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
    public void process_external_message(String sender, String source, JSONObject message) {
        if (source.equals(_msg_RFID)) {
//            String spawn_role = spawn_segments.column(active_segment).get("");
            get_spawn_role_for(sender, active_segment).ifPresent(spawn_role -> {
                on_spawn_rfid_scanned(sender, team_registry.get(spawn_role), message.getString("uid"));
            });
            return;
        }

        if (source.equalsIgnoreCase(_msg_BUTTON_01)) {
            if (message.getString("button").equalsIgnoreCase("up"))
                send_message_to_agent_in_segment(active_segment, sender, _msg_BUTTON_01);
            return;
        }

        // there is no other class above us which cares about external messages,
        // so we simply consume it
        send_message_to_agent_in_segment(active_segment, sender, source);
    }

    /**
     * override this method to react on button presses on a spawn agent_id
     * the base method automatically counts the number of respawns.
     *
     * @param spawn_role spawn role for this agent_id
     * @param agent_id   guess...
     */
    protected void on_spawn_button_pressed(String spawn_role, String agent_id) {
        if (!count_respawns) return;

        Team this_team = team_registry.get(spawn_role);
        this_team.add_respawn(1);
        add_in_game_event(new JSONObject()
                .put("item", "respawn").put("agent_id", agent_id)
                .put("team", this_team.getLed_device_id())
                .put("value", this_team.getRespawns())
        );
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.MEDIUM), agent_id);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.WHITE, MQTT.MEDIUM), agent_id);

        log.trace(this_team);

        if (!announce_sprees) return;
        // this is a special case only for two teams.
        if (spawn_role.equals(TEAM1)) {
            // a respawn here resets the spree of the other tea
            team1_killing_spree_length++;
            team2_killing_spree_length = 0;
            if (team1_killing_spree_length == 1) announced_long_spree_already = false;
        }
        if (spawn_role.equals(TEAM2)) {
            team1_killing_spree_length = 0;
            team2_killing_spree_length++;
            if (team2_killing_spree_length == 1) announced_long_spree_already = false;
        }
        first_blood_announcement();
        prepare_spree_announcement();
    }


    /**
     * override to react on a rfid tags that have been scanned at a spawn agent. If the game class
     * itself needs to handle rfid events, this should be done in the
     * class' overriden process_external_message() method
     *
     * @param agent
     * @param uid
     */
    protected void on_spawn_rfid_scanned(String agent, Team team, String uid) {
        log.debug("scanned tag {} at {} for Team {}", uid, agent, get_spawn_role_for(agent, active_segment).orElse("not assigned"));
    }

    private void prepare_spree_announcement() {
        team_on_a_spree = Optional.empty();
        deleteJob(delayed_announcement_jobkey);
        if (team1_killing_spree_length > 1)
            team_on_a_spree = Optional.of(new ImmutablePair<>(TEAM1, team1_killing_spree_length));
        if (team2_killing_spree_length > 1)
            team_on_a_spree = Optional.of(new ImmutablePair<>(TEAM2, team2_killing_spree_length));
        team_on_a_spree.ifPresent(stringIntegerPair -> {
            if (!announced_long_spree_already)
                create_job(delayed_announcement_jobkey, LocalDateTime.now().plusSeconds(3), DelayedAudioJob.class, Optional.empty());
        });
    }

    // todo: this delayed_reaction could conflict with deriving classes
    @Override
    public void play_later(JobDataMap map) {
        // spree announcement when necessary
        team_on_a_spree.ifPresent(stringIntegerPair -> {
            int spree = Math.min(stringIntegerPair.getRight(), ENEMY_SPREE_ANNOUNCEMENTS.length + 1);
            String team = stringIntegerPair.getLeft();
            String enemy = get_opposing_team(team);

            log.debug("spree {}", spree);
            log.debug("{}({}) says {}", team, get_active_spawn_agents(team), ENEMY_SPREE_ANNOUNCEMENTS[spree - 2]);
            log.debug("{}({}) says {}", enemy, get_active_spawn_agents(enemy), SPREE_ANNOUNCEMENTS[spree - 2]);

            play("voice1", AGENT_EVENT_PATH, ENEMY_SPREE_ANNOUNCEMENTS[spree - 2], get_active_spawn_agents(team));
            play("voice1", AGENT_EVENT_PATH, SPREE_ANNOUNCEMENTS[spree - 2], get_active_spawn_agents(enemy));

            announced_long_spree_already = spree >= 6;
        });
    }

    private void first_blood_announcement() {
        // if the sum of all respawns equals 1 then this must be the first one
        if (team_registry.values().stream().mapToInt(Team::getRespawns).sum() == 1) {
            play("voice2", AGENT_EVENT_PATH, "firstblood");
            log.debug("Announcing FIRST BLOOD");
        }
    }

    protected void assert_two_teams_red_and_blue() throws JSONException {
        if (team_registry.size() != 2) throw new JSONException("we need exactly 2 teams");
        if (spawn_segments.stream().noneMatch(objects -> objects.getValue0().equalsIgnoreCase("red_spawn")))
            throw new JSONException("red_spawn missing");
        if (spawn_segments.stream().noneMatch(objects -> objects.getValue0().equalsIgnoreCase("blue_spawn")))
            throw new JSONException("blue_spawn");
    }

    @Override
    public JSONObject getState() {
        JSONObject json = super.getState();
        json.getJSONObject("played")
                .put("game_lobby", game_lobby)
                .put("starter_countdown", starter_countdown)
                .put("active_segment", active_segment);

        JSONObject played = json.getJSONObject("played");
        spawn_segments.stream()
                .filter(objects -> objects.getValue1().equals(active_segment))
                .forEach(objects -> played.getJSONObject("agent_states")
                        .put(objects.getValue2(), objects.getValue3().getCurrentState()));

        team_registry.forEach((s, team) -> played.put(team.getLed_device_id() + "_respawns", team.getRespawns()));
        json.put("played", played);
        return json;
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        if (count_respawns)
            team_registry.forEach((s, team) -> model.addAttribute(team.getColor() + "_respawns", team.getRespawns()));

    }

    protected Collection<String> get_active_spawn_agents() {
        return spawn_segments.stream()
                .filter(o -> o.getValue1() == active_segment)
                .map(Quartet::getValue2)
                .collect(Collectors.toSet());
    }

    protected Collection<String> get_all_spawn_agents() {
        return spawn_segments.stream()
                .map(Quartet::getValue2)
                .collect(Collectors.toSet());
    }

    protected Optional<String> get_spawn_role_for(final String agent, final int segment) {
        List<Quartet<String, Integer, String, FSM>> list = spawn_segments.stream()
                .filter(o -> o.getValue1() == segment)
                .filter(o1 -> o1.getValue2().equalsIgnoreCase(agent))
                .toList();
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(0).getValue0());
    }

    private Collection<String> get_active_spawn_agents(String team) {
        return spawn_segments.stream()
                .filter(o -> o.getValue0().equalsIgnoreCase(team))
                .map(Quartet::getValue2)
                .collect(Collectors.toSet());
    }

    private String get_opposing_team(String spawn) {
        return spawn.equals(TEAM1) ? TEAM2 : TEAM1;
    }

    private Optional<String> get_team_for(String agent) {
        List<String> result_list = spawn_segments.stream()
                .filter(o -> o.getValue1() == active_segment && o.getValue2().equalsIgnoreCase(agent))
                .map(Quartet::getValue0)
                .toList();
        return result_list.isEmpty() ? Optional.empty() : Optional.of(result_list.get(0));
    }

    protected String get_spawn_node_for(String spawn_role) {
        return get_spawn_node_for(spawn_role, 0);
    }

    protected String get_spawn_node_for(String spawn_role, int segment) {
        return spawn_segments.stream()
                .filter(o -> o.getValue0().equalsIgnoreCase(spawn_role))
                .filter(o1 -> o1.getValue1() == segment)
                .map(Quartet::getValue2)
                .toList().get(0); // can only be one - ensure by throwing an exception in the constructor
    }

}
