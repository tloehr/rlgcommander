package de.flashheart.rlg.commander.mechanics;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.ConquestTicketBleedingJob;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * lifecycle
 * * cleanup before new game is laoded (by GameService)
 *  *
 */
@Log4j2
public class ConquestGame extends ScheduledGame {
    private final BigDecimal BLEEDING_CALCULATION_INTERVALL_IN_S = BigDecimal.valueOf(0.5d);
    private final long BROADCAST_SCORE_EVERY_N_CYCLES = 5;
    private long broadcast_cycle_counter;
    private final Map<String, FSM> agentFSMs;
    private Multimap<String, Agent> agent_roles;
    private final BigDecimal starting_tickets, ticket_price_to_respawn, minimum_cp_for_bleeding, interval_reduction_per_cp, starting_bleed_interval;
    private BigDecimal remaining_blue_tickets, remaining_red_tickets, cps_held_by_blue, cps_held_by_red;
    private JobKey bleedingJob;

    public ConquestGame(Multimap<String, Agent> agent_roles, BigDecimal starting_tickets, BigDecimal ticket_price_to_respawn, BigDecimal minimum_cp_for_bleeding, BigDecimal starting_bleed_interval, BigDecimal interval_reduction_per_cp, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        this("conquest", agent_roles, starting_tickets, ticket_price_to_respawn, minimum_cp_for_bleeding, starting_bleed_interval, interval_reduction_per_cp, scheduler, mqttOutbound);
    }

    public ConquestGame(String name, Multimap<String, Agent> agent_roles, BigDecimal starting_tickets, BigDecimal ticket_price_to_respawn, BigDecimal minimum_cp_for_bleeding, BigDecimal starting_bleed_interval, BigDecimal interval_reduction_per_cp, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, scheduler, mqttOutbound);
        this.agent_roles = agent_roles;
        this.starting_tickets = starting_tickets;
        this.minimum_cp_for_bleeding = minimum_cp_for_bleeding;
        this.starting_bleed_interval = starting_bleed_interval;
        this.interval_reduction_per_cp = interval_reduction_per_cp;
        this.ticket_price_to_respawn = ticket_price_to_respawn;
        bleedingJob = new JobKey(name + "-" + ConquestTicketBleedingJob.name, name);
        agentFSMs = new HashMap<>();
        agent_roles.get("cp_agents").forEach(agent -> agentFSMs.put(agent.getAgentid(), createFSM(agent)));
        init();
    }

    @Override
    public void init() {
        agent_roles.get("red_spawn_agent").forEach(agent -> {
            mqttOutbound.sendCommandTo("agent", getSignal(
                    new JSONObject()
                            .put("led_all", "off")
                            .put("led_red", "∞:off,2000;on,1000"))
                    .put("line_display", new JSONArray(new String[]{"Press Button", "to Respawn"}))
            );
        });

        agent_roles.get("blue_spawn_agent").forEach(agent -> {
            mqttOutbound.sendCommandTo("agent", getSignal(
                    new JSONObject()
                            .put("led_all", "off")
                            .put("led_blu", "∞:off,2000;on,1000"))
                    .put("line_display", new JSONArray(new String[]{"Press Button", "to Respawn"}))
            );
        });
    }


    @Override
    public void react_to(String sender, String message) {
        if (agent_roles.get("red_spawn_agent").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // red respawn button was pressed
            mqttOutbound.sendCommandTo(sender, getSignal(
                    new JSONObject()
                            .put("buzzer", "2:on,100;off,100")
                            .put("led_wht", "2:on,100;off,100"))
                    .put("line_display", new JSONArray(new String[]{"Press Button", "to Respawn"}))
            );
            remaining_red_tickets = remaining_red_tickets.subtract(ticket_price_to_respawn);
        } else if (agent_roles.get("blue_spawn_agent").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))) {
            // blue respawn button was pressed
            mqttOutbound.sendCommandTo(sender, getSignal(
                    new JSONObject()
                            .put("buzzer", "2:on,100;off,100")
                            .put("led_wht", "2:on,100;off,100"))
                    .put("line_display", new JSONArray(new String[]{"Press Button", "to Respawn"}))
            );
            remaining_blue_tickets = remaining_blue_tickets.subtract(ticket_price_to_respawn);
        } else if (agentFSMs.containsKey(sender)) {
            agentFSMs.get(sender).ProcessFSM(message.toUpperCase());
            log.debug("process: " + message);
        } else {
            log.debug(message + " is not for me. ignoring.");
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
                            getSignal(
                                    new JSONObject()
                                            .put("led_all", "off")
                                            .put("led_wht", "∞:on,1000;off,1000")
                            )
                    );
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
                            getSignal(
                                    new JSONObject()
                                            .put("buzzer", "2:on,125;off,125")
                                            .put("led_all", "off")
                                            .put("led_blu", "∞:on,1000;off,1000")
                            )
                    );
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
                            getSignal(
                                    new JSONObject()
                                            .put("buzzer", "2:on,125;off,125")
                                            .put("led_all", "off")
                                            .put("led_red", "∞:on,1000;off,1000")
                            )
                    );
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
                            getSignal(
                                    new JSONObject()
                                            .put("buzzer", "2:on,125;off,125")
                                            .put("led_all", "off")
                                            .put("led_blu", "∞:on,1000;off,1000")
                            )
                    );

                    return true;
                }
            });

            fsm.setAction(new ArrayList<>(
                    Arrays.asList("GAME_OVER", "RED", "BLUE", "PROLOG")), "INIT", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", agent.getAgentid(), curState, nextState);
                    mqttOutbound.sendCommandTo(agent,
                            getSignal(
                                    new JSONObject()
                                            .put("sir_all", "off")
                                            .put("led_all", "off")
                                            .put("led_red", "∞:on,750;off,750")
                                            .put("led_blu", "∞:off,750;on,750"))
                                    .put("line_display", new JSONArray(new String[]{"Loaded Game Mode", "Conquest"}))
                    );
                    return true;
                }
            });
            fsm.ProcessFSM("INIT");
            // todo: subscribe to subchannels
            return fsm;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void start() {
        remaining_blue_tickets = starting_tickets;
        remaining_red_tickets = starting_tickets;
        cps_held_by_blue = BigDecimal.ZERO;
        cps_held_by_red = BigDecimal.ZERO;

        mqttOutbound.sendCommandTo("siren_agents", getSignal(
                new JSONObject()
                        .put("sir_all", "off")
                        .put("sir1", "1:on,5000;off,1"))
        );
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("START"));

        // setup and start bleeding job
        long repeat_every_ms = BLEEDING_CALCULATION_INTERVALL_IN_S.multiply(BigDecimal.valueOf(1000l)).longValue();
        try {
            deleteJob(bleedingJob);
            JobDetail job = newJob(ConquestTicketBleedingJob.class)
                    .withIdentity(bleedingJob)
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

    @Override
    public void stop() {
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("GAME_OVER"));
        mqttOutbound.sendCommandTo("siren_agents", getSignal(
                new JSONObject()
                        .put("sir_all", "off")
                        .put("sir1", "1:on,5000;off,1"))
        );

        //mqttOutbound.agentStandbyPattern(agent_roles.get("cp_agents"));
        super.stop();
    }

    @Override
    public void cleanup() {
        super.cleanup();
    }

    public void ticket_bleeding_cycle() {
        if (pause_start_time != null) return; // as we are pausing, we are not doing anything

        broadcast_cycle_counter++;
        cps_held_by_blue = BigDecimal.valueOf(agentFSMs.values().stream().filter(fsm -> fsm.getCurrentState().equalsIgnoreCase("BLUE")).count());
        cps_held_by_red = BigDecimal.valueOf(agentFSMs.values().stream().filter(fsm -> fsm.getCurrentState().equalsIgnoreCase("RED")).count());

        remaining_red_tickets = remaining_red_tickets.subtract(ticketsLostWhen(cps_held_by_blue));
        remaining_blue_tickets = remaining_blue_tickets.subtract(ticketsLostWhen(cps_held_by_red));

        if (broadcast_cycle_counter % BROADCAST_SCORE_EVERY_N_CYCLES != 0)
            broadcast_score();

        if (remaining_blue_tickets.compareTo(BigDecimal.ZERO) <= 0 || remaining_red_tickets.compareTo(BigDecimal.ZERO) <= 0)
            stop();

        log.debug("Cp: R{} B{}", cps_held_by_red.intValue(), cps_held_by_blue.intValue());
        log.debug("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());
    }

    private void broadcast_score() {
        mqttOutbound.sendCommandTo("cp_agents", getSignal(
                new JSONObject()
                        .put("sir_all", "off")
                        .put("sir1", "1:on,5000;off,1"))
        );

        mqttOutbound.sendCommandTo(agent_roles.get("cp_agents"),
                new JSONObject()
                        .put("line_display", new JSONArray(new String[]{"Cp: R" + cps_held_by_red + " B" + cps_held_by_blue, "Tk: R" + remaining_red_tickets + " B" + remaining_blue_tickets}))
        );
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
