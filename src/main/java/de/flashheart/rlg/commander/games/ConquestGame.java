package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ConquestTicketBleedingJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * lifecycle cleanup before new game is laoded (by GameService)
 */
@Log4j2
public class ConquestGame extends ScheduledGame {
    private final BigDecimal BLEEDING_CALCULATION_INTERVALL_IN_S = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final Map<String, FSM> agentFSMs;
    private final BigDecimal starting_tickets, ticket_price_to_respawn, minimum_cp_for_bleeding, interval_reduction_per_cp, starting_bleed_interval;
    private BigDecimal remaining_blue_tickets, remaining_red_tickets, cps_held_by_blue, cps_held_by_red;
    private JobKey ticketBleedingJobkey;
    private boolean game_in_prolog_state;

    /**
     * This class creates a conquest style game as known from Battlefield.
     * <p>
     * for the whole issue of ticket arithmetics please refer to https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542
     * for explanation
     * The mandatory parameters are handed down to this class via a JSONObject called <b>game_parameters</b>.
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
     * @param scheduler       internal scheduler reference as this class is NOT a spring component and
     *                        therefore cannot use DI.
     * @param mqttOutbound    internal mqtt reference as this class is NOT a spring component and therefore
     *                        cannot use DI.
     */
    public ConquestGame(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);
        this.starting_tickets = game_parameters.getBigDecimal("starting_tickets");
        this.minimum_cp_for_bleeding = game_parameters.getBigDecimal("minimum_cp_for_bleeding");
        this.starting_bleed_interval = game_parameters.getBigDecimal("starting_bleed_interval");
        this.interval_reduction_per_cp = game_parameters.getBigDecimal("interval_reduction_per_cp");
        this.ticket_price_to_respawn = game_parameters.getBigDecimal("ticket_price_to_respawn");
        ticketBleedingJobkey = new JobKey("ticketbleeding", name);
        agentFSMs = new HashMap<>();
        setGameDescription("Conquest",
                String.format("Bleeding every: %ds", starting_bleed_interval.intValue()),
                String.format("Bleeding @%s CPs", minimum_cp_for_bleeding.intValue()),
                String.format("Tickets: %s", starting_tickets.intValue()));

        roles.get("capture_points").forEach(agent -> agentFSMs.put(agent, createFSM(agent)));
        reset();
    }

    @Override
    public void react_to(String sender, JSONObject event) {
        if (!event.keySet().contains("button_pressed")) {
            log.debug("message is not for me. ignoring.");
            return;
        }

        // where did this message come from ?
        if (hasRole(sender, "red_spawn")) {
            // red respawn button was pressed
            mqttOutbound.sendSignalTo(sender, "buzzer", "single_buzz", "led_wht", "single_buzz");
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_to_respawn);
            broadcast_score();   // to make the respawn show up quicker. 
        } else if (hasRole(sender, "blue_spawn")) {
            // blue respawn button was pressed
            mqttOutbound.sendSignalTo(sender, "buzzer", "single_buzz", "led_wht", "single_buzz");
            remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_price_to_respawn);
            broadcast_score();   // to make the respawn show up quicker.
        } else if (hasRole(sender, "capture_points")) {
            agentFSMs.get(sender).ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("i don't care about this message.");
        }
    }

    private FSM createFSM(String agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest.xml"), null);
            /**
             * PROLOG => NEUTRAL
             */
            fsm.setAction("PROLOG", "START", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent, curState, nextState);
                    agent_to_neutral(agent);
                    broadcast_score();
                    return true;
                }
            });

            // switching back to NEUTRAL in case of misuse
            fsm.setAction(new ArrayList<>(Arrays.asList("RED", "BLUE")), "TO_NEUTRAL", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent, curState, nextState);
                    agent_to_neutral(agent);
                    return true;
                }
            });

            /**
             * NEUTRAL => BLUE
             */
            fsm.setAction("NEUTRAL", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent, curState, nextState);
                    agent_to_blue(agent);
                    broadcast_score();
                    return true;
                }
            });
            /**
             * BLUE => RED
             */
            fsm.setAction("BLUE", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent, curState, nextState);
                    agent_to_red(agent);
                    broadcast_score();
                    return true;
                }
            });
            /**
             * RED => BLUE
             */
            fsm.setAction("RED", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent, curState, nextState);
                    agent_to_blue(agent);
                    broadcast_score();
                    return true;
                }
            });

            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    private void agent_to_neutral(String agent) {
        mqttOutbound.sendSignalTo(agent, "led_all", "off", "led_wht", "normal");
    }

    private void agent_to_blue(String agent) {
        mqttOutbound.sendSignalTo(agent, "led_all", "off", "led_blu", "normal", "buzzer", "double_buzz");

    }

    private void agent_to_red(String agent) {
        mqttOutbound.sendSignalTo(agent, "led_all", "off", "led_red", "normal", "buzzer", "double_buzz");
    }

    @Override
    public void start() {
        if (!game_in_prolog_state) return;
        game_in_prolog_state = false;

        remaining_blue_tickets = starting_tickets;
        remaining_red_tickets = starting_tickets;
        cps_held_by_blue = BigDecimal.ZERO;
        cps_held_by_red = BigDecimal.ZERO;

        mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("START"));

        // setup and start bleeding job
        long repeat_every_ms = BLEEDING_CALCULATION_INTERVALL_IN_S.multiply(BigDecimal.valueOf(1000l)).longValue();

        create_job(ticketBleedingJobkey, simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever(), ConquestTicketBleedingJob.class);
        broadcast_cycle_counter = 0;
    }

    public void ticket_bleeding_cycle() {
        if (pausing_since.isPresent()) return; // as we are pausing, we are not doing anything

        broadcast_cycle_counter++;
        cps_held_by_blue = BigDecimal.valueOf(agentFSMs.values().stream().filter(fsm -> fsm.getCurrentState().equalsIgnoreCase("BLUE")).count());
        cps_held_by_red = BigDecimal.valueOf(agentFSMs.values().stream().filter(fsm -> fsm.getCurrentState().equalsIgnoreCase("RED")).count());

        remaining_red_tickets = remaining_red_tickets.subtract(ticketLossPerIntervalWithCPsHeld(cps_held_by_blue));
        remaining_blue_tickets = remaining_blue_tickets.subtract(ticketLossPerIntervalWithCPsHeld(cps_held_by_red));

        if (broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_CYCLES == 0)
            broadcast_score();

        if (remaining_blue_tickets.intValue() <= 0 || remaining_red_tickets.intValue() <= 0) {
            broadcast_score();
            game_over();
        }

    }

    private void game_over() {
        deleteJob(ticketBleedingJobkey); // this cycle has no use anymore
        // broadcast_score();
        String outcome = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "Team Red" : "Team Blue";
        String winner = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "led_red" : "led_blu";
        mqttOutbound.sendSignalTo("all", winner, "normal");
        MQTT.page0("Game Over", "Red: " + remaining_red_tickets.intValue() + " Blue: " + remaining_blue_tickets.intValue(), "The Winner is", outcome);

        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("GAME_OVER"));
        mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
    }

    private void broadcast_score() {

        mqttOutbound.sendCommandTo("all",
                MQTT.page0("Red: " + remaining_red_tickets.intValue() + " Tickets",
                        cps_held_by_red.intValue() + " flag(s)",
                        "Blue: " + remaining_blue_tickets.intValue() + " Tickets",
                        cps_held_by_blue.intValue() + " flag(s)")
        );
        log.debug("Cp: R{} B{}", cps_held_by_red.intValue(), cps_held_by_blue.intValue());
        log.debug("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());
    }

    /**
     * see https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542 for
     * explanation
     */
    private BigDecimal ticketLossPerIntervalWithCPsHeld(BigDecimal cps_held) {
        BigDecimal ticket_loss = BigDecimal.ZERO;
        if (cps_held.compareTo(minimum_cp_for_bleeding) >= 0) {
            BigDecimal OneTicketLostEveryNSeconds = starting_bleed_interval.subtract(cps_held.subtract(minimum_cp_for_bleeding).multiply(interval_reduction_per_cp));
            ticket_loss = BLEEDING_CALCULATION_INTERVALL_IN_S.divide(OneTicketLostEveryNSeconds, 4, RoundingMode.HALF_UP);
        }
        return ticket_loss;
    }


    @Override
    public void reset() {
        super.reset();
        game_in_prolog_state = true;
        mqttOutbound.sendSignalTo("capture_points", "led_all", "off", "led_wht", "slow");
        mqttOutbound.sendSignalTo("red_spawn", "led_all", "off", "led_red", "slow");
        mqttOutbound.sendSignalTo("blue_spawn", "led_all", "off", "led_blu", "slow");
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("RESET"));
    }

    @Override
    public JSONObject getStatus() {
        final JSONObject statusObject = super.getStatus()
                .put("mode", "conquest")
                .put("starting_tickets", starting_tickets)
                .put("ticket_price_to_respawn", ticket_price_to_respawn)
                .put("minimum_cp_for_bleeding", minimum_cp_for_bleeding)
                .put("interval_reduction_per_cp", interval_reduction_per_cp)
                .put("starting_bleed_interval", starting_bleed_interval)
                .put("remaining_blue_tickets", JSONObject.wrap(remaining_blue_tickets))
                .put("remaining_red_tickets", JSONObject.wrap(remaining_red_tickets))
                .put("cps_held_by_blue", JSONObject.wrap(cps_held_by_blue))
                .put("cps_held_by_red", JSONObject.wrap(cps_held_by_red));
        final JSONObject states = new JSONObject();
        agentFSMs.forEach((agentid, fsm) -> states.put(agentid, fsm.getCurrentState()));
        statusObject.put("states", states);
        return statusObject;
    }
}
