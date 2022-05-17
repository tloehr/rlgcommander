package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ConquestTicketBleedingJob;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.WordUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * lifecycle cleanup before new game is laoded (by GameService)
 */
@Log4j2
public class Conquest extends WithRespawns {
    //    private final BigDecimal BLEEDING_DIVISOR = BigDecimal.valueOf(2);
    private final BigDecimal TICKET_CALCULATION_EVERY_N_SECONDS = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final Map<String, FSM> cpFSMs;
    private final BigDecimal respawn_tickets, ticket_price_for_respawn, not_bleeding_before_cps, start_bleed_interval, end_bleed_interval;

    private BigDecimal remaining_blue_tickets, remaining_red_tickets;
    private int blue_respawns, red_respawns;
    private HashSet<String> cps_held_by_blue, cps_held_by_red;
    private final JobKey ticketBleedingJobkey;
    private final BigDecimal[] ticket_bleed_table; // bleeding tickets per second per number of flags taken


    /**
     * This class creates a conquest style game as known from Battlefield.
     * <p>
     * for the whole issue of ticket arithmetics please refer to
     * https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542 for
     * explanation The mandatory parameters are handed down to this class via a JSONObject called
     * <b>game_parameters</b>.
     *
     * <ul>
     * <li><b>starting_tickets</b>: the number of respawn tickets each team starts with</li>
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
    public Conquest(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound);
        log.debug("\n   ______                                  __\n" +
                "  / ____/___  ____  ____ ___  _____  _____/ /_\n" +
                " / /   / __ \\/ __ \\/ __ `/ / / / _ \\/ ___/ __/\n" +
                "/ /___/ /_/ / / / / /_/ / /_/ /  __(__  ) /_\n" +
                "\\____/\\____/_/ /_/\\__, /\\__,_/\\___/____/\\__/\n" +
                "                    /_/");

        this.respawn_tickets = game_parameters.getBigDecimal("respawn_tickets");
        this.not_bleeding_before_cps = game_parameters.getBigDecimal("not_bleeding_before_cps");
        this.start_bleed_interval = game_parameters.getBigDecimal("start_bleed_interval");
        this.end_bleed_interval = game_parameters.getBigDecimal("end_bleed_interval");
        this.ticket_price_for_respawn = game_parameters.getBigDecimal("ticket_price_for_respawn");

        cps_held_by_blue = new HashSet<>();
        cps_held_by_red = new HashSet<>();

        // calculate ticket losses per captured flags
        // see https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit?usp=sharing
        int number_of_cps = roles.get("capture_points").size();
        BigDecimal[] interval_table = new BigDecimal[number_of_cps + 1];
        ticket_bleed_table = new BigDecimal[number_of_cps + 1];
        BigDecimal between = start_bleed_interval.subtract(end_bleed_interval);
        BigDecimal interval_reduction_per_cp = between.divide(BigDecimal.valueOf(number_of_cps).subtract(not_bleeding_before_cps), 4, RoundingMode.HALF_UP);

        // fill array with zeros under "not_bleeding..."
        for (int cp = 0; cp < not_bleeding_before_cps.intValue(); cp++) {
            interval_table[cp] = BigDecimal.ZERO;
            ticket_bleed_table[cp] = BigDecimal.ZERO;
        }

        // not_bleeding_before_cps is the first index when we need a bleeding calculation
        int start_index = not_bleeding_before_cps.intValue();
        interval_table[start_index] = start_bleed_interval;
        ticket_bleed_table[start_index] = TICKET_CALCULATION_EVERY_N_SECONDS.divide(start_bleed_interval, 4, RoundingMode.HALF_UP);
        BigDecimal min_bleeding = ticket_bleed_table[start_index];
        BigDecimal max_bleeding = BigDecimal.ZERO;
        for (int flag = start_index + 1; flag <= number_of_cps; flag++) {
            interval_table[flag] = interval_table[flag - 1].subtract(interval_reduction_per_cp);
            ticket_bleed_table[flag] = TICKET_CALCULATION_EVERY_N_SECONDS.divide(interval_table[flag], 4, RoundingMode.HALF_UP);
            max_bleeding = ticket_bleed_table[flag].max(max_bleeding);
        }

        // min and max game time (once the minimum amount of flag has been captured, no bleeding before that point in time). see parameter: not_bleeding_before_cps
        BigDecimal min_seconds_gametime = respawn_tickets.divide(max_bleeding, 4, RoundingMode.HALF_UP).multiply(TICKET_CALCULATION_EVERY_N_SECONDS);
        BigDecimal max_seconds_gametime = respawn_tickets.divide(min_bleeding, 4, RoundingMode.HALF_UP).multiply(TICKET_CALCULATION_EVERY_N_SECONDS);
        LocalTime min_time = LocalTime.ofSecondOfDay(min_seconds_gametime.intValue());
        LocalTime max_time = LocalTime.ofSecondOfDay(max_seconds_gametime.intValue());

        log.debug("Interval Table: {}", Arrays.toString(interval_table));
        log.debug("Ticket Bleeding per {} seconds: {}", TICKET_CALCULATION_EVERY_N_SECONDS, Arrays.toString(ticket_bleed_table));
        log.debug("gametime≈{}-{}", min_time.format(DateTimeFormatter.ofPattern("mm:ss")), max_time.format(DateTimeFormatter.ofPattern("mm:ss")));

        this.ticketBleedingJobkey = new JobKey("ticketbleeding", uuid.toString());

        cpFSMs = new HashMap<>();
        setGameDescription(game_parameters.getString("comment"),
                String.format("Tickets: %s", respawn_tickets.intValue()),
                String.format("Bleed starts @%sCPs", not_bleeding_before_cps),
                String.format("Time: %s-%s", min_time.format(DateTimeFormatter.ofPattern("mm:ss")), max_time.format(DateTimeFormatter.ofPattern("mm:ss"))));

        roles.get("capture_points").forEach(agent -> cpFSMs.put(agent, create_CP_FSM(agent)));
        add_spawn_for("red_spawn", "led_red", "Team Red");
        add_spawn_for("blue_spawn", "led_blu", "Team Blue");
    }

    @Override
    protected void respawn(String role, String agent) {
        if (role.equals("red_spawn")) {
            mqttOutbound.send("signals", MQTT.toJSON("buzzer", "single_buzz", "led_wht", "single_buzz"), agent);
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_for_respawn);
            red_respawns++;
            addEvent(String.format("RED Team Respawn #%s", red_respawns));
            broadcast_score();
        }
        if (role.equals("blue_spawn")) {
            mqttOutbound.send("signals", MQTT.toJSON("buzzer", "single_buzz", "led_wht", "single_buzz"), agent);
            remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_price_for_respawn);
            blue_respawns++;
            addEvent(String.format("BLUE Team Respawn #%s", blue_respawns));
            broadcast_score();
        }
        process_message(_msg_IN_GAME_EVENT_OCCURRED);
    }

    @Override
    public void process_message(String sender, String item, JSONObject message) {
        if (!item.equalsIgnoreCase("btn01")) {
            log.trace("no btn01 message. discarding.");
            return;
        }
        if (!message.getString("button").equalsIgnoreCase("up")) {
            log.trace("only reacting on button UP. discarding.");
            return;
        }

        if (hasRole(sender, "capture_points")) {
            if (game_fsm.getCurrentState().equals(_state_RUNNING)) cpFSMs.get(sender).ProcessFSM(item.toLowerCase());
        } else super.process_message(sender, item, message);
    }


    private FSM create_CP_FSM(final String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest_cp.xml"), null);
            fsm.setStatesAfterTransition("PROLOG", (state, obj) -> cp_to_neutral(agent));
            fsm.setStatesAfterTransition("NEUTRAL", (state, obj) -> {
                broadcast_score();
                cp_to_neutral(agent);
            });
            fsm.setStatesAfterTransition((new ArrayList<>(Arrays.asList("BLUE", "RED"))), (state, obj) -> {
                if (state.equalsIgnoreCase("BLUE")) cp_to_blue(agent);
                else cp_to_red(agent);
                broadcast_score();
                process_message(_msg_IN_GAME_EVENT_OCCURRED);
            });
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void cp_to_neutral(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "I will be a", "Capture Point"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_wht", "normal"), agent);
    }

    private void cp_to_blue(String agent) {
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_blu", "normal", "buzzer", "double_buzz"), agent);
        addEvent(String.format("Agent %s switched to BLUE", agent));
    }

    private void cp_to_red(String agent) {
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_red", "normal", "buzzer", "double_buzz"), agent);
        addEvent(String.format("Agent %s switched to RED", agent));
    }

    public void ticket_bleeding_cycle() {
        if (pausing_since.isPresent()) return; // as we are pausing, we are not doing anything

        broadcast_cycle_counter++;

        update_cps_held_by_list();

        remaining_red_tickets = remaining_red_tickets.subtract(ticket_bleed_table[cps_held_by_blue.size()]);
        remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_bleed_table[cps_held_by_red.size()]);

        log.trace("Cp: R{} B{}", cps_held_by_red, cps_held_by_blue);
        log.trace("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());

        if (broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES == 0)
            broadcast_score();

        if (remaining_blue_tickets.intValue() <= 0 || remaining_red_tickets.intValue() <= 0) {
            broadcast_score();
            process_message(_msg_GAME_OVER);
        }
    }

    private void update_cps_held_by_list() {
        cps_held_by_blue.clear();
        cps_held_by_red.clear();
        cpFSMs.entrySet().stream().filter(stringFSMEntry -> stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("BLUE")).forEach(stringFSMEntry -> cps_held_by_blue.add(stringFSMEntry.getKey()));
        cpFSMs.entrySet().stream().filter(stringFSMEntry -> stringFSMEntry.getValue().getCurrentState().equalsIgnoreCase("RED")).forEach(stringFSMEntry -> cps_held_by_red.add(stringFSMEntry.getKey()));
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RESET)) {
            blue_respawns = 0;
            red_respawns = 0;
            remaining_blue_tickets = respawn_tickets;
            remaining_red_tickets = respawn_tickets;
            cps_held_by_blue.clear();
            cps_held_by_red.clear();
        }
        if (message.equals(_msg_RUN)) { // need to react on the message here rather than the state, because it would mess up the game after a potential "continue" which also ends in the state "RUNNING"
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RUN));

            // setup and start bleeding job
            long repeat_every_ms = TICKET_CALCULATION_EVERY_N_SECONDS.multiply(BigDecimal.valueOf(1000l)).longValue();
            create_job(ticketBleedingJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), ConquestTicketBleedingJob.class);
            broadcast_cycle_counter = 0;
        }
    }

    @Override
    protected void at_state(String state) {
        super.at_state(state);
        if (state.equals(_state_PROLOG)) {
            deleteJob(ticketBleedingJobkey);
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM(_msg_RESET));
        }

        if (state.equals(_state_EPILOG)) {
            deleteJob(ticketBleedingJobkey); // this cycle has no use anymore
            log.info("Red Respawns #{}, Blue Respawns #{}", red_respawns, blue_respawns);
            cpFSMs.values().forEach(fsm -> fsm.ProcessFSM("game_over"));
        }
    }

    private void broadcast_score() {
        JSONObject vars = MQTT.toJSON("red_tickets", Integer.toString(remaining_red_tickets.intValue()),
                "blue_tickets", Integer.toString(remaining_blue_tickets.intValue()));

        String[] red_flags = get_flag_list(20, cps_held_by_red);
        vars.put("red_l1", red_flags[0]);
        vars.put("red_l2", red_flags[1]);

        String[] blue_flags = get_flag_list(20, cps_held_by_blue);
        vars.put("blue_l1", blue_flags[0]);
        vars.put("blue_l2", blue_flags[1]);

        mqttOutbound.send("vars", vars, roles.get("spawns"));

        log.trace("Cp: R{} B{}", cps_held_by_red.size(), cps_held_by_blue.size());
        log.debug("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());
    }

    /**
     * split list of flags to lines
     *
     * @param linelength
     * @param set
     * @return
     */
    String[] get_flag_list(int linelength, Collection<String> set) {
        if (set.isEmpty()) return new String[]{"", "", ""};
        String[] lines = new String[]{"", "", ""};
        String list_of_flags = WordUtils.wrap(set.stream().sorted().collect(Collectors.joining(" ")), linelength);
        String[] flags = list_of_flags.split(System.lineSeparator());
        for (int i = 0; i < Math.min(lines.length, flags.length); i++) lines[i] = flags[i];
        return lines;
    }

    @Override
    protected JSONObject getPages() {
        log.debug(game_fsm.getCurrentState());
        if (game_fsm.getCurrentState().equals(_state_EPILOG)) {
            String outcome = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "Team Red" : "Team Blue";
            return MQTT.page("page0", "Game Over", "Red: ${red_tickets} Blue: ${blue_tickets}", "The Winner is", outcome);
        }
        if (game_fsm.getCurrentState().equals(_state_RUNNING)) {
            return MQTT.merge(
                    MQTT.page("page0",
                            "   >>> RED   <<<   ",
                            "${red_l1}",
                            "${red_l2}",
                            "Red->${red_tickets}:${blue_tickets}<-Blue"),
                    MQTT.page("page1",
                            "   >>> BLUE  <<<   ",
                            "${blue_l1}",
                            "${blue_l2}",
                            "Red->${red_tickets}:${blue_tickets}<-Blue"));
        }
        return MQTT.page("page0", game_description);
    }

    @Override
    public JSONObject getState() {
        update_cps_held_by_list();

        final JSONObject statusObject = super.getState()
                .put("remaining_blue_tickets", remaining_blue_tickets.intValue())
                .put("remaining_red_tickets", remaining_red_tickets.intValue())
                .put("cps_held_by_blue", cps_held_by_blue)
                .put("cps_held_by_red", cps_held_by_red)
                .put("red_respawns", red_respawns)
                .put("blue_respawns", blue_respawns);

        final JSONObject states = new JSONObject();
        cpFSMs.forEach((agentid, fsm) -> states.put(agentid, fsm.getCurrentState()));
        statusObject.put("agent_states", states);
        return statusObject;
    }
}
