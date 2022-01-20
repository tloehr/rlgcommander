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

    public FarcryGame(String id, JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(id, game_parameters, scheduler, mqttOutbound);
        log.debug("\n   ____         _____\n" +
                "  / __/__ _____/ ___/_____ __\n" +
                " / _// _ `/ __/ /__/ __/ // /\n" +
                "/_/  \\_,_/_/  \\___/_/  \\_, /\n" +
                "                      /___/");
        this.flagcapturetime = game_parameters.getInt("flag_capture_time");
        this.respawn_period = game_parameters.getInt("respawn_period");
        myRespawnJobKey = new JobKey("respawn", id);
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(flagcapturetime), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtRespawnTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(respawn_period), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(match_length), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir1", "very_long"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_blu", "slow"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir2", "short"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_blu", "off", "led_red", "fast"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "Restspielzeit", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir3", "short"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("leds", "led_blu", "slow", "led_red", "off"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "Restspielzeit", "${remaining}", "Bombe NICHT scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir1", "very_long"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_blu", "normal", "led_red", "off"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "GAME OVER", "", "Bombe verteidigt", "Sieger: Team BLAU"), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir1", "very_long"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal", "led_blu", "off"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "", "GAME OVER", "", "Bombe explodiert", "Sieger: Team ROT"), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("led_red", "very_fast"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "", "--OVERTIME--", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir1", "very_long"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "", "GAME OVER", "--OVERTIME--", "Bombe explodiert", "Sieger: Team ROT"), roles.get("spawns"));
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
                    mqttOutbound.send("signals", MQTT.toJSON("sir1", "very_long"), roles.get("sirens"));
                    mqttOutbound.send("signals", MQTT.toJSON("led_blu", "normal"), roles.get("leds"));
                    mqttOutbound.send("paged", MQTT.page("page0", "", "GAME OVER", "--OVERTIME--", "Bomb verteidigt", "Sieger: Team GRÜN"), roles.get("spawns"));
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
        try {
            super.start();
        } catch (IllegalStateException e) {
            return;
        }
        // (re)start the respawn timer job.
        deleteJob(myRespawnJobKey);
        if (respawn_period > 0) { // respawn_period == 0 means we should not care about it
            create_job(myRespawnJobKey, simpleSchedule().withIntervalInSeconds(respawn_period).repeatForever(), RespawnJob.class);
        } else {
            mqttOutbound.send("paged", MQTT.page("page0", "", "Restspielzeit", "${remaining}", "", ""), roles.get("spawns"));
        }
        trigger_internal_event("START");
    }

    @Override
    public void game_over() {
        super.game_over();
        deleteJob(myRespawnJobKey);
        mqttOutbound.send("signals", MQTT.toJSON("buzzer", "off"), roles.get("spawns"));
        mqttOutbound.send("timers", MQTT.toJSON("respawn", "-1"), roles.get("spawns"));
        trigger_internal_event("GAME_OVER");
    }

    @Override
    public void overtime() {
        log.debug("overtime");
        trigger_internal_event("OVERTIME");
    }

    @Override
    public void react_to(String sender, String source, JSONObject event) {
        try {
            super.react_to(sender, source, event);
        } catch (IllegalStateException e) {
            return;
        }
        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_internal")) {
            farcryFSM.ProcessFSM(event.getString("message"));
        } else if (hasRole(sender, "button") && event.getString("button").equalsIgnoreCase("up")) {
            farcryFSM.ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }

    @Override
    public void reset() {
        super.reset();
        mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal", "led_blu", "normal"), roles.get("leds"));
        mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal"), roles.get("red_spawn"));
        mqttOutbound.send("signals", MQTT.toJSON("led_blu", "normal"), roles.get("blue_spawn"));
        trigger_internal_event("RESET");
    }

    @Override
    public JSONObject getStatus() {
        return super.getStatus()
                .put("flag_capture_time", flagcapturetime)
                .put("respawn_period", respawn_period)
                .put("current_state", farcryFSM.getCurrentState());
    }

    @Override
    public void respawn() {
        // the last part of this message is a delayed buzzer sound, so its end lines up with the end of the respawn period
        mqttOutbound.send("signals", MQTT.toJSON("buzzer", String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_period * 1000 - 2925 - 100)), roles.get("spawns"));
        mqttOutbound.send("timers", MQTT.toJSON("respawn", Long.toString(respawn_period)), roles.get("spawns"));
    }
}
