package de.flashheart.rlg.commander.mechanics;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.RespawnJob;
import de.flashheart.rlg.commander.service.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;


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
        myRespawnJobKey = new JobKey("respawn", name);
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

                    mqttOutbound.sendCommandTo("all",
                            MQTT.page_content("page0", "", "", "", ""));

                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,2000;off,1000"));

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));

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

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "Restspielzeit", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));

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

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));

                    // subtract the seconds since the start of the game from the match_length. this would be the remaining time
                    // if we are in overtime the result is negative, hence shifting the estimated_end_time into the past
                    // this will trigger the "GAME_OVER" event
                    // das problem ist, dass wir vorher nochmal schnell in modus grün umschalten müssen, wenn
                    estimated_end_time = start_time.plusSeconds(match_length); //LocalDateTime.now().plusSeconds(match_length - start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS));
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * bomb was defused in the end. GREEN is WINNER
             */
            farcryFSM.setAction("GREEN", "GAME_OVER", new FSMAction() {
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
                    String overtime = "";
                    if (start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond() < LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond()) {
                        overtime = "in der Nachspielzeit";
                    }

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "GAME OVER", overtime, "Bomb verteidigt", "Sieger: Team GRÜN"));

                    return true;
                }
            });

            /**
             * bomb has exploded. RED is WINNER
             */
            farcryFSM.setAction("RED", "GAME_OVER", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));
                    log.debug(start_time.plusSeconds(match_length).atZone(ZoneId.systemDefault()).toEpochSecond());
                    log.debug(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond());

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "GAME OVER", "", "Bombe explodiert", "Sieger: Team ROT"));
                    return true;
                }
            });

            /**
             * bomb has exploded during OVERTIME. RED is WINNER
             */
            farcryFSM.setAction("RED_OVERTIME", "GAME_OVER", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "GAME OVER", "--OVERTIME--", "Bombe explodiert", "Sieger: Team ROT"));
                    return true;
                }
            });

            /**
             * bomb was defused in the end. GREEN is WINNER
             */
            farcryFSM.setAction("RED_OVERTIME", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendCommandTo("sirens",
                            signal(SIR_ALL_OFF(), "sir1", "1:on,5000;off,1"));

                    mqttOutbound.sendCommandTo("leds",
                            signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));

                    mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "GAME OVER", "--OVERTIME--", "Bomb verteidigt", "Sieger: Team GRÜN"));

                    return true;
                }
            });


//            farcryFSM.setAction(new ArrayList<>(
//                    Arrays.asList("PROLOG")), "RESET", new FSMAction() {
//                @Override
//                public boolean action(String curState, String message, String nextState, Object args) {
//                    log.info("{} =====> {}", curState, nextState);
//                    mqttOutbound.sendCommandTo("all",
//                            MQTT.page_content("page0", "FarCry loaded", "", "Ready to start", ""));
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

            //react_to("RESET");
            reset();
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            System.exit(1);
        }
    }

    @Override
    public void start() {
        if (!farcryFSM.getCurrentState().equalsIgnoreCase("PROLOG")) reset();
        super.start();
        // (re)start the respawn timer job.
        deleteJob(myRespawnJobKey);
        if (respawn_period > 0) { // respawn_period == 0 means we should not care about it
            create_job(myRespawnJobKey, simpleSchedule().withIntervalInSeconds(respawn_period).repeatForever(), RespawnJob.class);
        } else {
            mqttOutbound.sendCommandTo("spawns", MQTT.page_content("page0", "Restspielzeit", "${remaining}", "", ""));
        }
        react_to("START");
    }

    @Override
    public void game_over() {
        deleteJob(myRespawnJobKey);
        mqttOutbound.sendCommandTo("spawns", TIMERS("respawn", "-1"), signal("buzzer", "off"));
        react_to("GAME_OVER");
    }

    @Override
    public void overtime() {
        log.debug("overtime");
        react_to("OVERTIME");
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
    public void reset() {
        mqttOutbound.sendCommandTo("all",
                MQTT.page_content("page0", "FarCry loaded", "", "Ready to start", ""));
        mqttOutbound.sendCommandTo("leds",
                signal("led_all", "∞:on,1000;off,1000"));
        mqttOutbound.sendCommandTo("sirens",
                signal("led_all", "off"));
        mqttOutbound.sendCommandTo("red_spawn",
                signal(LED_ALL_OFF(), "led_red", "∞:on,1000;off,1000"));
        mqttOutbound.sendCommandTo("green_spawn",
                signal(LED_ALL_OFF(), "led_grn", "∞:on,1000;off,1000"));
        react_to("RESET");
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
        // the last part of this message is a delayed buzzer sound, so its end lines up with the end of the respawn period
        mqttOutbound.sendCommandTo("spawns", TIMERS("respawn", Long.toString(respawn_period)), signal("buzzer", String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_period * 1000 - 2925 - 100)));
    }
}
