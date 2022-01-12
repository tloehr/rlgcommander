package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.Game;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Log4j2
public class GamesService {
    MQTTOutbound mqttOutbound;
    Scheduler scheduler;
    AgentsService agentsService;
    BuildProperties buildProperties;
    private Optional<Game> loaded_game;
    //todo: private HashMap<String, Game> loaded_games; // for later use when handling multiple games. will replace loaded_game.

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.loaded_game = Optional.empty();
    }

    /**
     * start behavior for all agents when the server starts
     */
    @EventListener(ApplicationReadyEvent.class)
    public void welcome() {
        mqttOutbound.send("all/init", new JSONObject());
        mqttOutbound.send("all/signals", MQTT.toJSON("led_all", "∞:on,250;off,2500"));
        mqttOutbound.send("all/paged", MQTT.page("page0", "Waiting for a game",
                "cmdr " + buildProperties.getVersion() + "." + buildProperties.get("buildNumber"),
                "agnt ${agversion}.${agbuild}",
                "RLGS2 @flashheart.de"));
    }

    public Optional<Game> load_game(String json) throws Exception {
        log.debug("\n _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                "|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        JSONObject game_description = new JSONObject(json);
        loaded_game.ifPresent(game -> game.cleanup());
        Game game = (Game) Class.forName(game_description.getString("class")).getDeclaredConstructor(JSONObject.class, Scheduler.class, MQTTOutbound.class).newInstance(game_description, scheduler, mqttOutbound);
        loaded_game = Optional.ofNullable(game);
        return loaded_game;
    }

    public void unload_game() {
        loaded_game.ifPresent(game -> game.cleanup());
        loaded_game = Optional.empty();
        welcome();
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

    public void react_to(String agentid, JSONObject payload) {
        loaded_game.ifPresent(game -> {
            game.react_to(agentid, payload); // event, will be mostly button
        });
    }

    public void shutdown_agents() {
        mqttOutbound.send("shutdown", "all");
    }

    public Optional<Game> admin_set_values(String description) {
        //loaded_game.ifPresent(game -> game.reset());
        loaded_game.ifPresent(game -> {
            if (!game.isPausing()) return;
        });
        return loaded_game;
    }

//    private Multimap<String, Agent> createAgentMap(JSONObject agents_for_role, List<String> requiredRoles) throws JSONException {
//        final Multimap<String, Agent> result = HashMultimap.create();
//        //final ArrayList<String> missingRoleAgents = new ArrayList<>();
//
//        agents_for_role.keySet().forEach(role -> {
//            JSONArray agents_for_this_role = agents_for_role.getJSONArray(role);
//            agents_for_this_role.forEach(agentid -> {
//                Optional<Agent> optAgent = agentsService.getAgent(agentid.toString());
//                if (optAgent.isEmpty()) {
//                    optAgent = Optional.of(new Agent(agentid.toString()));
//                    log.warn("agent {} is needed for {}, but hasn't reported in yet. will use dummy", agentid, role);
//                } else {
//                    log.debug("{} is alive and will be used for {}", agentid, role);
//                }
//                result.put(role, optAgent.get());
//            });
//        });
//
//        return result;
//    }

}
