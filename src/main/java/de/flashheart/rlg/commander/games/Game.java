package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.github.lalyos.jfiglet.FigletFont;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.events.StateReachedEvent;
import de.flashheart.rlg.commander.games.events.StateReachedListener;
import de.flashheart.rlg.commander.games.events.StateTransitionEvent;
import de.flashheart.rlg.commander.games.events.StateTransitionListener;
import de.flashheart.rlg.commander.misc.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.javatuples.Quartet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static de.flashheart.rlg.commander.controller.MQTT.CMD_PLAY;

@Log4j2
public abstract class Game {
    public static final String _msg_RESET = "reset";
    public static final String _msg_PREPARE = "prepare";
    public static final String _msg_READY = "ready";
    public static final String _msg_ADMIN = "admin";
    public static final String _msg_BUTTON_01 = "btn01";
    public static final String _msg_BUTTON_02 = "btn02";
    public static final String _msg_RFID = "rfid";
    public static final String _msg_RUN = "run";
    public static final String _msg_IN_GAME_EVENT_OCCURRED = "in_game_event_occurred";
    public static final String _msg_PAUSE = "pause";
    public static final String _msg_RESUME = "resume";
    public static final String _msg_CONTINUE = "continue";
    public static final String _msg_GAME_OVER = "game_over";
    public static final String _state_PROLOG = "PROLOG";
    public static final String _state_EMPTY = "EMPTY";
    public static final String _state_TEAMS_NOT_READY = "TEAMS_NOT_READY";
    public static final String _state_TEAMS_READY = "TEAMS_READY";
    public static final String _state_RUNNING = "RUNNING";
    public static final String _state_PAUSING = "PAUSING";
    public static final String _state_RESUMING = "RESUMING";
    public static final String _state_EPILOG = "EPILOG";
    public static final String OUT_LED_WHITE = MQTT.WHITE;
    public static final String OUT_LED_RED = MQTT.RED;
    public static final String OUT_LED_BLUE = MQTT.BLUE;
    public static final String OUT_LED_YELLOW = MQTT.YELLOW;
    public static final String OUT_LED_GREEN = MQTT.GREEN;
    public static final String _flag_state_NEUTRAL = "NEUTRAL";
    public static final String _flag_state_PROLOG = _state_PROLOG;
    public static final String _flag_state_RED = "RED";
    public static final String _flag_state_BLUE = "BLUE";
    public static final String _flag_state_YELLOW = "YELLOW";
    public static final String _flag_state_GREEN = "GREEN";
    public static final String[] OUT_ALL_LEDS = new String[]{OUT_LED_WHITE, OUT_LED_RED, OUT_LED_BLUE, OUT_LED_YELLOW, OUT_LED_GREEN};
    public static final List<String> _flag_ALL_RUNNING_STATES = List.of(_flag_state_RED, _flag_state_YELLOW, _flag_state_GREEN, _flag_state_BLUE);
    public static final List<String> ALL_LEDS = List.of(OUT_LED_WHITE, OUT_LED_RED, OUT_LED_YELLOW, OUT_LED_GREEN, OUT_LED_BLUE);
    public static final List<String> _state_ALL_STATES = List.of(_state_PROLOG, _state_TEAMS_NOT_READY, _state_TEAMS_READY, _state_RESUMING, _state_PAUSING, _state_RUNNING, _state_EPILOG);
    private List<StateTransitionListener> stateTransitionListeners = new ArrayList<>();
    private List<StateReachedListener> stateReachedListeners = new ArrayList<>();
    public static final String AGENT_MUSIC_PATH = "music";
    public static final String AGENT_VOICE_PATH = "countdown";
    public static final String AGENT_EVENT_PATH = "events";
    public static final String AGENT_PAUSE_PATH = "pause";

    // all message must be sent via THIS base.html classes send method to implement "SILENT_GAME"
    private final MQTTOutbound mqttOutbound;
    private final boolean silent_game;

    // should be overwritten by the game class to describe the mode and the parameters currently in use
    // can be displayed on the LCDs
    protected final ArrayList<String> game_description;

    @Getter
    private final JSONObject game_parameters;
    protected final UUID uuid;
    protected final Scheduler scheduler;
    @Getter
    protected final Multimap<String, String> agents, roles;
    protected final HashMap<String, String> map_flag_state_to_led_color;

    // what happened, and when ?
    protected final List<JSONObject> in_game_events;
    // main FSM to control the basic states of every game
    protected final FSM game_fsm;
    protected final Map<String, FSM> cpFSMs;
    private LocalDateTime game_init_at;

    Game(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws GameSetupException, ParserConfigurationException, IOException, SAXException, JSONException {
        log.info("\n{}", FigletFont.convertOneLine(getGameMode()));
        uuid = UUID.randomUUID();
        game_init_at = LocalDateTime.now();
        this.game_parameters = game_parameters;
        this.scheduler = scheduler;
        this.mqttOutbound = mqttOutbound;
        this.agents = HashMultimap.create();
        this.roles = HashMultimap.create();
        this.in_game_events = new ArrayList<>();
        this.silent_game = game_parameters.optBoolean("silent_game");
        this.cpFSMs = new HashMap<>();
        this.map_flag_state_to_led_color = new HashMap<>();

        JSONObject agts = game_parameters.getJSONObject("agents");
        Set<String> rls = agts.keySet();
        rls.forEach(role ->
                agts.getJSONArray(role).forEach(agent -> {
                            agents.put(agent.toString(), role);
                            roles.put(role, agent.toString());
                        }
                )
        );
        this.game_fsm = createFSM();
        this.game_description = new ArrayList<>();

        send("paged", MQTT.page("page0",
                "I am ${agentname}", "", "", "I am a Siren"), roles.get("sirens")
        );

        // generic creation of capture_points - if needed
        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        send("paged", MQTT.page("page0",
                "I am ${agentname}", "", "I will be a", "Capture Point"), roles.get("capture_points"));

    }

    protected void add_in_game_event(JSONObject in_game_event, String type) {
        in_game_event.put("type", type);
        in_game_events.add(new JSONObject()
                .put("pit", JavaTimeConverter.to_iso8601())
                .put("event", in_game_event)
                .put("new_state", game_fsm.getCurrentState())
        );
        if (game_fsm.getCurrentState().equals(_state_RUNNING)) process_internal_message(_msg_IN_GAME_EVENT_OCCURRED);
    }

    protected void add_in_game_event(JSONObject in_game_event) {
        add_in_game_event(in_game_event, "in_game_state_change");
    }

    /**
     * adds a listener for state transitions
     *
     * @param toAdd
     */
    public void addStateTransistionListener(StateTransitionListener toAdd) {
        stateTransitionListeners.add(toAdd);
    }

    /**
     * adds a listener when reaching a new state
     *
     * @param toAdd
     */
    public void addStateReachedListener(StateReachedListener toAdd) {
        stateReachedListeners.add(toAdd);
    }

    /**
     * if we have a game mode WITHOUT capture points - simply don't use the "capture_points" key in the game_params
     * json
     *
     * @param agent
     * @return
     */
    public abstract FSM create_CP_FSM(String agent);

    private void fireStateTransition(StateTransitionEvent event) {
        stateTransitionListeners.forEach(stateTransitionListener -> stateTransitionListener.onStateTransition(event));
        log.debug("transition {}:{} == > {}", event.getOldState(), event.getMessage(), event.getNewState());
        if (!event.getMessage().equals(_msg_IN_GAME_EVENT_OCCURRED)) {
            in_game_events.add(new JSONObject()
                    .put("pit", JavaTimeConverter.to_iso8601())
                    .put("event", new JSONObject().put("type", "general_game_state_change").put("message", event.getMessage()))
                    .put("new_state", event.getNewState()));
        }
//            addEvent(event.getMessage(), event.getNewState());
        on_transition(event.getOldState(), event.getMessage(), event.getNewState());
    }

    private void fireStateReached(StateReachedEvent event) {
        stateReachedListeners.forEach(stateReachedListener -> stateReachedListener.onStateReached(event));
        log.debug("new state {}", event.getState());
        at_state(event.getState());
    }

    /**
     * called whenever a new state of the game_fsm is reached.
     *
     * @param state the reached state
     */
    protected void at_state(String state) {
    }

    /**
     * called on a new transistion within the game_fsm
     *
     * @param old_state before the transition
     * @param message   message triggering the transition
     * @param new_state after the transition
     */
    protected void on_transition(String old_state, String message, String new_state) {
        if (message.equals(_msg_RUN)) on_run();
        if (message.equals(_msg_GAME_OVER)) on_game_over();
        if (message.equals(_msg_RESET)) on_reset();
    }

    /**
     * sandwich method to implement silent game function
     *
     * @param cmd
     * @param payload
     * @param agent
     */
    protected void send(String cmd, JSONObject payload, String agent) {
        if (silent_game && cmd.equalsIgnoreCase("acoustic")) return;
        mqttOutbound.send(cmd, payload, agent);
    }

    protected void send(String cmd, JSONObject payload, Collection<String> agents) {
        if (silent_game && cmd.equalsIgnoreCase("acoustic")) return;
        mqttOutbound.send(cmd, payload, agents);
    }


    protected void play(String channel, String subpath, String soundfile, String agent) {
        play(channel, subpath, soundfile, List.of(agent));
    }

    protected void play(String channel, String subpath, String soundfile) {
        play(channel, subpath, soundfile, roles.get("audio"));
    }

    protected void play(String channel, String subpath, String soundfile, Collection<String> agents) {
        send(CMD_PLAY, MQTT.toJSON("channel", channel, "subpath", subpath, "soundfile", soundfile), agents);
    }

    /**
     * dummy method. will be notified if a REST request is sent to games/admin.
     *
     * @param params contains the details about the admin operation
     * @throws IllegalStateException
     */
    public void zeus(JSONObject params) throws IllegalStateException {
        log.warn("no zeus function implemented. ignoring.");
    }

    public boolean hasZeus() {
        return false;
    }

    public void process_internal_message(String message) throws IllegalStateException {
        if (game_fsm.ProcessFSM(message) == null) {
            throw new IllegalStateException(String.format("message not processed. no transition in FSM from %s on message %s.", game_fsm.getCurrentState(), message));
        }
    }

    /**
     * when something happens on the field (a button or a scanned tag), we need to react on it.
     * Implement this method to tell us, WHAT we should do. these
     * messages are usually sent from outside the Game hierarchy. A sender can also be the quartz scheduler.
     *
     * @param agent_id of the message's source
     * @param source   currently btn01, _timed_respawn_, rfid
     * @param message  the payload of that message. Button up or down, UID of the tag...
     * @throws IllegalStateException an event is not important or doesn't make any sense an ISE is thrown
     */
    public abstract void on_external_message(String agent_id, String source, JSONObject message);

    public void on_reset() {
        game_init_at = LocalDateTime.now();
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
        //play("","","");
//        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agents.keySet());
//        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agents.keySet());
        send(MQTT.CMD_TIMERS, MQTT.toJSON("_clearall", ""), agents.keySet());
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.SIR_ALL, MQTT.OFF), roles.get("sirens"));
    }

    public void on_run() {
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR1, MQTT.GAME_STARTS), roles.get("sirens"));
    }

    public void on_game_over() {
        cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_GAME_OVER));
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF, MQTT.SIR1, MQTT.GAME_ENDS), roles.get("sirens"));
        //log.info(getState().toString(4));
    }

    /**
     * mainly boilerplate code to create the game_fsm
     *
     * @return the created FSM
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    private FSM createFSM() throws ParserConfigurationException, IOException, SAXException {
        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/game.xml"), null);

        // Transitions
        fsm.setAction(_msg_RESET, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                in_game_events.clear();
                fireStateTransition(new StateTransitionEvent(curState, _msg_RESET, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_RUN, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_RUN, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_IN_GAME_EVENT_OCCURRED, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_IN_GAME_EVENT_OCCURRED, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_READY, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_READY, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_PREPARE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_PREPARE, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_GAME_OVER, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_GAME_OVER, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_PAUSE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_PAUSE, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_RESUME, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_RESUME, nextState));
                return true;
            }
        });
        fsm.setAction(_msg_CONTINUE, new FSMAction() {
            @Override
            public boolean action(String curState, String message, String nextState, Object args) {
                fireStateTransition(new StateTransitionEvent(curState, _msg_CONTINUE, nextState));
                return true;
            }
        });
        fsm.setStatesAfterTransition(new ArrayList<>(_state_ALL_STATES), (state, obj) -> {
            fireStateReached(new StateReachedEvent(state));
        });

        return fsm;
    }


    /**
     * called whenever cleanup() is called from outside
     */
    protected void on_cleanup() {
        stateReachedListeners.clear();
        stateTransitionListeners.clear();
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), roles.get("sirens"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agents.keySet());
        send(MQTT.CMD_TIMERS, MQTT.toJSON("_clearall", ""), agents.keySet());
        play("", "", "");
    }

    String get_in_game_event_description(JSONObject event) {
        String type = event.getString("type");
        if (type.equalsIgnoreCase("general_game_state_change")) {
            return event.getString("message");
        }
        if (type.equalsIgnoreCase("free_text")) {
            return event.getString("message");
        }
        if (event.getString("item").equals("respawn")) {
            return "Respawn Team " + event.getString("team") + ": #" + event.getInt("value");
        }
        String zeus = (event.has("zeus") ? " (by the hand of ZEUS)" : "");
        if (type.equalsIgnoreCase("in_game_state_change")) {
            if (event.getString("item").equals("capture_point")) {
                return event.getString("agent") + " => " + event.getString("state") + zeus;
            }
        }
        if (event.getString("item").equals("add_respawns")) {
            String text = event.getLong("amount") >= 0 ? ": %d respawns have been added" : ": %d respawns have been removed";
            return "Team " + event.getString("team") + String.format(text, Math.abs(event.getLong("amount")))
                    + zeus;
        }

        return "";
    }

    public void fill_thymeleaf_model(Model model) {
        model.addAttribute("comment", game_parameters.getString("comment"));
        final ArrayList<Quartet<String, String, String, LocalDateTime>> events = new ArrayList<>();
        // toArray to avoid concurrent modification exception when things get busy.
        Arrays.stream(in_game_events.toArray()).forEach(array_element -> {
            JSONObject event_object = (JSONObject) array_element;
            events.add(new Quartet<>(
                    JavaTimeConverter.from_iso8601(event_object.get("pit").toString()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
                    get_in_game_event_description(event_object.getJSONObject("event")),
                    event_object.getString("new_state"),
                    JavaTimeConverter.from_iso8601(event_object.get("pit").toString())
            ));
        });
        model.addAttribute("events", events.stream()
                .sorted((o1, o2) -> o1.getValue3().compareTo(o2.getValue3()) * -1)
                .collect(Collectors.toList()));
        model.addAttribute("game_fsm_current_state", game_fsm_get_current_state());
        model.addAttribute("has_zeus", hasZeus());
        model.addAttribute("game_mode", getGameMode());
        model.addAttribute("game_init_at", JavaTimeConverter.to_iso8601(game_init_at));
        model.addAttribute("cps", new JSONArray(cpFSMs.keySet().stream().sorted(String::compareTo).collect(Collectors.toList())));
        //game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(o -> o.toString()).sorted().collect(Collectors.toList()));
    }

    public String game_fsm_get_current_state(){
        return game_fsm.getCurrentState();
    }

    /**
     * call this to initiate the cleanup process before You destroy a loaded game.
     */
    public void cleanup() {
        on_cleanup();
    }

    public abstract String getGameMode();

    /**
     * returns a JSON Object which describes the current game situation.
     *
     * @return status information to be sent if we are asked for it
     */
    public JSONObject getState() {
        JSONObject agent_states = new JSONObject();
        cpFSMs.forEach((agentid, fsm) -> agent_states.put(agentid, fsm.getCurrentState()));
        JSONObject json = new JSONObject()
                .put("setup", game_parameters)
                .put("played", new JSONObject()
                        .put("game_fsm_current_state", game_fsm_get_current_state())
                        .put("in_game_events", new JSONArray(in_game_events))
                        .put("game_init_at", JavaTimeConverter.to_iso8601(game_init_at))
                        .put("agent_states", agent_states)
                );
        return json;
    }

    /**
     * if the commander sends out a screen for the lcd display to show the current set game parameters it will use the
     * data set by this method.
     *
     * @param lines line by line description as we want it to be broadcasted
     */
    public void setGameDescription(String... lines) {
        game_description.clear();
        game_description.addAll(Arrays.asList(lines));
    }


    /**
     * pretty self-explanatory
     *
     * @param agent
     * @param role
     * @return
     */
    public boolean hasRole(String agent, String role) {
        return agents.get(agent).contains(role);
    }

    /**
     * this method enables the game class to have an own signal macro scheme in the game_description.
     * yet unused. see meshed.json for example
     *
     * @param key
     * @return
     */
    protected String get_signal(String key) {
        return get_signal(key, MQTT.RECURRING_SCHEME_NORMAL);
    }

    protected String get_signal(String key, String def) {
        return game_parameters.optJSONObject("signals", new JSONObject()).optString(key, def);
    }

    protected JSONObject getSpawnPages(String state) {
        return MQTT.page("page0", game_description);
    }


}
