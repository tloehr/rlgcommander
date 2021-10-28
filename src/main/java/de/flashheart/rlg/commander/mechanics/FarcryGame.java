package de.flashheart.rlg.commander.mechanics;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.RespawnJob;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;


/**
 * Implementation for the FarCry 1 (2004) Assault Game Mode.
 */
@Log4j2
public class FarcryGame extends TimedGame implements HasRespawn {
    private final int flagcapturetime;
    private final int respawn_period;
    private FSM farcryFSM;
    final JobKey myRespawnJobKey;


    public FarcryGame(Multimap<String, Agent> agent_roles, int flagcapturetime, int match_length, int respawn_period, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        this("farcry", agent_roles, flagcapturetime, match_length, respawn_period, scheduler, mqttOutbound);
    }

    public FarcryGame(String name, Multimap<String, Agent> agent_roles, int flagcapturetime, int match_length, int respawn_period, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(name, agent_roles, match_length, scheduler, mqttOutbound);
        this.flagcapturetime = flagcapturetime;
        this.respawn_period = respawn_period;
        myRespawnJobKey = new JobKey(name + "-" + RespawnJob.name, name);
        init();
    }

    @Override
    public void init() {
        try {
            farcryFSM = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/farcry.xml"), null);
            /**
             * PROLOG => GREEN
             */
            farcryFSM.setAction("PROLOG", "START", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,2000;off,1000"));

                    estimated_end_time = LocalDateTime.now().plusSeconds(match_length);
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * GREEN => RED
             */
            farcryFSM.setAction("GREEN", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal("sir3", "off", "sir2", "1:on,1000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,500;off,500"));

                    mqttOutbound.sendCommandTo("all",
                            page_content("page0", "Bomb has been fused", "Bombe ist scharf"));

                    estimated_end_time = LocalDateTime.now().plusSeconds(flagcapturetime);
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * RED => GREEN
             */
            farcryFSM.setAction("RED", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(
                                    "sir2", "off",
                                    "sir3", "1:on,1000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,2000;off,1000"));

                    mqttOutbound.sendCommandTo("all",
                            page_content("page0", "Bomb has been DEfused", "Bombe ist entschärft"));

                    // subtract the seconds since the start of the game from the match_length. this would be the remaining time
                    // if we are in overtime the result is negative, hence shifting the estimated_end_time into the past
                    // this will trigger the "TIMES_UP" event
                    estimated_end_time = LocalDateTime.now().plusSeconds(match_length - start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS));
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * bomb was defused in the end. GREEN is WINNER
             */
            farcryFSM.setAction("GREEN", "TIMES_UP", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("all",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"),
                            page_content("page0", "Bomb defended", "Bombe verteidigt"));

                    return true;
                }
            });

            /**
             * bomb has exploded. RED is WINNER
             */
            farcryFSM.setAction("RED", "TIMES_UP", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(
                                    "sir_all", "off",
                                    "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("all",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"),
                            page_content("page0", "Bomb exploded", "Bombe ist explodiert"));

                    return true;
                }
            });

            farcryFSM.setAction(new ArrayList<>(
                    Arrays.asList("CAPTURED", "DEFENDED", "RED", "GREEN", "PROLOG")), "INIT", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("all",
                            page_content("page0", "Game Mode", "FarCry"));
                    mqttOutbound.sendCommandTo("sirens",
                            signal("sir_all", "off"));
                    mqttOutbound.sendCommandTo("leds",
                            signal("led_all", "∞:on,1000;off,1000"));
                    mqttOutbound.sendCommandTo("red_spawn",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));
                    mqttOutbound.sendCommandTo("green_spawn",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));
                    return true;
                }
            });

            farcryFSM.ProcessFSM("INIT");
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            System.exit(1);
        }
    }

    @Override
    public void start() {
        // start the respawn timer job.
        deleteJob(myRespawnJobKey);

        if (respawn_period > 0) {
            JobDetail job = newJob(RespawnJob.class)
                    .withIdentity(myRespawnJobKey)
                    .build();
            jobs.add(myRespawnJobKey);

            Trigger trigger = newTrigger()
                    .withIdentity(RespawnJob.name + "-trigger", name)
                    .startNow()
                    .withSchedule(simpleSchedule().withIntervalInSeconds(respawn_period).repeatForever())
                    .build();

            try {
                scheduler.scheduleJob(job, trigger);
            } catch (SchedulerException e) {
                log.fatal(e);
            }
        }

        react_to("START");
        super.start();
    }

    @Override
    public void react_to(String sender, JSONObject event) {
        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_internal")) {
            farcryFSM.ProcessFSM(event.getString("message"));
        } else if (agent_roles.get("button").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))
                && event.keySet().contains("button_pressed")) {
            farcryFSM.ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("mode", "farcry")
                .put("flag_capture_time", flagcapturetime)
                .put("respawn_period", respawn_period)
                .put("current_state", farcryFSM.getCurrentState());
    }

    @Override
    public void respawn() {
        // todo: respawn anfordern. Button drücken und auf respawn signal warten.
        log.debug("respawn message to be sent");
        mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "Respawn", "now!"));
        mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "Respawn", "now!"));
    }
}
