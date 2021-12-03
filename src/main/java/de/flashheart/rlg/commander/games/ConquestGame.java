package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ConquestTicketBleedingJob;
import de.flashheart.rlg.commander.service.Agent;
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
     * This class creates a conquest style game as we know it from Battlefield.
     * <p>
     * for the whole issue of ticket arithmetics please refer to https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542
     * for explanation
     *
     * @param function_to_agents        is a multimap which contains the list of agents sorted by their purpose. Known
     *                                  keys for this map are: <b>"red_spawn", "blue_spawn", "capture_points",
     *                                  "sirens"</b>. The class handles as <b>many capture points</b> as there are
     *                                  listed under "capture_points".
     * @param starting_tickets          the number of tickets for each team when the game start
     * @param ticket_price_to_respawn   the price a team has to pay when a player respawns
     * @param minimum_cp_for_bleeding   the minimum number of capture points a team has to hold before the ticket
     *                                  bleeding starts for the opposing team
     * @param starting_bleed_interval   the number of seconds for the first bleeding interval
     * @param interval_reduction_per_cp the number of seconds the bleeding interval shortens for every newly captured
     *                                  point
     * @param scheduler                 internal scheduler reference as this class is NOT a spring component and
     *                                  therefore cannot use DI.
     * @param mqttOutbound              internal mqtt reference as this class is NOT a spring component and therefore
     *                                  cannot use DI.
     */
    public ConquestGame(Multimap<String, Agent> function_to_agents, BigDecimal starting_tickets, BigDecimal ticket_price_to_respawn, BigDecimal minimum_cp_for_bleeding, BigDecimal starting_bleed_interval, BigDecimal interval_reduction_per_cp, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super("conquest", function_to_agents, scheduler, mqttOutbound);
        this.starting_tickets = starting_tickets;
        this.minimum_cp_for_bleeding = minimum_cp_for_bleeding;
        this.starting_bleed_interval = starting_bleed_interval;
        this.interval_reduction_per_cp = interval_reduction_per_cp;
        this.ticket_price_to_respawn = ticket_price_to_respawn;
        ticketBleedingJobkey = new JobKey("ticketbleeding", name);
        agentFSMs = new HashMap<>();
        setGameDescription("Conquest",
                String.format("Bleeding every: %ds", starting_bleed_interval.intValue()),
                String.format("Bleeding @%s CPs", minimum_cp_for_bleeding.intValue()),
                String.format("Tickets: %s", starting_tickets.intValue()));
        function_to_agents.get("capture_points").forEach(agent -> agentFSMs.put(agent.getAgentid(), createFSM(agent)));
        reset();
    }

    @Override
    public void react_to(String sender, JSONObject event) {
        if (!event.keySet().contains("button_pressed")) {
            log.debug("message is not for me. ignoring.");
            return;
        }

        // where did this message come from ?
        if (function_to_agents.get("red_spawn").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // red respawn button was pressed
            mqttOutbound.sendCommandTo(sender, MQTT.signal("buzzer", "1:on,75;off,1", "led_wht", "1:on,75;off,1"));
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_to_respawn);
            broadcast_score();   // to make the respawn show up quicker. 
        } else if (function_to_agents.get("blue_spawn").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // blue respawn button was pressed
            mqttOutbound.sendCommandTo(sender, MQTT.signal("buzzer", "1:on,75;off,1", "led_wht", "1:on,75;off,1"));
            remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_price_to_respawn);
            broadcast_score();   // to make the respawn show up quicker.
        } else if (function_to_agents.get("capture_points").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            agentFSMs.get(sender).ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }

    private FSM createFSM(Agent agent) {
        try {
            FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/conquest.xml"), null);
            /**
             * PROLOG => NEUTRAL
             */
            fsm.setAction("PROLOG", "START", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
                    agent_to_neutral(agent);
                    broadcast_score();
                    return true;
                }
            });

            // switching back to NEUTRAL in case of player error
            fsm.setAction(new ArrayList<>(Arrays.asList("RED", "BLUE")), "TO_NEUTRAL", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
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
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
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
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
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
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
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

    private void agent_to_neutral(Agent agent) {
        mqttOutbound.sendSignalTo(agent, "led_wht", "normal");
    }

    private void agent_to_blue(Agent agent) {
        mqttOutbound.sendSignalTo(agent, "led_blu", "normal");
        mqttOutbound.sendCommandTo(agent, MQTT.signal(MQTT.LED_ALL_OFF(), "buzzer", "2:on,75;off,75"));
    }

    private void agent_to_red(Agent agent) {
        mqttOutbound.sendSignalTo(agent, "led_red", "normal");
        mqttOutbound.sendCommandTo(agent, MQTT.signal(MQTT.LED_ALL_OFF(), "buzzer", "2:on,75;off,75"));
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

        remaining_red_tickets = remaining_red_tickets.subtract(ticketsLostWhen(cps_held_by_blue));
        remaining_blue_tickets = remaining_blue_tickets.subtract(ticketsLostWhen(cps_held_by_red));

        if (broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_CYCLES == 0)
            broadcast_score();

        if (remaining_blue_tickets.intValue() <= 0 || remaining_red_tickets.intValue() <= 0) {
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
                MQTT.page0("", "Red: " + remaining_red_tickets.intValue() + " Blue: " + remaining_blue_tickets.intValue(), "The Winner is", "Red: " + cps_held_by_red.intValue() + " flag(s)", "Blue: " + cps_held_by_blue.intValue() + " flag(s)")
        );
        log.debug("Cp: R{} B{}", cps_held_by_red.intValue(), cps_held_by_blue.intValue());
        log.debug("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());
    }

    /**
     * see https://docs.google.com/spreadsheets/d/12n3uIMWDDaWNhwpBvZZj6vMfb8PXBTtxsnlT8xJ9M2k/edit#gid=457469542 for
     * explanation
     */
    private BigDecimal ticketsLostWhen(BigDecimal cps_held) {
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
        mqttOutbound.sendSignalTo("capture_points", "led_wht", "slow");
        mqttOutbound.sendSignalTo("red_spawn", "led_red", "slow");
        mqttOutbound.sendSignalTo("blue_spawn", "led_blu", "slow");
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
