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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Gamemode inspired by Call Of Duty Hardpoint or Headquarters.
 * <p>Suggested back in 2019 by <a href="https://woodlandforum.com/board/index.php/User/14458-Chronoxon/?s=a70741ca9836656e56796bbf1461625052338da9">Chronoxon</a> on the <a href="https://woodlandforum.com/board/index.php/Thread/39784-Ideen-f%C3%BCr-elektronisches-Spielsystem/?postID=666001#post666001">Woodlandforum</a></p>
 */
// todo: add option to "hide the next flag announcement"
@Log4j2
public class Hardpoint extends WithRespawns implements HasDelayedReaction, HasScoreBroadcast, HasActivation, HasTimeOut, HasFlagTimer {
    public static final String _msg_TO_NEUTRAL = "to_neutral";
    public static final String _msg_DEACTIVATE = "deactivate";
    public static final String _msg_TO_BLUE = "to_blue";
    public static final String _msg_TO_RED = "to_red";
    public static final String _msg_GO = "go";
    public static final String _msg_ACCEPTED = "accepted";
    public static final String _msg_NEXT_FLAG = "next_flag";
    public static final String _flag_state_RED_SCORING = "RED_SCORING";
    public static final String _flag_state_BLUE_SCORING = "BLUE_SCORING";
    private static final String _flag_state_GET_READY = "GET_READY";
    private static final String _flag_state_STAND_BY = "STAND_BY";
    private static final String _flag_state_AFTER_FLAG_TIME_UP = "AFTER_FLAG_TIME_IS_UP";
    private static final String _flag_state_PROLOG = _state_PROLOG;
    private long broadcast_cycle_counter;
    private final long winning_score, flag_time_up, flag_time_out, delay_until_next_flag;
    private final String who_goes_first;
    private final BigDecimal delay_after_color_change;
    private final List<String> capture_points;
    private final Table<String, String, Long> scores;
    private long iteration_score_red, iteration_score_blue;
    private final JobKey broadcastScoreJobkey, flag_activation_jobkey, delayed_reaction_jobkey, flag_time_up_jobkey, flag_time_out_jobkey;
    private long last_job_broadcast;
    private int active_capture_point;
    private final CircularFifoQueue<String> recently_activated_flags;
    private boolean hide_next_flag;

    public Hardpoint(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        assert_two_teams_red_and_blue();

        count_respawns = false;
        this.who_goes_first = game_parameters.optString("who_goes_first", "blue");
        this.winning_score = game_parameters.optLong("winning_score", 250);
        this.flag_time_out = game_parameters.optLong("flag_time_out", 120);
        this.flag_time_up = game_parameters.optLong("flag_time_up", 120);
        this.delay_until_next_flag = game_parameters.optLong("delay_until_next_flag", 30);
        this.delay_after_color_change = game_parameters.optBigDecimal("delay_after_color_change", BigDecimal.ONE);
        this.hide_next_flag = game_parameters.optBoolean("hide_next_flag", true);

        capture_points = game_parameters.getJSONObject("agents").getJSONArray("capture_points").toList().stream().map(Object::toString).collect(Collectors.toList());

        // random makes no sense with less than 4 flags
        //this.random_flag_selection = capture_points.size() > 3 && game_parameters.optBoolean("random_flag_selection", false);

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
        last_line_in_description = StringUtils.rightPad(last_line_in_description, 18, " ") + "${wifi_signal}";

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
            fsm.setStatesAfterTransition(_flag_state_PROLOG, (state, obj) -> cp_prolog(agent));
            fsm.setStatesAfterTransition(_flag_state_GET_READY, (state, obj) -> cp_get_ready(agent));
            fsm.setStatesAfterTransition(_flag_state_NEUTRAL, (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition(_flag_state_STAND_BY, (state, obj) -> cp_to_stand_by(agent));
            fsm.setStatesAfterTransition(_flag_state_AFTER_FLAG_TIME_UP, (state, obj) -> next_flag());
            fsm.setStatesAfterTransition(Lists.newArrayList(_flag_state_RED, _flag_state_BLUE), (state, obj) ->
                    cp_to_color(agent, StringUtils.left(state.toLowerCase(), 3))
            );
            fsm.setStatesAfterTransition(Lists.newArrayList(_flag_state_RED_SCORING, _flag_state_BLUE_SCORING), (state, obj) ->
                    cp_to_scoring_color(agent, StringUtils.left(state.toLowerCase(), 3))
            );
            // after a flag has been accepted for the first time
            fsm.setAction(Lists.newArrayList(_flag_state_BLUE, _flag_state_RED), _msg_ACCEPTED, new FSMAction() {
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
                        send("acoustic", MQTT.toJSON(MQTT.SHUTDOWN_SIREN, MQTT.LONG), roles.get("sirens"));
                    log.trace("flag ended with score red {} and blue {}", iteration_score_red / 1000L, iteration_score_blue / 1000L);
                    if (iteration_score_red + iteration_score_blue > 0) {
                        add_in_game_event(new JSONObject().put("message", String.format("Flag %s added %s points for RED and %s points for BLUE", agent, iteration_score_red / 1000L, iteration_score_blue / 1000L)).put("agent", agent), "free_text");
                    }
                    iteration_score_red = 0L;
                    iteration_score_blue = 0L;
                    return true;
                }
            });
            fsm.setAction(_flag_state_GET_READY, _msg_GO, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    send("acoustic", MQTT.toJSON(MQTT.EVENT_SIREN, MQTT.LONG), roles.get("sirens"));
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
        log.trace("cp_prolog {}", agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "PROLOG"),
                agent);
    }

    private void next_flag() {
        log.trace("next_flag {}", peek_next_flag());
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_NEXT_FLAG);
        deleteJob(flag_time_out_jobkey);

        active_capture_point++;
        if (active_capture_point >= capture_points.size()) active_capture_point = 0;

        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_PREPARE);
    }

    @Override
    public void flag_time_is_up(String bombid) {
        log.trace("flag_time_is_up({})", get_active_flag());
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_FLAG_TIME_IS_UP);
    }

    private void cp_get_ready(String agent) {
        log.trace("getting ready {}", get_active_flag());
        if (delay_until_next_flag > 0) {
            create_job(flag_activation_jobkey, LocalDateTime.now().plusSeconds(delay_until_next_flag), ActivationJob.class, Optional.empty());
            send("timers", MQTT.toJSON("timer", Long.toString(delay_until_next_flag)), agents.keySet());
            send("vars", MQTT.toJSON("timelabel", "Next READY in"), agents.keySet());
            send(MQTT.CMD_PAGED,
                    MQTT.page("page0",
                            "I am ${agentname}", "", "", "Flag is preparing"),
                    agent);
            send(MQTT.CMD_VISUAL, new JSONObject("""
                    "led_all" : MQTT.OFF,
                    "wht": {
                        "repeat": -1,
                        "scheme": [75,-75,75,-2500]
                    }
                    """), agent); // short flashes
        } else {
            // delay is 0 - activate at once
            activate(new JobDataMap());
        }
    }

    @Override
    public void activate(JobDataMap map) {
        log.trace("activate {}", get_active_flag());
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_GO);
    }

    @Override
    public String getGameMode() {
        return "hardpoint";
    }

    private void cp_to_stand_by(String agent) {
        log.trace("cp_to_stand_by {}", agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag standing by"),
                agent);
        log.trace("sending OFF to {}", agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
    }

    private String peek_next_flag() {
        int peek = active_capture_point + 1 >= capture_points.size() ? 0 : active_capture_point + 1;
        return capture_points.get(peek);
    }

    private String get_active_flag() {
        return capture_points.get(active_capture_point);
    }

    private void cp_to_neutral(String agent) {
        // time limit - if nobody can touch the flag
        log.trace("cp_to_neutral {}", agent);
        create_job(flag_time_out_jobkey, LocalDateTime.now().plusSeconds(flag_time_out), TimeOutJob.class, Optional.empty());
        send("timers", MQTT.toJSON("timer", Long.toString(flag_time_out)), agents.keySet());
        // notify the players that this flag is active now
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.SCHEME_VERY_LONG), agent);
        String label = "actv:" + get_active_flag();
        if (!hide_next_flag) label += " next:" + peek_next_flag();
        send("vars", MQTT.toJSON("timelabel", "Next Flag in", "label", label), agents.keySet());

        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is NEUTRAL"),
                agent);
        log.trace("sending white to {}", agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "NEUTRAL"));
    }


    @Override
    public void timeout(JobDataMap map) {
        log.trace("flag has timed out. nobody made it to the point. next flag please.");
        next_flag();
    }

    private void cp_to_color(String agent, String color) {
        create_job(delayed_reaction_jobkey,
                LocalDateTime.now().plus(delay_after_color_change.multiply(new BigDecimal(1000L)).longValue(), ChronoUnit.MILLIS),
                DelayedReactionJob.class, Optional.empty());
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, color, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
    }

    private void cp_to_scoring_color(String agent, String color) {
        String COLOR = (color.equalsIgnoreCase("blu") ? "BLUE" : "RED");
        deleteJob(flag_time_out_jobkey);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.ALERT_SIREN, MQTT.MEDIUM), roles.get("sirens"));
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, color, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is " + COLOR),
                agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", COLOR));
    }

    @Override
    public void delayed_reaction(JobDataMap map) {
        cpFSMs.get(get_active_flag()).ProcessFSM(_msg_ACCEPTED);
    }

    private void reset_score_table() {
        iteration_score_red = 0L;
        iteration_score_blue = 0L;

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
        active_capture_point = 0;
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
        if (!current_state.matches(_flag_state_BLUE_SCORING + "|" + _flag_state_RED_SCORING)) return;
        String color = current_state.equals(_flag_state_BLUE_SCORING) ? "blue" : "red";

        // replace cell values
        scores.put(agent, color, scores.get(agent, color) + score); // replace cell value
        scores.put("all", color, scores.get("all", color) + score);

        if (color.equals("red")) iteration_score_red += score;
        else iteration_score_blue += score;

        if (scores.get("all", "red") >= winning_score * 1000L || scores.get("all", "blue") >= winning_score * 1000L)
            game_fsm.ProcessFSM(_msg_GAME_OVER);
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (game_fsm.getCurrentState().equals(_state_RUNNING) && get_active_flag().equalsIgnoreCase(agent_id)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            if (cpFSMs.get(agent_id).getCurrentState().equalsIgnoreCase(_flag_state_NEUTRAL)) {
                cpFSMs.get(agent_id).ProcessFSM(who_goes_first.equalsIgnoreCase("blue") ? _msg_TO_BLUE : _msg_TO_RED);
            } else {
                cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
            }
        } else
            super.on_external_message(agent_id, source, message);
    }

    @Override
    protected void on_spawn_button_pressed(String role, String agent_id) {
        // no respawns in Hardpoint
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            return MQTT.page("page0",
                    "Game Over         ${wifi_signal}",
                    "Winner: ${winner}",
                    "BlueFor: ${score_blue}",
                    "RedFor: ${score_red}");
        }
        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "Red: ${score_red} Blue: ${score_blue}   ${wifi_signal}",
                            "${label}",
                            "${timelabel}:",
                            "   ${timer}"));
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public JSONObject getState() {
        JSONObject json = super.getState();
        JSONObject played = MQTT.merge(json.getJSONObject("played"), get_vars());
        json.put("played", played);
        return json;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        JSONObject vars = get_vars();
        model.addAttribute("score_red", vars.getLong("score_red"));
        model.addAttribute("score_blue", vars.getLong("score_blue"));
        model.addAttribute("active_agent", get_active_flag());

        int next_capture_point = active_capture_point + 1;
        if (next_capture_point >= capture_points.size()) next_capture_point = 0;
        model.addAttribute("next_agent", capture_points.get(next_capture_point));

        model.addAttribute("winning_score", winning_score);
        model.addAttribute("who_goes_first", who_goes_first.toUpperCase());
        model.addAttribute("who_goes_first_style", who_goes_first.equals("blue") ? "text-primary" : "text-danger");
        model.addAttribute("flag_time_out", flag_time_out);
        model.addAttribute("flag_time_up", flag_time_up);
        model.addAttribute("delay_until_next_flag", delay_until_next_flag);

        model.addAttribute("active_state", cpFSMs.get(get_active_flag()).getCurrentState());

    }

    @Override
    public boolean hasZeus() {
        return false;
    }

    @Override
    public void zeus(JSONObject params) throws IllegalStateException, JSONException {
        if (!game_fsm.getCurrentState().equals(_state_RUNNING)) return;
        String operation = params.getString("operation").toLowerCase();
        if (operation.equalsIgnoreCase("to_neutral")) {
            String agent = params.getString("agent");
            if (!cpFSMs.containsKey(agent)) throw new IllegalStateException(agent + " unknown");
            if (!cpFSMs.get(agent).getCurrentState().toLowerCase().matches("blue|red"))
                throw new IllegalStateException(agent + " is in state " + cpFSMs.get(agent).getCurrentState() + " must be BLUE or RED");
            cpFSMs.get(agent).ProcessFSM(operation);
            add_in_game_event(new JSONObject()
                    .put("item", "capture_point")
                    .put("agent", agent)
                    .put("state", "neutral")
                    .put("zeus", "intervention"));
            return;
        }

        if (operation.equalsIgnoreCase("next_flag")) {
            String agent = get_active_flag();
            if (!cpFSMs.containsKey(agent)) throw new IllegalStateException(agent + " unknown");
            if (!cpFSMs.get(agent).getCurrentState().equalsIgnoreCase(_flag_state_NEUTRAL))
                throw new IllegalStateException(agent + " is in state " + cpFSMs.get(agent).getCurrentState() + " must be NEUTRAL");
            next_flag();
            add_in_game_event(new JSONObject()
                    .put("item", "capture_point")
                    .put("agent", agent)
                    .put("state", "next_flag")
                    .put("zeus", "intervention"));
            return;
        }
    }

}
