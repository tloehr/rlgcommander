package de.flashheart.rlg.commander.service;

import com.github.lalyos.jfiglet.FigletFont;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.Exception.AgentInUseException;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.games.GameSetupException;
import de.flashheart.rlg.commander.games.events.StateReachedEvent;
import de.flashheart.rlg.commander.games.events.StateReachedListener;
import de.flashheart.rlg.commander.misc.GameStateChangedMessage;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.persistence.PlayedGamesService;
import de.flashheart.rlg.commander.persistence.Users;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class GamesService {
    private final MessageSource messageSource;
    private final MQTTOutbound mqttOutbound;
    private final Scheduler scheduler;
    private final AgentsService agentsService;
    private final BuildProperties buildProperties;
    //private final MyYamlConfiguration myYamlConfiguration;
    private final PlayedGamesService playedGamesService;
    private final HashMap<Integer, Game> loaded_games;
    private final Multimap<Integer, StateReachedListener> gameStateListeners;

    @EventListener(ApplicationReadyEvent.class)
    public void welcome() {
        log.info("RLG-Commander v{} b{}", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService, BuildProperties buildProperties, PlayedGamesService playedGamesService, MessageSource messageSource) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.playedGamesService = playedGamesService;
        this.messageSource = messageSource;
        //this.loaded_games =  new Optional[]{Optional.empty()};
        this.loaded_games = new HashMap<>();
        this.gameStateListeners = HashMultimap.create();
        //this.myYamlConfiguration = myYamlConfiguration;
    }

    public void load_game(final int game_id, String json, Users owner, Locale locale) throws GameSetupException, AgentInUseException, ClassNotFoundException, ArrayIndexOutOfBoundsException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, JSONException, IOException {
        if (loaded_games.containsKey(game_id)) {
            log.debug(locale.getLanguage());
            throw new GameSetupException(String.format(messageSource.getMessage("game_setup_exception.game_id_in_use", null, locale), game_id));
        }
        log.debug("\n" + FigletFont.convertOneLine("LOADING GAME #" + game_id));
        JSONObject game_description = new JSONObject(json);

        game_description.put("owner", owner.getUsername());

        final Game game = (Game) Class
                .forName(game_description.getString("class"))
                .getDeclaredConstructor(JSONObject.class, Scheduler.class, MQTTOutbound.class, MessageSource.class, Locale.class)
                .newInstance(game_description, scheduler, mqttOutbound, messageSource, locale);
        agentsService.assign_game_id_to_agents(game_id, game.getAgents().keySet());

        game.addStateReachedListener(event -> {
            log.trace("gameid #{} new state: {}", game_id, event.getState());
            fireStateReached(game_id, event); // pass it on to the RestController
        });
        game.addStateTransistionListener(event -> {
            if (event.getMessage().equals(Game._msg_GAME_OVER)) {
                // save this game
                playedGamesService.save(playedGamesService.createNew(owner, game.get_full_state()));
            }
        });
        loaded_games.put(game_id, game);
        game.process_internal_message(Game._msg_RESET);
    }

    public void unload_game(int game_id) throws IllegalStateException, ArrayIndexOutOfBoundsException, IOException {
        if (!loaded_games.containsKey(game_id)) throw new IllegalStateException("Game #" + game_id + " not loaded.");
        log.debug("\n" + FigletFont.convertOneLine("GAME #" + game_id + " RELEASED"));
        Game game_to_unload = loaded_games.get(game_id);
        // delete all replacements used in this for this game
        //game_to_unload.getAgents().keySet().forEach(agent_to_remove -> agent_replacement_map.remove(agent_to_remove));
        // free all used agents from their game assignments
        agentsService.free_agents(game_to_unload.getAgents().keySet());
        game_to_unload.cleanup();
        loaded_games.remove(game_id);
        fireStateReached(game_id, StateReachedEvent.EMPTY); // pass it on to the RestController
        gameStateListeners.removeAll(game_id);
        // this welcomes all agents even if they never reported in. not really a problem - kind of an oddity
        agentsService.welcome_unused_agents();
    }

    public Optional<Game> getGame(int game_id) throws ArrayIndexOutOfBoundsException {
        return Optional.ofNullable(loaded_games.get(game_id));
    }

    public JSONObject get_full_state(int game_id) throws ArrayIndexOutOfBoundsException {
        JSONObject state = new JSONObject()
                .put("rlgcommander", String.format("%s.%s", buildProperties.getVersion(), buildProperties.get("buildNumber")))
                .put("timestamp", JavaTimeConverter.to_iso8601());
        return loaded_games.containsKey(game_id) ? MQTT.merge(state, loaded_games.get(game_id).get_full_state()) : state.put("played", new JSONObject().put("game_fsm_current_state", "EMPTY"));
    }

    public JSONObject get_tiny_state(int game_id) throws ArrayIndexOutOfBoundsException {
        return loaded_games.containsKey(game_id) ? loaded_games.get(game_id).get_tiny_state() : new JSONObject();
    }

    public void process_message(int game_id, String message) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        if (!loaded_games.containsKey(game_id)) throw new IllegalStateException("Game #" + game_id + " not loaded.");
        loaded_games.get(game_id).process_internal_message(message);
    }

    public void zeus(int game_id, String params) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        if (!loaded_games.containsKey(game_id)) throw new IllegalStateException("Game #" + game_id + " not loaded.");
        loaded_games.get(game_id).zeus(new JSONObject(params));
    }

    public void process_message(int game_id, String agent, String source, JSONObject payload) throws IllegalStateException, ArrayIndexOutOfBoundsException {
        if (!loaded_games.containsKey(game_id)) throw new IllegalStateException("Game #" + game_id + " not loaded.");
        loaded_games.get(game_id).on_external_message(agent, source, payload);
    }

    public JSONArray get_games() {
        return new JSONArray(loaded_games.values().stream().map(Game::get_full_state).collect(Collectors.toList()));
    }

    private void fireStateReached(int game_id, StateReachedEvent event) {
        mqttOutbound.notify(game_id, new GameStateChangedMessage(game_id, event.getState()));
        gameStateListeners.get(game_id).forEach(gameStateListener -> gameStateListener.onStateReached(event));
    }

}
