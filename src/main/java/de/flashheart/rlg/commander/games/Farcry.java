package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.BombTimerJob;
import de.flashheart.rlg.commander.jobs.RespawnJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.TimeZone;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;


/**
 * Implementation for the FarCry 1 (2004) Assault Game Mode.
 */
@Log4j2
public class Farcry extends Timed {
    private final int bomb_timer;
    private FSM farcryFSM;
    private final JobKey bombTimerJobkey;
    private LocalDateTime estimated_end_time;

    public Farcry(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException {
        super(game_parameters, scheduler, mqttOutbound);
        estimated_end_time = null;
        log.debug("\n   ____         _____\n" +
                "  / __/__ _____/ ___/_____ __\n" +
                " / _// _ `/ __/ /__/ __/ // /\n" +
                "/_/  \\_,_/_/  \\___/_/  \\_, /\n" +
                "                      /___/");
        this.bombTimerJobkey = new JobKey("bomb_timer", uuid.toString());
        this.bomb_timer = game_parameters.getInt("bomb_time");
        LocalDateTime ldtFlagTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(bomb_timer), TimeZone.getTimeZone("UTC").toZoneId());
        LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(game_time), TimeZone.getTimeZone("UTC").toZoneId());
        setGameDescription(game_parameters.getString("comment"),
                String.format("Bombtime: %s", ldtFlagTime.format(DateTimeFormatter.ofPattern("mm:ss"))),
                String.format("Gametime: %s", ldtTime.format(DateTimeFormatter.ofPattern("mm:ss"))));

        roles.get("capture_point").stream().findFirst().ifPresent(agent -> create_flag_FSM(agent));
    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {
        super.on_transition(old_state, message, new_state);
        if (message.equals(_msg_RUN)) {
            estimated_end_time = end_time;
        }
        if (message.equals(_msg_RESET)) {
            estimated_end_time = null;
            mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal", "led_blu", "normal"), roles.get("leds"));
            mqttOutbound.send("signals", MQTT.toJSON("led_red", "normal"), roles.get("red_spawn"));
            mqttOutbound.send("signals", MQTT.toJSON("led_blu", "normal"), roles.get("blue_spawn"));
        }
    }

    @Override
    public void time_is_up() {

    }


    private void defused(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "DEFUSED"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_grn", "normal"), agent);
        deleteJob(bombTimerJobkey);
    }

    private void fused(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "", "FUSED"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_red", "normal"), agent);
        final JobDataMap jdm = new JobDataMap();
        jdm.put("bombid", "bomb1");
        create_job(bombTimerJobkey, LocalDateTime.now().plusSeconds(bomb_timer), BombTimerJob.class, Optional.of(jdm));
    }

    private void prolog(String agent) {
        mqttOutbound.send("paged",
                MQTT.page("page0",
                        "I am ${agentname}", "", "I will be a", "Capture Point"),
                agent);
        mqttOutbound.send("signals", MQTT.toJSON("led_all", "off", "led_wht", "normal"), agent);
    }


    public void create_flag_FSM(final String agent) {
        try {
            farcryFSM = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/farcry.xml"), null);
            farcryFSM.setStatesAfterTransition("DEFUSED", (state, obj) -> {
                defused(agent);
            });

            farcryFSM.setStatesAfterTransition("FUSED", (state, obj) -> {
                fused(agent);
            });

            farcryFSM.setAction("DEFUSED", "btn01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.send("signals", MQTT.toJSON("sir2", "short"), roles.get("sirens"));
//                    mqttOutbound.send("paged", MQTT.page("page0", "Restspielzeit", "${remaining}", "Bombe IST scharf", respawn_period > 0 ? "Respawn: ${respawn}" : ""), roles.get("spawns"));
                    estimated_end_time = LocalDateTime.now().plusSeconds(bomb_timer);
                    return true;
                }
            });

            farcryFSM.setAction("FUSED", "btn01", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{} =====> {}", curState, nextState);
                    mqttOutbound.send("signals", MQTT.toJSON("sir3", "short"), roles.get("sirens"));
                    estimated_end_time = start_time.plusSeconds(game_time); //LocalDateTime.now().plusSeconds(match_length - start_time.until(LocalDateTime.now(), ChronoUnit.SECONDS));
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


    public void on_game_over() {

        deleteJob(myRespawnJobKey);
        mqttOutbound.send("signals", MQTT.toJSON("buzzer", "off"), roles.get("spawns"));
        mqttOutbound.send("timers", MQTT.toJSON("respawn", "-1"), roles.get("spawns"));
//        trigger_internal_event("GAME_OVER");
    }


//    @Override
//    public void process_message(String sender, String item, JSONObject message) {
//        // internal message OR message I am interested in
//        if (sender.equalsIgnoreCase("_internal")) {
//            farcryFSM.ProcessFSM(message.getString("message"));
//        } else if (hasRole(sender, "button") && message.getString("button").equalsIgnoreCase("up")) {
//            farcryFSM.ProcessFSM(message.getString("button_pressed").toUpperCase());
//        } else {
//            log.debug("message is not for me. ignoring.");
//        }
//    }

    @Override
    protected void respawn(String role, String agent) {

    }


    protected void on_run() {

        deleteJob(myRespawnJobKey);
        if (respawn_period > 0) { // respawn_period == 0 means we should not care about it
            create_job(myRespawnJobKey, simpleSchedule().withIntervalInSeconds(respawn_period).repeatForever(), RespawnJob.class);
        } else {
            mqttOutbound.send("paged", MQTT.page("page0", "", "Restspielzeit", "${remaining}", "", ""), roles.get("spawns"));
        }
    }

    @Override
    public JSONObject getState() {
        return super.getState()
                .put("flag_capture_time", bomb_timer)
                .put("respawn_period", respawn_period)
                .put("current_state", farcryFSM.getCurrentState());
    }

    @Override
    protected JSONObject getPages() {
        return MQTT.page("page0", game_description);
    }


    public void respawn() {
        // the last part of this message is a delayed buzzer sound, so its end lines up with the end of the respawn period
        mqttOutbound.send("signals", MQTT.toJSON("buzzer", String.format("1:off,%d;on,75;off,500;on,75;off,500;on,75;off,500;on,1200;off,1", respawn_period * 1000 - 2925 - 100)), roles.get("spawns"));
        mqttOutbound.send("timers", MQTT.toJSON("respawn", Long.toString(respawn_period)), roles.get("spawns"));
    }
}
