package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.*;
import de.flashheart.rlg.commander.games.traits.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Log4j2
public class Hardpoint extends WithRespawns implements HasDelayedReaction, HasScoreBroadcast, HasActivation, HasTimeOut, HasFlagTimer {
    public static final String _msg_TO_NEUTRAL = "to_neutral";
    public static final String _msg_DEACTIVATE = "deactivate";
    public static final String _msg_GO = "go";
    public static final String _msg_NEXT_FLAG = "next_flag";
    public static final String _flag_state_NEUTRAL = "NEUTRAL";
    public static final String _flag_state_RED = "RED";
    public static final String _flag_state_BLUE = "BLUE";
    private static final String _flag_state_GET_READY = "GET_READY";
    private static final long SIREN_DELAY_AFTER_COLOR_CHANGE = 1L;
    private final BigDecimal SCORE_CALCULATION_EVERY_N_SECONDS;
    private final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES;
    private final long REPEAT_EVERY_MS;
    private long broadcast_cycle_counter;
    private final long winning_score, flag_time_up, flag_time_out, delay_until_next_flag;
    private final List<String> capture_points;
    private final Table<String, String, Long> scores;
    private final JobKey broadcastScoreJobkey, flag_activation_jobkey, delayed_reaction_jobkey, flag_time_up_jobkey, flag_time_out_jobkey;
    private long last_job_broadcast;
    private int active_capture_point;
    private final boolean random_flag_selection;
    private final CircularFifoQueue<String> recently_activated_flags;
    private final Random random = new Random(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));

    public Hardpoint(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);

        BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = Long.parseLong(game_parameters.optString("BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES", "10"));
        SCORE_CALCULATION_EVERY_N_SECONDS = new BigDecimal(game_parameters.optString("SCORE_CALCULATION_EVERY_N_SECONDS","0.5"));
        REPEAT_EVERY_MS = SCORE_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000L)).longValue();

        log.info("\n    __  __               __            _       __\n" +
                "   / / / /___ __________/ /___  ____  (_)___  / /_\n" +
                "  / /_/ / __ `/ ___/ __  / __ \\/ __ \\/ / __ \\/ __/\n" +
                " / __  / /_/ / /  / /_/ / /_/ / /_/ / / / / / /_\n" +
                "/_/ /_/\\__,_/_/   \\__,_/ .___/\\____/_/_/ /_/\\__/\n" +
                "                      /_/");

        count_respawns = false;

        this.winning_score = game_parameters.optLong("winning_score", 250);
        this.flag_time_out = game_parameters.optLong("flag_time_out", 120);
        this.flag_time_up = game_parameters.optLong("flag_time_up", 120);
        this.delay_until_next_flag = game_parameters.optLong("delay_until_next_flag", 30);

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(Object::toString).collect(Collectors.toList());

        // random makes no sense with less than 4 flags
        this.random_flag_selection = capture_points.size() > 3 && game_parameters.optBoolean("random_flag_selection", false);

        scores = HashBasedTable.create();
        recently_activated_flags = new CircularFifoQueue<>(2);

        broadcastScoreJobkey = new JobKey("broadcast_score", uuid.toString());
        flag_activation_jobkey = new JobKey("flag_activation", uuid.toString());
        flag_time_up_jobkey = new JobKey("flag_time_up", uuid.toString());
        flag_time_out_jobkey = new JobKey("flag_time_out", uuid.toString());
        delayed_reaction_jobkey = new JobKey("delayed_reaction", uuid.toString());

        jobs_to_suspend_during_pause.add(broadcastScoreJobkey);
        jobs_to_suspend_during_pause.add(flag_activation_jobkey);
        jobs_to_suspend_during_pause.add(flag_time_up_jobkey);
        jobs_to_suspend_during_pause.add(flag_time_out_jobkey);
        jobs_to_suspend_during_pause.add(delayed_reaction_jobkey);

        String last_line_in_description = "";
        if (delay_until_next_flag > 0L) last_line_in_description = String.format("Delay %s", delay_until_next_flag);
        if (random_flag_selection) last_line_in_description += " Random";
        setGameDescription(
                game_parameters.getString("comment"),
                String.format("Winning@ %s", winning_score),
                String.format("T_OUT/UP %s/%s", flag_time_out, flag_time_up),
                last_line_in_description
        );
    }


    @Override
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/hardpoint.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_prolog(agent));
            fsm.setStatesAfterTransition("GET_READY", (state, obj) -> cp_get_ready(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("STAND_BY", (state, obj) -> cp_to_stand_by(agent));
            fsm.setStatesAfterTransition("AFTER_FLAG_TIME_IS_UP", (state, obj) -> next_flag());
            fsm.setStatesAfterTransition(new ArrayList<>(Arrays.asList(_flag_state_RED, _flag_state_BLUE)), (state, obj) ->
                    cp_to_color(agent, StringUtils.left(state.toLowerCase(), 3))
            );
            // after a neutral flag has been activated for the first time
            fsm.setAction(_flag_state_NEUTRAL, _msg_BUTTON_01, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    deleteJob(flag_time_out_jobkey);
                    create_job(flag_time_up_jobkey, LocalDateTime.now().plusSeconds(flag_time_up), FlagTimerJob.class, Optional.empty());
                    send("timers", MQTT.toJSON("timer", Long.toString(flag_time_up)), agents.keySet());
                    send("vars", MQTT.toJSON("timelabel", "Next Flag in"), agents.keySet());
                    return true;
                }
            });

            fsm.setAction(_msg_NEXT_FLAG, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    if (delay_until_next_flag > 0L)
                        send("acoustic", MQTT.toJSON(MQTT.SHUTDOWN_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
                    return true;
                }
            });
            fsm.setAction(_flag_state_GET_READY, _msg_GO, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    send("acoustic", MQTT.toJSON(MQTT.EVENT_SIREN, MQTT.SCHEME_LONG), roles.get("sirens"));
                    return true;
                }
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_prolog(String agent) {
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "PROLOG"),
                agent);
    }

    private void next_flag() {
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_NEXT_FLAG);
        //create_job(delayed_reaction_jobkey, LocalDateTime.now().plusSeconds(10), DelayedReactionJob.class, Optional.empty());

        if (random_flag_selection) {
            log.debug("recently activated flags before {}", recently_activated_flags);
            recently_activated_flags.add(get_active_flag());
            log.debug("recently activated flags no with me {}", recently_activated_flags);
            List<String> not_recently_used = new ArrayList<>(capture_points);
            not_recently_used.removeAll(recently_activated_flags);
            log.debug("not recently used {}", not_recently_used);
            int random_position = random.nextInt(not_recently_used.size());
            log.debug("random position {}", random_position);
            String random_flag = not_recently_used.get(random_position);
            log.debug("fresh random flag {}", random_flag);
            active_capture_point = capture_points.indexOf(random_flag);
            log.debug("original list {}", capture_points);
            log.debug("new flag found on position {}", active_capture_point);
        } else {
            active_capture_point++;
            if (active_capture_point >= capture_points.size()) active_capture_point = 0;
        }

        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_PREPARE);
    }

    @Override
    public void flag_time_is_up(String bombid) {
        log.debug("flag_time_is_up({})", get_active_flag());
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_FLAG_TIME_IS_UP);
    }

    private void cp_get_ready(String agent) {
        log.debug("getting ready ({})", get_active_flag());
        create_job(flag_activation_jobkey, LocalDateTime.now().plusSeconds(delay_until_next_flag), ActivationJob.class, Optional.empty());
        send("timers", MQTT.toJSON("timer", Long.toString(delay_until_next_flag)), agents.keySet());
        send("vars", MQTT.toJSON("timelabel", "Next READY in"), agents.keySet());
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is preparing"),
                agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, "infty:on,75;off,75;on,75;off,2500"), agent); // short flashes
    }

    @Override
    public void activate(JobDataMap map) {
        log.debug("go({})", get_active_flag());
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_GO);
    }

    @Override
    public String getGameMode() {
        return "hardpoint";
    }

    private void cp_to_stand_by(String agent) {
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag standing by"),
                agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent);
    }

    private String peek_next_flag() {
        if (random_flag_selection) return "???";
        int peek = active_capture_point + 1 >= capture_points.size() ? 0 : active_capture_point + 1;
        return capture_points.get(peek);
    }

    private String get_active_flag() {
        return capture_points.get(active_capture_point);
    }

    private void cp_to_neutral(String agent) {
        // time limit - if nobody can touch the flag
        deleteJob(flag_time_up_jobkey); // if it was by zeus
        create_job(flag_time_out_jobkey, LocalDateTime.now().plusSeconds(flag_time_out), TimeOutJob.class, Optional.empty());
        send("timers", MQTT.toJSON("timer", Long.toString(flag_time_out)), agents.keySet());
        send("vars", MQTT.toJSON("timelabel", "Next Flag in", "label", "actv:" + get_active_flag() + " next:" + peek_next_flag()), agents.keySet());

        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is NEUTRAL"),
                agent);
        send("visual", MQTT.toJSON(MQTT.ALL, "off", MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "NEUTRAL"));
    }


    @Override
    public void timeout(JobDataMap map) {
        log.debug("flag has timed out. nobody made it to the point. next flag please.");
        next_flag();
    }

    private void cp_to_color(String agent, String color) {
        String COLOR = (color.equalsIgnoreCase("blu") ? "BLUE" : "RED");
        deleteJob(flag_time_out_jobkey);
        create_job(delayed_reaction_jobkey, LocalDateTime.now().plusSeconds(SIREN_DELAY_AFTER_COLOR_CHANGE), DelayedReactionJob.class, Optional.empty());
        send("visual", MQTT.toJSON(MQTT.ALL, "off", color, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send("acoustic", MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is " + COLOR),
                agent);
        addEvent(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", COLOR));
    }

    @Override
    public void delayed_reaction(JobDataMap map) {
//        String agent = get_active_flag();
//        if (cpFSMs.get(agent).getCurrentState().equals(_flag_state_GET_READY)) {
//            long rep = delay_until_next_flag - 10 / 5L - 1L;
//            send("acoustic", MQTT.toJSON(MQTT.EVENT_SIREN, rep + ":on,150;off,50;on,150;off,50;on,150;off,4400"), roles.get("sirens"));
//        } else // so the siren does not wail to often when players are switching the colors back and forth
        send("acoustic", MQTT.toJSON(MQTT.ALERT_SIREN, MQTT.SCHEME_MEDIUM), roles.get("sirens"));
    }

    private void reset_score_table() {
        Lists.newArrayList("blue", "red").forEach(color -> {
            capture_points.forEach(agent -> scores.put(agent, color, 0L));
            scores.put("all", color, 0L);
        });
    }

    @Override
    public void on_reset() {
        super.on_reset();
        delete_all_jobs();

        broadcast_cycle_counter = 0L;
        last_job_broadcast = 0L;
        active_capture_point = random_flag_selection ? random.nextInt(capture_points.size()) : 0;
        recently_activated_flags.clear();
        reset_score_table();
        broadcast_score();
    }

    private void delete_all_jobs() {
        deleteJob(broadcastScoreJobkey);
        deleteJob(flag_activation_jobkey);
        deleteJob(flag_time_up_jobkey);
        deleteJob(flag_time_out_jobkey);
        deleteJob(delayed_reaction_jobkey);
    }

    @Override
    public void on_run() {
        super.on_run();
        create_job(broadcastScoreJobkey, simpleSchedule().withIntervalInMilliseconds(REPEAT_EVERY_MS).repeatForever(), BroadcastScoreJob.class);
        broadcast_cycle_counter = 0L;
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_GO); // the first flag always starts immediately
    }

    @Override
    public void on_game_over() {
        super.on_game_over();
        broadcast_score(); // one last time
        delete_all_jobs();
    }

    @Override
    public void broadcast_score() {
        final long now = ZonedDateTime.now().toInstant().toEpochMilli();
        final long time_to_add = now - last_job_broadcast;
        last_job_broadcast = now;

        if (game_fsm.getCurrentState().equals(_state_RUNNING))
            cpFSMs.forEach((key, value) -> add_score_for(key, value.getCurrentState(), time_to_add));

        broadcast_cycle_counter++;

        if (!game_fsm.getCurrentState().equals(_state_RUNNING) || broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0) {
            send("vars", get_vars(), agents.keySet());
        }

        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            long score_blue = scores.get("all", "blue");
            long score_red = scores.get("all", "red");
            send("vars", new JSONObject()
                    .put("winner", score_blue > score_red ? "BlueFor" : "RedFor"), agents.keySet());
        }

    }

    private JSONObject get_vars() {
        //String agent = get_active_flag();
        //String state = cpFSMs.get(agent).getCurrentState();

        return new JSONObject()
                .put("score_blue", scores.get("all", "blue") / 1000L)
                .put("score_red", scores.get("all", "red") / 1000L);
    }

    private void add_score_for(String agent, String current_state, long score) {
        String state = current_state.toLowerCase();
        if (!state.toLowerCase().matches("red|blue")) return;
        // replace cell values
        scores.put(agent, state, scores.get(agent, state) + score); // replace cell value
        scores.put("all", state, scores.get("all", state) + score);

        if (scores.get("all", "red") >= winning_score * 1000L || scores.get("all", "blue") >= winning_score * 1000L)
            game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    public void process_external_message(String sender, String source, JSONObject message) {
        if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
        if (!message.getString("button").equalsIgnoreCase("up")) return;

        if (game_fsm.getCurrentState().equals(_state_RUNNING) && get_active_flag().equalsIgnoreCase(sender))
            cpFSMs.get(sender).ProcessFSM(source.toLowerCase());
        else
            super.process_external_message(sender, _msg_RESPAWN_SIGNAL, message);
    }

    @Override
    protected void on_respawn_signal_received(String role, String agent) {
        // no respawns in Hardpoint
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0",
                    "Game Over",
                    "Winner: ${winner}",
                    "BlueFor: ${score_blue}",
                    "RedFor: ${score_red}");
        }
        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "Red: ${score_red} Blue: ${score_blue}",
                            "${label}",
                            "${timelabel}:",
                            "   ${timer}"));
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public JSONObject getState() {
        return MQTT.merge(super.getState(), get_vars());
    }

    @Override
    public void add_model_data(Model model) {
        super.add_model_data(model);
        JSONObject vars = get_vars();
        model.addAttribute("score_red", vars.getLong("score_red"));
        model.addAttribute("score_blue", vars.getLong("score_blue"));
        model.addAttribute("active_agent", get_active_flag());

        if (random_flag_selection)
            model.addAttribute("next_agent", "Randomized - hence unknown");
        else {
            int next_capture_point = active_capture_point + 1;
            if (next_capture_point >= capture_points.size()) next_capture_point = 0;
            model.addAttribute("next_agent", capture_points.get(next_capture_point));
        }

        model.addAttribute("active_state", cpFSMs.get(get_active_flag()).getCurrentState());
        model.addAttribute("random_flag_selection", random_flag_selection);
    }

}
