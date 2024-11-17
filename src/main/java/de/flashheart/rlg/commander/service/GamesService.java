package de.flashheart.rlg.commander.service;

import com.github.lalyos.jfiglet.FigletFont;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.Exception.AgentInUseException;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.games.events.StateReachedEvent;
import de.flashheart.rlg.commander.games.events.StateReachedListener;
import de.flashheart.rlg.commander.configs.MyYamlConfiguration;
import de.flashheart.rlg.commander.persistence.SavedGamesService;
import de.flashheart.rlg.commander.persistence.PlayedGamesService;
import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.websockets.OutputMessage;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class GamesService {
    private final MQTTOutbound mqttOutbound;
    private final Scheduler scheduler;
    private final AgentsService agentsService;
    private final BuildProperties buildProperties;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MyYamlConfiguration myYamlConfiguration;
    private final PlayedGamesService playedGamesService;

    private final Optional<Game>[] loaded_games;
    public static final int MAX_NUMBER_OF_GAMES = 1;
    private final Multimap<Integer, StateReachedListener> gameStateListeners;

    @EventListener(ApplicationReadyEvent.class)
    public void welcome() {
        log.info("RLG-Commander v{} b{}", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties, SimpMessagingTemplate simpMessagingTemplate, MyYamlConfiguration myYamlConfiguration, PlayedGamesService playedGamesService, SavedGamesService savedGamesService) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.playedGamesService = playedGamesService;
        this.loaded_games = new Optional[]{Optional.empty()};
        this.gameStateListeners = HashMultimap.create();
        this.myYamlConfiguration = myYamlConfiguration;
    }

    public Game load_game(final int game_id, String json, Users owner) throws AgentInUseException, ClassNotFoundException, ArrayIndexOutOfBoundsException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, JSONException, IOException {
        if (game_id < 1 || game_id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        if (loaded_games[game_id - 1].isPresent())
            throw new IllegalAccessException("id " + game_id + " is in use. Unload first.");
        log.debug("\n" + FigletFont.convertOneLine("LOADING GAME #" + game_id));
        JSONObject game_description = new JSONObject(json);

        // add parameters from application.yml
        game_description.put("SCORE_CALCULATION_EVERY_N_SECONDS", myYamlConfiguration.getScore_broadcast().get("every_seconds"));
        game_description.put("BROADCAST_SCORE_EVERY_N_TICKET_CALCULATION_CYCLES", myYamlConfiguration.getScore_broadcast().get("cycle_counter"));
        game_description.put("owner", owner.getUsername());
        game_description.put("rlgcommander", String.format("%s.%s", buildProperties.getVersion(), buildProperties.get("buildNumber")));

        loaded_games[game_id - 1].ifPresent(game -> game.cleanup());
        final Game game = (Game) Class
                .forName(game_description.getString("class"))
                .getDeclaredConstructor(JSONObject.class, Scheduler.class, MQTTOutbound.class)
                .newInstance(game_description, scheduler, mqttOutbound);
        agentsService.assign_game_id_to_agents(game_id, game.getAgents().keySet());

        game.addStateReachedListener(event -> {
            log.trace("gameid #{} new state: {}", game_id, event.getState());
            fireStateReached(game_id, event); // pass it on to the RestController
        });
        game.addStateTransistionListener(event -> {
            if (event.getMessage().equals(Game._msg_GAME_OVER)) {
                playedGamesService.save(playedGamesService.createNew(owner, game.getState()));
            }
        });

        loaded_games[game_id - 1] = Optional.of(game);

        game.process_internal_message(Game._msg_RESET);
        return game;
    }

    public void unload_game(int id) throws IllegalStateException, ArrayIndexOutOfBoundsException, IOException {
        check_id(id);

        log.debug("\n" + FigletFont.convertOneLine("GAME #" + id + " RELEASED"));
        Game game_to_unload = loaded_games[id - 1].get();
        // delete all replacements used in this for this game
        //game_to_unload.getAgents().keySet().forEach(agent_to_remove -> agent_replacement_map.remove(agent_to_remove));
        // free all used agents from their game assignments
        agentsService.free_agents(game_to_unload.getAgents().keySet());
        game_to_unload.cleanup();
        loaded_games[id - 1] = Optional.empty();
        fireStateReached(id, StateReachedEvent.EMPTY); // pass it on to the RestController
        gameStateListeners.removeAll(id);
        // this welcomes all agents even if they never reported in. not really a problem - kind of an oddity
        agentsService.welcome_unused_agents();
    }

    public Optional<Game> getGame(int id) throws ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        return loaded_games[id - 1];
    }

    public JSONObject getGameState(int id) throws ArrayIndexOutOfBoundsException {
        if (id < 1 || id > MAX_NUMBER_OF_GAMES)
            throw new ArrayIndexOutOfBoundsException("MAX_GAMES allowed is " + MAX_NUMBER_OF_GAMES);
        JSONObject state = new JSONObject()
                .put("rlgcommander", String.format("%s.%s", buildProperties.getVersion(), buildProperties.get("buildNumber")))
                .put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)))
                .put("mode", "server");
        return loaded_games[id - 1].isEmpty() ? state : MQTT.merge(state, loaded_games[id - 1].get().getState());
    }

    public void process_message(int id, String message) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().process_internal_message(message);
    }

    public void zeus(int id, String params) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().zeus(new JSONObject(params));
    }

    public void process_message(int id, String agent, String source, JSONObject payload) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        check_id(id);
        loaded_games[id - 1].get().process_external_message(agent, source, payload);
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
        // todo: IDs mit berücksichtigen.
        OutputMessage outputMessage = new OutputMessage(event.getState(), new SimpleDateFormat("HH:mm").format(new Date()));
        simpMessagingTemplate.convertAndSend("/topic/messages", outputMessage); // STOMP
        gameStateListeners.get(id).forEach(gameStateListener -> gameStateListener.onStateReached(event));
    }

    public StateReachedListener addStateReachedListener(int id, StateReachedListener toAdd) {
        try {
            check_id(id);
            gameStateListeners.put(id, toAdd);
            // we need to send an event right away, otherwise the rlgrc won't realize that it has subscribed successfully
            toAdd.onStateReached(new StateReachedEvent(loaded_games[id - 1].get().getState().getString("game_state")));
        } catch (ArrayIndexOutOfBoundsException | IllegalStateException | JSONException ex) {
            log.warn(ex.getMessage());
            toAdd = null;
        }
        return toAdd;
    }


}
