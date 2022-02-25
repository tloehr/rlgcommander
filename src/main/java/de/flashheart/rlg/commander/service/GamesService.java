package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.misc.Tools;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class GamesService {
    MQTTOutbound mqttOutbound;
    Scheduler scheduler;
    AgentsService agentsService;
    BuildProperties buildProperties;
    private final Optional<Game>[] loaded_games; // exactly n games are possible
    public static final int MAX_NUMBER_OF_GAMES = 1;

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.loaded_games = new Optional[]{Optional.empty()};
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

    public Game load_game(int id, String json) throws ClassNotFoundException, ArrayIndexOutOfBoundsException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        //if (loaded_games[id-1].isPresent()) throw new IllegalAccessException("id "+id+" is in use. Unload first.");
        log.debug("\n _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                "|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        log.debug("\n"+ Tools.fignums[id]);
        JSONObject game_description = new JSONObject(json);
        loaded_games[id - 1].ifPresent(game -> game.cleanup());
        // todo: check for agent conflicts when loading. reject if necessary
        Game game = (Game) Class.forName(game_description.getString("class")).getDeclaredConstructor(JSONObject.class, Scheduler.class, MQTTOutbound.class).newInstance(game_description, scheduler, mqttOutbound);
        loaded_games[id - 1] = Optional.of(game);
        agentsService.assign_gameid_to_agents(id, game.getAgents().keySet());
        return game;
    }

    public void unload_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        log.debug("\n _   _ _  _ _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| | | | \\| | |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| | .` | |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                " \\___/|_|\\_|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        log.debug("\n"+ Tools.fignums[id]);
        agentsService.assign_gameid_to_agents(-1, loaded_games[id - 1].get().getAgents().keySet());
        loaded_games[id - 1].get().cleanup();
        loaded_games[id - 1] = Optional.empty();
        welcome();
    }


    public Optional<Game> getGame(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        return loaded_games[id - 1];
    }

    public JSONObject getGameStatus(int id) throws ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        return loaded_games[id - 1].isEmpty() ? new JSONObject() : loaded_games[id - 1].get().getStatus();
    }

    public void start_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().start();
    }

    public void reset_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().reset();
    }

    public void resume_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().resume();
    }

    public void pause_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().pause();
    }

    public void react_to(int id, String agentid, String item, JSONObject payload) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().react_to(agentid, item, payload);
    }

    public JSONArray get_games() {
        return new JSONArray(Arrays.stream(loaded_games).map(game -> game.isEmpty() ? "{}" : game.get().getStatus()).collect(Collectors.toList()));
    }
//
//    public void shutdown_agents() {
//        mqttOutbound.send("shutdown", "all");
//    }

    private void check_id(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        if (loaded_games[id - 1].isEmpty()) throw new IllegalStateException("Game #" + id + " not loaded.");
    }

//    public Optional<Game> admin_set_values(String id, String description) {
//        //loaded_game.ifPresent(game -> game.reset());
//        Optional<Game> loaded_game = Optional.ofNullable(loaded_games.get(id));
//        loaded_game.ifPresent(game -> {
//            if (!game.isPausing()) return;
//        });
//        return loaded_game;
//    }


}
