package de.flashheart.rlg.commander.mechanics;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ConquestTicketBleedingJob;
import de.flashheart.rlg.commander.misc.Tools;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * lifecycle * cleanup before new game is laoded (by GameService) *
 */
@Log4j2
public class ConquestGame extends ScheduledGame {
    private final BigDecimal BLEEDING_CALCULATION_INTERVALL_IN_S = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_CYCLES = 10;
    private long broadcast_cycle_counter;
    private final Map<String, FSM> agentFSMs;
    private final BigDecimal starting_tickets, ticket_price_to_respawn, minimum_cp_for_bleeding, interval_reduction_per_cp, starting_bleed_interval;
    private BigDecimal remaining_blue_tickets, remaining_red_tickets, cps_held_by_blue, cps_held_by_red;
    private JobKey bleedingJob;

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
        bleedingJob = new JobKey(name + "-" + ConquestTicketBleedingJob.name, name);
        agentFSMs = new HashMap<>();
        init();
    }

    @Override
    public void init() {
        function_to_agents.get("capture_points").forEach(agent -> agentFSMs.put(agent.getAgentid(), createFSM(agent)));
        set_all_to_prolog(false);
        mqttOutbound.sendCommandTo("red_spawn",
                signal(LED_ALL_OFF(), "led_red", "∞:on,2000;off,1000"));
        mqttOutbound.sendCommandTo("blue_spawn",
                signal(LED_ALL_OFF(), "led_blu", "∞:on,2000;off,1000"));
        mqttOutbound.sendCommandTo("sirens",
                signal(Tools.merge(SIR_ALL_OFF(), LED_ALL_OFF())));
    }

    @Override
    public void react_to(String sender, JSONObject event) {
        if (!event.keySet().contains("button_pressed")) {
            log.debug("message is not for me. ignoring.");
            return;
        }

        if (function_to_agents.get("red_spawn").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // red respawn button was pressed
            mqttOutbound.sendCommandTo(sender, signal("buzzer", "1:on,75;off,1", "led_wht", "1:on,75;off,1"));
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_to_respawn);
            broadcast_score();   // to make the respawn show up quicker. 
        } else if (function_to_agents.get("blue_spawn").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // blue respawn button was pressed
            mqttOutbound.sendCommandTo(sender, signal("buzzer", "1:on,75;off,1", "led_wht", "1:on,75;off,1"));
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
                    mqttOutbound.sendCommandTo(agent,
                            signal(LED_ALL_OFF(), "led_wht", "∞:on,2000;off,1000")
                    );
                    broadcast_score();
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
                    mqttOutbound.sendCommandTo(agent,
                            signal(LED_ALL_OFF(),
                                    "buzzer", "2:on,75;off,75",
                                    "led_blu", "∞:on,1000;off,1000")
                    );
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
                    mqttOutbound.sendCommandTo(agent,
                            signal(LED_ALL_OFF(),
                                    "buzzer", "2:on,75;off,75",
                                    "led_red", "∞:on,1000;off,1000")
                    );
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
                    mqttOutbound.sendCommandTo(agent,
                            signal(LED_ALL_OFF(),
                                    "buzzer", "2:on,75;off,75",
                                    "led_blu", "∞:on,1000;off,1000")
                    );
                    broadcast_score();
                    return true;
                }
            });

//            fsm.setAction(new ArrayList<>(
//                    Arrays.asList("RED", "BLUE", "NEUTRAL")), "GAME_OVER", new FSMAction() {
//                @Override
//                public boolean action(String curState, String message, String nextState, Object args) {
//                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
//                    mqttOutbound.sendCommandTo(agent,
//
//                    return true;
//                }
//            });
            // clearing all additional subscriptions from agents
            //fsm.ProcessFSM("INIT");
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    /**
     * where ever we are, NOW we are going to PROLOG
     */
    void set_all_to_prolog(boolean ignore_signals_for_capture_points) {
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("INIT"));
        if (ignore_signals_for_capture_points) return;
        mqttOutbound.sendCommandTo("capture_points",
                signal(Tools.merge(LED_ALL_OFF(), SIR_ALL_OFF()),
                        "led_red", "∞:on,750;off,750",
                        "led_blu", "∞:off,750;on,750"
                ));
        mqttOutbound.sendCommandTo("all",
                page_content("page0", "Loaded Game Mode:", "Conquest"));

    }

    @Override
    public void start() {
        set_all_to_prolog(true);
        remaining_blue_tickets = starting_tickets;
        remaining_red_tickets = starting_tickets;
        cps_held_by_blue = BigDecimal.ZERO;
        cps_held_by_red = BigDecimal.ZERO;

        mqttOutbound.sendCommandTo("sirens", signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("START"));

        // setup and start bleeding job
        long repeat_every_ms = BLEEDING_CALCULATION_INTERVALL_IN_S.multiply(BigDecimal.valueOf(1000l)).longValue();
        try {
            deleteJob(bleedingJob);
            JobDetail job = newJob(ConquestTicketBleedingJob.class)
                    .withIdentity(bleedingJob)
                    .usingJobData("name_of_the_game", name) // where we find the context later
                    .build();
            jobs.add(bleedingJob);

            Trigger trigger = newTrigger()
                    .withIdentity(ConquestTicketBleedingJob.name + "-trigger", name)
                    .startNow()
                    .withSchedule(simpleSchedule().withIntervalInMilliseconds(repeat_every_ms).repeatForever())
                    .build();
            broadcast_cycle_counter = 0;
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.fatal(e);
        }
    }

    public void ticket_bleeding_cycle() {
        if (pause_start_time != null) return; // as we are pausing, we are not doing anything

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
        deleteJob(bleedingJob); // this cycle has no use anymore
        // broadcast_score();
        String outcome = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "Team Red" : "Team Blue";
        String winner_led = remaining_red_tickets.intValue() > remaining_blue_tickets.intValue() ? "led_red" : "led_blu";
        mqttOutbound.sendCommandTo("all",
                signal(LED_ALL_OFF(), winner_led, "∞:on,1000;off,1000"),
                score("Red: " + remaining_red_tickets.intValue() + " Blue: " + remaining_blue_tickets.intValue()),
                page_content("page0", "The Winner is", outcome)
        );
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("GAME_OVER"));
        mqttOutbound.sendCommandTo("sirens", signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));
    }

    private void broadcast_score() {
        mqttOutbound.sendCommandTo("all",
                score("Red: " + remaining_red_tickets.intValue() + " Blue: " + remaining_blue_tickets.intValue()),
                page_content("page0", "Red: " + cps_held_by_red.intValue() + " flag(s)", "Blue: " + cps_held_by_blue.intValue() + " flag(s)")
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
