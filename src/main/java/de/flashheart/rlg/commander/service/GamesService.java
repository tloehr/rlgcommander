package de.flashheart.rlg.commander.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.ConquestGame;
import de.flashheart.rlg.commander.games.FarcryGame;
import de.flashheart.rlg.commander.games.Game;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
public class GamesService {
    MQTTOutbound mqttOutbound;
    Scheduler scheduler;
    AgentsService agentsService;
    BuildProperties buildProperties;
    private Optional<Game> loaded_game;

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.loaded_game = Optional.empty();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void welcome_page() {
        long hours_since_last_version_change = ChronoUnit.HOURS.between(LocalDateTime.of(2021, 11, 9, 0, 0, 0), LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()));
//        mqttOutbound.sendCommandTo("all",
//                MQTT.add_pages("gamepage"));
        mqttOutbound.sendCommandTo("all",
                MQTT.pages(
                        MQTT.page_content("page0", "RLGS2 ready",
                                "c v" + buildProperties.getVersion() + " b" + LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyMMddHHmm")),
                                "a v${agversion} b${agbdate}",
                                "flashheart.de")
//                        MQTT.page_content("versionpage", "RLG-Commander", "v" + buildProperties.getVersion() + " b" + LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
//                                "RLG-Agent", "v${agversion} b${agbuild}"),
//                        MQTT.page_content("gamepage", loaded_game.isPresent() ? loaded_game.get().getDisplay() : new String[]{"no", "loaded", "game", "found"})
                )
        );
    }

    public Optional<Game> load_game(String json) {

        log.debug("\n _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                "|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        JSONObject description = new JSONObject(json);
        loaded_game.ifPresent(game -> game.cleanup());

        if (description.getString("game").equalsIgnoreCase("conquest"))
            loaded_game = load_conquest(description);
        else if (description.getString("game").equalsIgnoreCase("farcry"))
            loaded_game = load_farcry(description);
        else
            loaded_game = Optional.empty();

        loaded_game.ifPresent(game -> {
            log.info("GAME LOADED: " + game.getStatus());
            mqttOutbound.sendCommandTo("all",
                    MQTT.pages(MQTT.page_content("page0", game.getDisplay()))
            );
        });
        return loaded_game;
    }

    public void unload_game() {
        loaded_game.ifPresent(game -> game.cleanup());
        loaded_game = Optional.empty();
    }

//    /**
//     * reset a specific agent to neutral and set the remaining tickets to correct an in-game misbehaviour
//     * @param description
//     * @return
//     */
//    private Optional<Game> reset_conquest_flag(JSONObject description) {
//        log.debug("Resetting running conquest game");
//        loaded_game.ifPresent(game -> {
//            if (game.getName().equalsIgnoreCase(description.getString("name"))) {
//
//                description.getBigDecimal("ticket_change")
//            }
//        });
//        loaded_game = Optional.of(new ConquestGame(
//                createAgentMap(description.getJSONObject("agents"), Arrays.asList("cp_agents", "blue_spawn_agent", "red_spawn_agent", "sirens")),
//                description.getBigDecimal("starting_tickets"),
//                description.getBigDecimal("ticket_price_to_respawn"),
//                description.getBigDecimal("minimum_cp_for_bleeding"),
//                description.getBigDecimal("starting_bleed_interval"),
//                description.getBigDecimal("interval_reduction_per_cp"),
//                scheduler,
//                mqttOutbound));
//        return loaded_game;
//    }


    private Optional<Game> load_conquest(JSONObject description) {
        log.debug("\n   __________  _   ______  __  ___________________\n" +
                "  / ____/ __ \\/ | / / __ \\/ / / / ____/ ___/_  __/\n" +
                " / /   / / / /  |/ / / / / / / / __/  \\__ \\ / /\n" +
                "/ /___/ /_/ / /|  / /_/ / /_/ / /___ ___/ // /\n" +
                "\\____/\\____/_/ |_/\\___\\_\\____/_____//____//_/\n");
        loaded_game = Optional.of(new ConquestGame(
                createAgentMap(description.getJSONObject("agents"), Arrays.asList("cp_agents", "blue_spawn_agent", "red_spawn_agent", "sirens")),
                description.getBigDecimal("starting_tickets"),
                description.getBigDecimal("ticket_price_to_respawn"),
                description.getBigDecimal("minimum_cp_for_bleeding"),
                description.getBigDecimal("starting_bleed_interval"),
                description.getBigDecimal("interval_reduction_per_cp"),
                scheduler,
                mqttOutbound));
        return loaded_game;
    }

    private Optional<Game> load_farcry(JSONObject description) {
        log.debug("\n    ______           ______\n" +
                "   / ____/___ ______/ ____/______  __\n" +
                "  / /_  / __ `/ ___/ /   / ___/ / / /\n" +
                " / __/ / /_/ / /  / /___/ /  / /_/ /\n" +
                "/_/    \\__,_/_/   \\____/_/   \\__, /\n" +
                "                            /____/");
        loaded_game = Optional.of(new FarcryGame(
                createAgentMap(description.getJSONObject("agents"), Arrays.asList("sirens", "leds", "red_spawn", "blue_spawn")),
                description.getInt("flag_capture_time"),
                description.getInt("match_length"),
                description.getInt("respawn_period"),
                scheduler,
                mqttOutbound));
        return loaded_game;
    }


    public Optional<Game> getGame() {
        return loaded_game;
    }

    public Optional<Game> start_game() {
        loaded_game.ifPresent(game -> game.start());
        return loaded_game;
    }

    public Optional<Game> reset_game() {
        loaded_game.ifPresent(game -> game.reset());
        return loaded_game;
    }

    public Optional<Game> resume_game() {
        loaded_game.ifPresent(game -> game.resume());
        return loaded_game;
    }

    public Optional<Game> pause_game() {
        loaded_game.ifPresent(game -> game.pause());
        return loaded_game;
    }

    public void react_to(String agentid, JSONObject event) {
        loaded_game.ifPresent(game -> {
            game.react_to(agentid, event); // event, will be mostly button
        });
    }

    public void shutdown_agents() {
        mqttOutbound.sendCommandTo("all", new JSONObject().put("init", ""));
    }

    /**
     * creates a multimap which contains the role to agent assignment. but only if those agents have reported in during
     * the lifetime of the commander
     *
     * @param agents_for_role
     * @param requiredRoles   an array for the keys to be parsed e.g. {"button", "leds", "sirens","attack_spawn_agent","defend_spawn_agent"}
     * @return MultiMap with Agents assigned to their roles.
     * @throws JSONException
     */
    private Multimap<String, Agent> createAgentMap(JSONObject agents_for_role, List<String> requiredRoles) throws JSONException {
        final Multimap<String, Agent> result = HashMultimap.create();
        //final ArrayList<String> missingRoleAgents = new ArrayList<>();

        agents_for_role.keySet().forEach(role -> {
            JSONArray agents_for_this_role = agents_for_role.getJSONArray(role);
            agents_for_this_role.forEach(agentid -> {
                Optional<Agent> optAgent = agentsService.getAgent(agentid.toString());
                if (optAgent.isEmpty()) {
                    optAgent = Optional.of(new Agent(agentid.toString()));
                    log.warn("agent {} is needed for {}, but hasn't reported in yet. will use dummy", agentid, role);
                } else {
                    log.debug("{} is alive and will be used for {}", agentid, role);
                }
                result.put(role, optAgent.get());
            });
        });

        return result;
    }


    public Optional<Game> admin_game(String description) {
        //loaded_game.ifPresent(game -> game.reset());
        loaded_game.ifPresent(game -> {
            if (!game.isPaused()) return;
        });
        return loaded_game;
    }
}
