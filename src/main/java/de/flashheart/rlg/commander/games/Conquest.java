package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class Conquest extends WithRespawns {
    //    private final BigDecimal BLEEDING_DIVISOR = BigDecimal.valueOf(2);

    //private long broadcast_cycle_counter;
    private final BigDecimal respawn_tickets;
    private final BigDecimal ticket_price_for_respawn;

    private BigDecimal remaining_blue_tickets, remaining_red_tickets;
    private final HashSet<String> cps_held_by_blue, cps_held_by_red;
    private final BigDecimal[] ticket_bleed_table; // bleeding tickets per second per number of flags taken
    // todo: idea - add chart for the tickets over time
    private final ArrayList<Triple<LocalDateTime, BigDecimal, BigDecimal>> scores;
    private final String who_goes_first;
    private long time_since_last_score_storage;

    /**
     * This class creates a conquest style game as known from Battlefield.
     * <p>
     * for the whole issue of ticket arithmetics please refer to
     * https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542 for
     * explanation The mandatory parameters are handed down to this class via a JSONObject called
     * <b>game_parameters</b>.
     *
     * <ul>
     * <li><b>respawn_tickets</b>: the number of respawn tickets each team starts with</li>
     * <li><b>ticket_price_to_respawn</b>: the number of tickets lost, when a player pushes the spawn button</li>
     * <li><b>minimum_cp_for_bleeding</b>: the minimum number of capture points a team has to hold before the ticket bleeding starts for the other team</li>
     * <li><b>starting_bleed_interval</b>: number of seconds for the first bleeding interval step</li>
     * <li><b>interval_reduction_per_cp</b>: number of seconds for the bleeding interval to shorten, when another point is taken. <b>note:</b> the last two parameters are virtual. This calculation method is used in the video games. For us it is easier that the bleeding interval doesn't change at all. Bleeding happens at a constant rate of 0,5 seconds (see BLEEDING_CALCULATION_INTERVALL_IN_S). The amount of tickets lost in- or decrease to match the number of owned capture points.</li>
     * <li><b>agents</b>
     * <ul>
     *     <li><b>capture_points</b> a list of agents to act as capture points. This class handles as many points as listed here.</li>
     *     <li><b>red_spawn</b> the agent for the red spawn. Handles the red spawn button.</li>
     *     <li><b>blue_spawn</b> the agent for the red spawn. Handles the blue spawn button.</li>
     *     <li><b>sirens</b> the list of agents providing sirens for the game.</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param game_parameters a JSONObject containing the (mandatory) game parameters for this match.
     * @param scheduler       internal scheduler reference as this class is NOT a spring component and therefore cannot
     *                        use DI.
     * @param mqttOutbound    internal mqtt reference as this class is NOT a spring component and therefore cannot use
     *                        DI.
     */
    public Conquest(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);

        assert_two_teams_red_and_blue();

        int number_of_cps = roles.get("capture_points").size();
        if (number_of_cps < 3) throw new GameSetupException("Minimum number of CPs: 3");

        this.who_goes_first = game_parameters.optString("who_goes_first", "blue");
        this.respawn_tickets = game_parameters.getBigDecimal("respawn_tickets");
        remaining_blue_tickets = respawn_tickets;
        remaining_red_tickets = respawn_tickets;

        BigDecimal not_bleeding_before_cps = game_parameters.getBigDecimal("not_bleeding_before_cps");
        BigDecimal start_bleed_interval = game_parameters.getBigDecimal("start_bleed_interval");
        BigDecimal end_bleed_interval = game_parameters.getBigDecimal("end_bleed_interval");
        this.ticket_price_for_respawn = game_parameters.getBigDecimal("ticket_price_for_respawn");

        cps_held_by_blue = new HashSet<>();
        cps_held_by_red = new HashSet<>();

        // calculate ticket losses per captured flags
        // see https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit?usp=sharing
        BigDecimal[] interval_table = new BigDecimal[number_of_cps + 1];
        ticket_bleed_table = new BigDecimal[number_of_cps + 1];
        BigDecimal between = start_bleed_interval.subtract(end_bleed_interval);
        BigDecimal interval_reduction_per_cp = between.divide(BigDecimal.valueOf(number_of_cps).subtract(not_bleeding_before_cps), 4, RoundingMode.HALF_UP);
        scores = new ArrayList<>();

        // fill array with zeros under "not_bleeding..."
        for (int cp = 0; cp < not_bleeding_before_cps.intValue(); cp++) {
            interval_table[cp] = BigDecimal.ZERO;
            ticket_bleed_table[cp] = BigDecimal.ZERO;
        }

        // not_bleeding_before_cps is the first index when we need a bleeding calculation
        int start_index = not_bleeding_before_cps.intValue();
        interval_table[start_index] = start_bleed_interval;
        ticket_bleed_table[start_index] = SCORE_CALCULATION_EVERY_N_SECONDS.divide(start_bleed_interval, 4, RoundingMode.HALF_UP);
        BigDecimal min_bleeding = ticket_bleed_table[start_index];
        BigDecimal max_bleeding = BigDecimal.ZERO;
        for (int flag = start_index + 1; flag <= number_of_cps; flag++) {
            interval_table[flag] = interval_table[flag - 1].subtract(interval_reduction_per_cp);
            ticket_bleed_table[flag] = SCORE_CALCULATION_EVERY_N_SECONDS.divide(interval_table[flag], 4, RoundingMode.HALF_UP);
            max_bleeding = ticket_bleed_table[flag].max(max_bleeding);
        }

        // min and max game time (once the minimum amount of flag has been captured, no bleeding before that point in time). see parameter: not_bleeding_before_cps
        BigDecimal min_seconds_gametime = respawn_tickets.divide(max_bleeding, 4, RoundingMode.HALF_UP).multiply(SCORE_CALCULATION_EVERY_N_SECONDS);
        BigDecimal max_seconds_gametime = respawn_tickets.divide(min_bleeding, 4, RoundingMode.HALF_UP).multiply(SCORE_CALCULATION_EVERY_N_SECONDS);
        LocalTime min_time = LocalTime.ofSecondOfDay(min_seconds_gametime.intValue());
        LocalTime max_time = LocalTime.ofSecondOfDay(max_seconds_gametime.intValue());

        log.trace("Interval Table: {}", Arrays.toString(interval_table));
        log.trace("Ticket Bleeding per {} seconds: {}", SCORE_CALCULATION_EVERY_N_SECONDS, Arrays.toString(ticket_bleed_table));
        log.trace("gametime≈{}-{}", min_time.format(DateTimeFormatter.ofPattern("mm:ss")), max_time.format(DateTimeFormatter.ofPattern("mm:ss")));

        setGameDescription(game_parameters.getString("comment"),
                String.format("Tickets: %s", respawn_tickets.intValue()),
                String.format("Bleed starts @%sCPs", not_bleeding_before_cps),
                String.format("Time: %s-%s", min_time.format(DateTimeFormatter.ofPattern("mm:ss")), max_time.format(DateTimeFormatter.ofPattern("mm:ss"))) + " ${wifi_signal}");
    }

    @Override
    protected void on_spawn_button_pressed(String spawn, String agent_id) {
        super.on_spawn_button_pressed(spawn, agent_id);
        if (spawn.equals("red_spawn")) {
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_for_respawn);
        }
        if (spawn.equals("blue_spawn")) {
            remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_price_for_respawn);
        }
    }


    @Override
    public void on_external_message(String agent_id, String source, JSONObject message) {
        if (cpFSMs.containsKey(agent_id) && game_fsm.getCurrentState().equals(_state_RUNNING)) {
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
    public FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED"))), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.WHITE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    private void cp_to_blue(String agent) {
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.BLUE, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "blue"));
    }

    private void cp_to_red(String agent) {
        send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.BUZZER, MQTT.DOUBLE_BUZZ), agent);
        send(MQTT.CMD_VISUAL, MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF, MQTT.RED, MQTT.RECURRING_SCHEME_NORMAL), agent);
        add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "red"));
    }

    private void update_cps_held_by_list() {
        cps_held_by_blue.clear();
        cps_held_by_red.clear();
        // gather blue
        cpFSMs.entrySet().stream().filter(stringFSMEntry -> stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("BLUE")
                        || stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("GAME_OVER_BLUE"))
                .forEach(stringFSMEntry -> cps_held_by_blue.add(stringFSMEntry.getKey()));
        // gather red
        cpFSMs.entrySet().stream().filter(stringFSMEntry -> stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("RED")
                        || stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("GAME_OVER_RED"))
                .forEach(stringFSMEntry -> cps_held_by_red.add(stringFSMEntry.getKey()));
    }

    @Override
    public void on_reset() {
        super.on_reset();
        remaining_blue_tickets = respawn_tickets;
        remaining_red_tickets = respawn_tickets;
        cps_held_by_blue.clear();
        cps_held_by_red.clear();
        scores.clear();
    }

    @Override
    protected void calculate_score() {
        super.calculate_score();
        update_cps_held_by_list();
        remaining_red_tickets = remaining_red_tickets.subtract(ticket_bleed_table[cps_held_by_blue.size()]);
        remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_bleed_table[cps_held_by_red.size()]);

        // only once every minute
        time_since_last_score_storage += time_passed_since_last_calculation;
        if (time_since_last_score_storage >= 60000L) {
            time_since_last_score_storage = 0L;
            scores.add(new ImmutableTriple<>(LocalDateTime.now(), remaining_red_tickets, remaining_blue_tickets));
        }

        log.trace("Cp: R{} B{}", cps_held_by_red.size(), cps_held_by_blue.size());
        log.trace("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());

        if (game_fsm.getCurrentState().equalsIgnoreCase(_state_RUNNING) &&
                (remaining_blue_tickets.intValue() <= 0 || remaining_red_tickets.intValue() <= 0))
            process_internal_message(_msg_GAME_OVER);
    }

    @Override
    protected JSONObject get_broadcast_vars() {
        JSONObject vars = super.get_broadcast_vars();

        vars.put("red_tickets", Integer.toString(remaining_red_tickets.intValue()));
        vars.put("blue_tickets", Integer.toString(remaining_blue_tickets.intValue()));

//        String[] red_flags = split_list_to_lines(20, cps_held_by_red, "", "", "");
//        vars.put("red_l1", red_flags[0]);
//        vars.put("red_l2", red_flags[1]);
//
//        String[] blue_flags = split_list_to_lines(20, cps_held_by_blue, "", "", "");
//        vars.put("blue_l1", blue_flags[0]);
//        vars.put("blue_l2", blue_flags[1]);

        vars.put("red_flags", cps_held_by_red.size());
        vars.put("blue_flags", cps_held_by_blue.size());

        return vars;
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        if (state.equals(_state_EPILOG)) {
            String outcome = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "Team Red" : "Team Blue";
            return MQTT.page("page0", "Game Over", "Red: ${red_tickets} Blue: ${blue_tickets}", "The Winner is", outcome);
        }
        if (state.equals(_state_RUNNING)) {
            return
                    MQTT.page("page0",
                            "RED Flags: ${red_flags}",
                            "BLUE Flags: ${blue_flags}",
                            " ".repeat(18) + "${wifi_signal}",
                            "Red->${red_tickets}:${blue_tickets}<-Blue");
        }
        return MQTT.page("page0", game_description);
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
        }
    }


    @Override
    public String getGameMode() {
        return "conquest";
    }

    @Override
    public boolean hasZeus() {
        return true;
    }

    @Override
    public JSONObject get_full_state() {
        update_cps_held_by_list();

        JSONObject json = super.get_full_state();
        json.getJSONObject("played")
                .put("remaining_blue_tickets", remaining_blue_tickets.intValue())
                .put("remaining_red_tickets", remaining_red_tickets.intValue())
                .put("cps_held_by_blue", cps_held_by_blue)
                .put("cps_held_by_red", cps_held_by_red);
        return json;
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        update_cps_held_by_list();
        JSONArray score_data_red = new JSONArray();
        JSONArray score_data_blue = new JSONArray();

        scores.forEach(triple -> {
            score_data_red.put(triple.getMiddle());
            score_data_blue.put(triple.getRight());
        });

        model.addAttribute("who_goes_first", who_goes_first.toUpperCase());
        model.addAttribute("who_goes_first_style", who_goes_first.equals("blue") ? "text-primary" : "text-danger");
        model.addAttribute("cps_held_by_blue", cps_held_by_blue.stream().sorted().collect(Collectors.toList()));
        model.addAttribute("cps_held_by_red", cps_held_by_red.stream().sorted().collect(Collectors.toList()));
        model.addAttribute("remaining_blue_tickets", remaining_blue_tickets.intValue());
        model.addAttribute("remaining_red_tickets", remaining_red_tickets.intValue());
        model.addAttribute("respawn_tickets", respawn_tickets.intValue());
        model.addAttribute("score_data_red", score_data_red.toList());
        model.addAttribute("score_data_blue", score_data_blue.toList());


    }
}
