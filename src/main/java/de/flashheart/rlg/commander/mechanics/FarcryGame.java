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
import java.time.ZoneId;
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

    public FarcryGame(Multimap<String, Agent> function_to_agents, int flagcapturetime, int match_length, int respawn_period, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super("farcry", function_to_agents, match_length, scheduler, mqttOutbound);
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

                    estimated_end_time = start_time.plusSeconds(match_length);
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
                            signal("sir3", "off", "sir2", "1:on,500;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,500;off,500"));

                    mqttOutbound.sendCommandTo("all",
                            page_content("page0", "Restspielzeit", "${remaining}", "", "Bombe ist scharf"));

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
                            page_content("page0", "Restspielzeit", "${remaining}", "", "Bombe ist entschärft"));

                    // subtract the seconds since the start of the game from the match_length. this would be the remaining time
                    // if we are in overtime the result is negative, hence shifting the estimated_end_time into the past
                    // this will trigger the "TIMES_UP" event
                    // todo: klappt noch nicht im sudden death
                    estimated_end_time = start_time.plusSeconds(match_length); //LocalDateTime.now().plusSeconds(match_length - start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS));
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

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));

                    log.debug(start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond());
                    log.debug(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond());
                    // epochsecond because nanos are too precise
                    if (start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond() < LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond()) {
                        log.debug("SUDDEN DEATH");
                    }
                    // todo: overtime and sudden death

                    mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "GAME OVER", "", "Bomb defended.", "Winner: Team Green"));
                    mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "GAME OVER", "", "Bomb defended.", "Winner: Team Green"));

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

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000")
                    );
                    log.debug(start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond());
                    log.debug(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond());
                    // epochsecond because nanos are too precise
                    if (start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond() < LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond()) {
                        log.debug("OVERTIME");
                    }
                    mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "GAME OVER", "", "Bomb exploded.", "Winner: Team Red"));
                    mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "GAME OVER", "", "Bomb exploded.", "Winner: Team Red"));

                    return true;
                }
            });

//            farcryFSM.setAction(new ArrayList<>(
//                    Arrays.asList("CAPTURED", "DEFENDED", "RED", "GREEN")), "INIT", new FSMAction() {
//                @Override
//                public boolean action(String curState, String message, String nextState, Object args) {
//                    log.info("{} =====> {}", curState, nextState);
//                    //todo: page änderungen zusammenfassen
//                    mqttOutbound.sendCommandTo("red_spawn",
//                            page_content("page0", "Game Mode", "FarCry"));
//                    mqttOutbound.sendCommandTo("green_spawn",
//                            page_content("page0", "Game Mode", "FarCry"));
//                    mqttOutbound.sendCommandTo("leds",
//                            signal("led_all", "∞:on,1000;off,1000"));
//                    mqttOutbound.sendCommandTo("sirens",
//                            signal("led_all", "off"));
//                    mqttOutbound.sendCommandTo("red_spawn",
//                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));
//                    mqttOutbound.sendCommandTo("green_spawn",
//                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));
//                    return true;
//                }
//            });
//
//
            farcryFSM.setAction(new ArrayList<>(
                    Arrays.asList("PROLOG")), "INIT", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    //todo: page änderungen zusammenfassen
                    mqttOutbound.sendCommandTo("all",
                            page_content("page0", "FarCry loaded", "", "Ready to start", ""));
                    mqttOutbound.sendCommandTo("leds",
                            signal("led_all", "∞:on,1000;off,1000"));
                    mqttOutbound.sendCommandTo("sirens",
                            signal("led_all", "off"));
                    mqttOutbound.sendCommandTo("red_spawn",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));
                    mqttOutbound.sendCommandTo("green_spawn",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));
                    return true;
                }
            });

            react_to("INIT");
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            System.exit(1);
        }
    }

    @Override
    public void start() {
        if (!farcryFSM.getCurrentState().equalsIgnoreCase("PROLOG")) react_to("INIT");

        super.start();
        // start the respawn timer job.
        deleteJob(myRespawnJobKey);

        if (respawn_period > 0) { // respawn_period == 0 means we should not care about it

            mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "Restspielzeit", "${remaining}", "Press Button and wait", "for RESPAWN"));
            mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "Restspielzeit", "${remaining}", "Press Button and wait", "for RESPAWN"));

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
        } else {
            mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "Restspielzeit", "${remaining}", "", ""));
            mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "Restspielzeit", "${remaining}", "", ""));
        }
        react_to("START");
    }

    @Override
    public void time_is_up() {
        react_to("TIMES_UP");
    }

    @Override
    public void react_to(String sender, JSONObject event) {
        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_internal")) {
            farcryFSM.ProcessFSM(event.getString("message"));
        } else if (function_to_agents.get("button").stream().anyMatch(agent -> agent.getAgentid().equalsIgnoreCase(sender))
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
        // todo: respawn
        log.debug("respawn message to be sent");
        mqttOutbound.sendCommandTo("red_spawn", page_content("page0", "", "", "Respawn at ", "now!"));
        mqttOutbound.sendCommandTo("green_spawn", page_content("page0", "", "", "Respawn", "now!"));
    }
}
