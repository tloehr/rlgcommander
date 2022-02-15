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

import java.util.HashMap;
import java.util.Optional;

@Service
@Log4j2
public class GamesService {
    MQTTOutbound mqttOutbound;
    Scheduler scheduler;
    AgentsService agentsService;
    BuildProperties buildProperties;
    private final HashMap<String, Game> loaded_games;

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.loaded_games = new HashMap<>();
    }

    /**
     * start behavior for all agents when the server starts
     */
    @EventListener(ApplicationReadyEvent.class)
    public void welcome() {
        mqttOutbound.send("all/init", new JSONObject());
        mqttOutbound.send("all/signals", MQTT.toJSON("led_wht", "∞:on,500;off,500", "led_ylw", "∞:on,500;off,500", "led_blu", "∞:on,500;off,500",
                "led_red", "∞:off,500;on,500", "led_grn", "∞:off,500;on,500"));
        mqttOutbound.send("all/paged", MQTT.page("page0", "Waiting for a game",
                "cmdr " + buildProperties.getVersion() + "." + buildProperties.get("buildNumber"),
                "agnt ${agversion}.${agbuild}",
                "RLGS2 @flashheart.de"));
    }

    public Game load_game(String id, String json) throws Exception {
        log.debug("\n _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                "|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        JSONObject game_description = new JSONObject(json);
        Optional.ofNullable(loaded_games.get(id)).ifPresent(game -> game.cleanup());
        Game game = (Game) Class.forName(game_description.getString("class")).getDeclaredConstructor(String.class, JSONObject.class, Scheduler.class, MQTTOutbound.class).newInstance(id, game_description, scheduler, mqttOutbound);
        // todo: check for agent conflicts when loading
        loaded_games.put(id, game);
        agentsService.assign_gameid_to_agents(id, game.getAgents().keySet());
        return game;
    }

    public void unload_game(String id) {
        // todo: release agents from id
        Optional.ofNullable(loaded_games.get(id)).ifPresent(game -> game.cleanup());
        loaded_games.remove(id);
        welcome();
    }


    public Optional<Game> getGame(String id) {
        return Optional.ofNullable(loaded_games.get(id));
    }

    public void start_game(String id) throws IllegalStateException {
        if (!loaded_games.containsKey(id)) throw new IllegalStateException("Game #"+id+" not loaded. Can't start.");
        Optional<Game> mygame = Optional.ofNullable(loaded_games.get(id));
        if (mygame.isPresent()) {
            mygame.get().start();
        }
    }

    public void reset_game(String id) {
        Optional.ofNullable(loaded_games.get(id)).ifPresent(game -> game.reset());
    }

    public void resume_game(String id) throws IllegalStateException {
        Optional<Game> mygame = Optional.ofNullable(loaded_games.get(id));
        if (mygame.isPresent()) {
            mygame.get().resume();
        }
    }

    public void pause_game(String id) throws IllegalStateException {
        Optional<Game> mygame = Optional.ofNullable(loaded_games.get(id));
        if (mygame.isPresent()) {
            mygame.get().pause();
        }
    }

    public void react_to(String id, String agentid, String source, JSONObject payload) throws IllegalStateException {
        Optional<Game> mygame = Optional.ofNullable(loaded_games.get(id));
        if (mygame.isPresent()) {
            mygame.get().react_to(agentid, source, payload);
        }
    }

    public JSONObject get_running_games() {
        final JSONObject jsonObject = new JSONObject();
        loaded_games.entrySet().forEach(stringGameEntry -> jsonObject.put(stringGameEntry.getKey(), stringGameEntry.getValue().getStatus()));
        return jsonObject;
    }

    public void shutdown_agents() {
        mqttOutbound.send("shutdown", "all");
    }

    public Optional<Game> admin_set_values(String id, String description) {
        //loaded_game.ifPresent(game -> game.reset());
        Optional<Game> loaded_game = Optional.ofNullable(loaded_games.get(id));
        loaded_game.ifPresent(game -> {
            if (!game.isPausing()) return;
        });
        return loaded_game;
    }


}
