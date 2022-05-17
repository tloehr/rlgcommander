package de.flashheart.rlg.commander.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.misc.*;
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
    private final Multimap<Integer, StateReachedListener> gameStateListeners;

    @EventListener(ApplicationReadyEvent.class)
    public void welcome() {
        log.info("RLG-Commander {}b{}", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.loaded_games = new Optional[]{Optional.empty()};
        this.gameStateListeners = HashMultimap.create();
    }

    public Game load_game(final int id, String json) throws ClassNotFoundException, ArrayIndexOutOfBoundsException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        //if (loaded_games[id-1].isPresent()) throw new IllegalAccessException("id "+id+" is in use. Unload first.");
        log.debug("\n _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                "|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        log.debug("\n" + Tools.fignums[id]);
        JSONObject game_description = new JSONObject(json);
        loaded_games[id - 1].ifPresent(game -> game.cleanup());
        // todo: check for agent conflicts when loading. reject if necessary
        Game game = (Game) Class.forName(game_description.getString("class")).getDeclaredConstructor(JSONObject.class, Scheduler.class, MQTTOutbound.class).newInstance(game_description, scheduler, mqttOutbound);
        game.addStateReachedListener(event -> {
            log.trace("gameid #{} new state: {}", id, event.getState());
            fireStateReached(id, event); // pass it on to the RestController
        });

        loaded_games[id - 1] = Optional.of(game);
        agentsService.assign_gameid_to_agents(id, game.getAgents().keySet());
        game.process_message(Game._msg_RESET);
        return game;
    }

    public void unload_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        log.debug("\n _   _ _  _ _    ___   _   ___ ___ _  _  ___    ___   _   __  __ ___\n" +
                "| | | | \\| | |  / _ \\ /_\\ |   \\_ _| \\| |/ __|  / __| /_\\ |  \\/  | __|\n" +
                "| |_| | .` | |_| (_) / _ \\| |) | || .` | (_ | | (_ |/ _ \\| |\\/| | _|\n" +
                " \\___/|_|\\_|____\\___/_/ \\_\\___/___|_|\\_|\\___|  \\___/_/ \\_\\_|  |_|___|");
        log.debug("\n" + Tools.fignums[id]);
        Game game_to_unload = loaded_games[id - 1].get();
        agentsService.assign_gameid_to_agents(-1, game_to_unload.getAgents().keySet()); // remove gameid assignment
        game_to_unload.cleanup();
        loaded_games[id - 1] = Optional.empty();
        gameStateListeners.removeAll(id);
        //fireStateReached(id, new StateReachedEvent(""));
    }

    public Optional<Game> getGame(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        return loaded_games[id - 1];
    }

    public JSONObject getGameState(int id) throws ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        return loaded_games[id - 1].isEmpty() ? new JSONObject() : loaded_games[id - 1].get().getState();
    }

    public JSONArray getGameStates() throws ArrayIndexOutOfBoundsException {
        JSONArray jsonArray = new JSONArray();
        for (int id = 1; id <= MAX_NUMBER_OF_GAMES; id++) {
            JSONObject iGame = loaded_games[id - 1].isEmpty() ? new JSONObject() : loaded_games[id - 1].get().getState();
            jsonArray.put(id, iGame);
        }
        return jsonArray;
    }

    public void process_message(int id, String message) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().process_message(message);
    }

    public void process_message(int id, String agentid, String item, JSONObject payload) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().process_message(agentid, item, payload);
    }

    public JSONArray get_games() {
        return new JSONArray(Arrays.stream(loaded_games).map(game -> game.isEmpty() ? "{}" : game.get().getState()).collect(Collectors.toList()));
    }

    private void check_id(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        if (loaded_games[id - 1].isEmpty()) throw new IllegalStateException("Game #" + id + " not loaded.");
    }

    private void fireStateReached(int id, StateReachedEvent event) {
        gameStateListeners.get(id).forEach(gameStateListener -> gameStateListener.onStateReached(event));
    }

    public void addStateReachedListener(int id, StateReachedListener toAdd) {
        try {
            check_id(id);
            gameStateListeners.put(id, toAdd);
            // we need to send an event right away, otherwise the rlgrc won't realize that it has subscribed successfully
            toAdd.onStateReached(new StateReachedEvent(loaded_games[id - 1].get().getState().getString("game_state")));
        } catch (ArrayIndexOutOfBoundsException | IllegalStateException | JSONException ex) {
            log.warn(ex.getMessage());
        }
    }

}
