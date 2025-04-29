package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.jobs.FlagTimerJob;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Game mode that has been proposed by Toby. Players have to be quick to "gather" flags on the field by activating them. Those CPs stay active for a while
 * and then turn back to neutral. It is closely connected to Hardpoint, hence the superclass.
 * <ul>
 *     <li>A flag that has been "fetched" scores for the matching team. One Point per second.</li>
 *     <li>locked for period: flag_time_up</li>
 *     <li>match ends when one team has reached: winning_score</li>
 *     <li>fetched flags cant be switched back</li>
 * </ul>
 */
@Log4j2
public class FetchEm extends Hardpoint {

    public FetchEm(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource,locale);
        setGameDescription(
                game_parameters.getString("comment"),
                String.format("Winning@ %s", this.winning_score),
                "",
                "                   ${wifi_signal}"
        );
    }

    @Override
    protected void setup_scheduler_jobs() {
        capture_points.forEach(agent -> {
            register_job("delayed_reaction_" + agent);
            register_job("flag_time_up_" + agent);
        });
    }

    @Override
    public String getGameMode() {
        return "fetch_em";
    }

    @Override
    public FSM create_CP_FSM(final String agent) {
        final JobDataMap jdm = new JobDataMap();
        jdm.put("agent_id", agent);
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/fetch_em.xml"), null);
            fsm.setStatesAfterTransition(_flag_state_PROLOG, (state, obj) -> cp_prolog(agent));
            fsm.setStatesAfterTransition(_flag_state_NEUTRAL, (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition(Lists.newArrayList(_flag_state_RED, _flag_state_BLUE), (state, obj) ->
                    cp_to_color("delayed_reaction_" + agent, agent, StringUtils.left(state.toLowerCase(), 3), jdm)
            );
            fsm.setStatesAfterTransition(Lists.newArrayList(_flag_state_RED_SCORING, _flag_state_BLUE_SCORING), (state, obj) ->
                    cp_to_scoring_color(agent, StringUtils.left(state.toLowerCase(), 3))
            );
            // after a flag has been accepted for the first time
            fsm.setAction(Lists.newArrayList(_flag_state_BLUE, _flag_state_RED), _msg_ACCEPTED, new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    create_job_with_reschedule("flag_time_up_" + agent, LocalDateTime.now().plusSeconds(flag_time_up), FlagTimerJob.class, Optional.of(jdm));
                    return true;
                }
            });

            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        deleteJob("delayed_reaction_" + agent);
        deleteJob("flag_time_up_" + agent);
        send(MQTT.CMD_PAGED,
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "Flag is NEUTRAL"),
                agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "NEUTRAL"));
    }

    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (game_fsm.getCurrentState().equals(_state_RUNNING) && capture_points.contains(agent_id)) {
            if (!source.equalsIgnoreCase(_msg_BUTTON_01)) return;
            if (!message.getString("button").equalsIgnoreCase("up")) return;
            if (cpFSMs.get(agent_id).getCurrentState().equalsIgnoreCase(_flag_state_NEUTRAL)) {
                cpFSMs.get(agent_id).ProcessFSM(who_goes_first.equalsIgnoreCase("blue") ? _msg_TO_BLUE : _msg_TO_RED);
            } else if (cpFSMs.get(agent_id).getCurrentState().matches(_flag_state_BLUE + "|" + _flag_state_RED)) {
                cpFSMs.get(agent_id).ProcessFSM(_msg_BUTTON_01);
            }
        } else
            super.on_external_message(agent_id, source, message);
    }
    
    @Override
    public void activate(JobDataMap map) {
        // nop
    }
    @Override
    public void flag_time_is_up(String agent_id) {
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SHUTDOWN_SIREN, MQTT.SCHEME_VERY_SHORT), roles.get("sirens"));
        super.flag_time_is_up(agent_id);
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
                            "Red Flags:",
                            "${red_flags1}",
                            "${red_flags2}"),
                    MQTT.page("page1",
                            "Red: ${score_red} Blue: ${score_blue}   ${wifi_signal}",
                            "Blue Flags:",
                            "${blue_flags1}",
                            "${blue_flags2}"));
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    protected JSONObject get_broadcast_vars() {
        // determine which flag has which scoring color
        Multimap<String, String> map = HashMultimap.create();
        cpFSMs.forEach((key, value) -> {
            if (value.getCurrentState().equals(_flag_state_RED_SCORING) || value.getCurrentState().equals(_flag_state_BLUE_SCORING))
                map.put(value.getCurrentState(), key);
        });
        String[] red_flags_lines = split_list_to_lines(20, map.get(_flag_state_RED_SCORING), "", "");
        String[] blue_flags_lines = split_list_to_lines(20, map.get(_flag_state_BLUE_SCORING), "", "");
        return super.get_broadcast_vars()
                .put("red_flags", map.get(_flag_state_RED_SCORING).stream().sorted().toList().toString())
                .put("red_flags1", red_flags_lines[0])
                .put("red_flags2", red_flags_lines[1])
                .put("blue_flags", map.get(_flag_state_BLUE_SCORING).stream().sorted().toList().toString())
                .put("blue_flags1", blue_flags_lines[0])
                .put("blue_flags2", blue_flags_lines[1])
                .put("score_blue", scores.get("all", "blue") / 1000L)
                .put("score_red", scores.get("all", "red") / 1000L);
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        JSONObject vars = get_broadcast_vars();
        model.addAttribute("score_red", vars.getLong("score_red"));
        model.addAttribute("score_blue", vars.getLong("score_blue"));
        model.addAttribute("red_flags", vars.getString("red_flags"));
        model.addAttribute("blue_flags", vars.getString("blue_flags"));

        model.addAttribute("winning_score", winning_score);
        model.addAttribute("who_goes_first", who_goes_first.toUpperCase());
        model.addAttribute("who_goes_first_style", who_goes_first.equals("blue") ? "text-primary" : "text-danger");
        model.addAttribute("flag_time_up", flag_time_up);
    }
}
