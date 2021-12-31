package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.RespawnJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

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

    public FarcryGame(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);
        log.debug("\n   ____         _____\n" +
                "  / __/__ _____/ ___/_____ __\n" +
                " / _// _ `/ __/ /__/ __/ // /\n" +
                "/_/  \\_,_/_/  \\___/_/  \\_, /\n" +
                "                      /___/");
        this.flagcapturetime = game_parameters.getInt("flag_capture_time");
        this.respawn_period = game_parameters.getInt("respawn_period");
        myRespawnJobKey = new JobKey("respawn", name);
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(flagcapturetime), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawnTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_period), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(match_length), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription("FarCry Assault/Rush",
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                respawn_period == 0 ? "Respawns not handled" : String.format("Respawn-Time: %s", ldtRespawnTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Spielzeit: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))));
        create_FSM();
        reset();
    }

    public void create_FSM() {
        try {
            farcryFSM = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/farcry.xml"), null);
            /**
             * PROLOG => BLUE
             */
            farcryFSM.setAction("PROLOG", "START", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
                    mqttOutbound.sendSignalTo("leds", "led_all", "off", "led_blu", "slow");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));
                    estimated_end_time = start_time.plusSeconds(match_length);
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * BLUE => RED
             */
            farcryFSM.setAction("BLUE", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("sirens", "sir2", "short");
                    mqttOutbound.sendSignalTo("leds", "led_blu", "off", "led_red", "fast");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "Restspielzeit", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));
                    estimated_end_time = LocalDateTime.now().plusSeconds(flagcapturetime);
                    monitorRemainingTime();
                    return true;
                }
            });

            /**
             * RED => BLUE
             */
            farcryFSM.setAction("RED", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("sirens", "sir3", "short");
                    mqttOutbound.sendSignalTo("leds", "led_blu", "slow", "led_red", "off");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));
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
             * bomb was defused in the end. BLUE is WINNER
             */
            farcryFSM.setAction("BLUE", "GAME_OVER", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
                    mqttOutbound.sendSignalTo("leds", "led_blu", "normal");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "GAME OVER", "", "Bombe verteidigt", "Sieger: Team GRÜN"));
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
                    mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
                    mqttOutbound.sendSignalTo("leds", "led_red", "normal");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "GAME OVER", "", "Bombe explodiert", "Sieger: Team ROT"));
                    return true;
                }
            });

            /**
             * Game time is up, but bomb is still fused. Going into Overtime
             */
            farcryFSM.setAction("RED", "OVERTIME", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("leds", "led_red", "very_fast");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "--OVERTIME--", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""));
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
                    mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
                    mqttOutbound.sendSignalTo("leds", "led_red", "normal");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "GAME OVER", "--OVERTIME--", "Bombe explodiert", "Sieger: Team ROT"));
                    return true;
                }
            });

            /**
             * bomb was defused in overtime. BLUE is WINNER.
             */
            farcryFSM.setAction("RED_OVERTIME", "BTN01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.sendSignalTo("sirens", "sir1", "very_long");
                    mqttOutbound.sendSignalTo("leds", "led_blu", "normal");
                    mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "GAME OVER", "--OVERTIME--", "Bomb verteidigt", "Sieger: Team GRÜN"));
                    return true;
                }
            });
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
            mqttOutbound.sendCommandTo("spawns", MQTT.page0("", "Restspielzeit", "${remaining}", "", ""));
        }
        react_to("START");
    }

    @Override
    public void game_over() {
        deleteJob(myRespawnJobKey);
        mqttOutbound.sendCommandTo("spawns", TIMERS("respawn", "-1"), MQTT.signal("buzzer", "off"));
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
        } else if (hasRole(sender, "button") && event.keySet().contains("button_pressed")) {
            farcryFSM.ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }

    @Override
    public void reset() {
        super.reset();
        mqttOutbound.sendSignalTo("leds", "led_red", "normal", "led_blu", "normal");
        mqttOutbound.sendSignalTo("red_spawn", "led_red", "normal");
        mqttOutbound.sendSignalTo("blue_spawn", "led_blu", "normal");
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
        mqttOutbound.sendCommandTo("spawns", TIMERS("respawn", Long.toString(respawn_period)), MQTT.signal("buzzer", String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_period * 1000 - 2925 - 100)));
    }
}
