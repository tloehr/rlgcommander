package de.flashheart.rlg.commander.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.mechanics.ConquestGame;
import de.flashheart.rlg.commander.mechanics.FarcryGame;
import de.flashheart.rlg.commander.mechanics.Game;
import de.flashheart.rlg.commander.mechanics.ScheduledGame;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Log4j2
public class GamesService {
    MQTTOutbound mqttOutbound;
    Scheduler scheduler;
    AgentsService agentsService;
    private Optional<Game> loaded_game;

    @Autowired
    public GamesService(MQTTOutbound mqttOutbound, Scheduler scheduler, AgentsService agentsService) {
        this.mqttOutbound = mqttOutbound;
        this.scheduler = scheduler;
        this.agentsService = agentsService;
        this.loaded_game = Optional.empty();
    }

    public Optional<Game> load_game(String json) {
        JSONObject description = new JSONObject(json);
        loaded_game.ifPresent(game -> game.cleanup());

        if (description.getString("game").equalsIgnoreCase("conquest"))
            loaded_game = load_conquest(description);
        else if (description.getString("game").equalsIgnoreCase("farcry"))
            loaded_game = load_farcry(description);
        else
            loaded_game = Optional.empty();

        loaded_game.ifPresent(game -> log.info("GAME LOADED: " + game.getStatus()));
        return loaded_game;
    }

    private Optional<Game> load_conquest(JSONObject description) {
        loaded_game = Optional.of(new ConquestGame(
                createAgentMap(description.getJSONObject("agent_roles"), new String[]{"cp_agents", "blue_spawn_agent", "red_spawn_agent"}),
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
        loaded_game = Optional.of(new FarcryGame(
                createAgentMap(description.getJSONObject("agent_roles"), new String[]{"button", "led", "siren"}),
                description.getInt("flag_capture_time"),
                description.getInt("match_length"),
                0, // description.getInt("respawn_period")
                scheduler,
                mqttOutbound));
        return loaded_game;
    }

    public void unload_game() {
        loaded_game.ifPresent(game -> {
            game.cleanup();
        });
        loaded_game = Optional.empty();
        //agentStandbyPattern(liveAgents.keySet());
    }

    public Optional<Game> getGame() {
        return loaded_game;
    }

    public Optional<Game> start_game() {
        loaded_game.ifPresent(game -> game.start());
        return loaded_game;
    }

    public Optional<Game> stop_game() {
        loaded_game.ifPresent(game -> game.stop());
        return loaded_game;
    }

    public Optional<Game> resume_game() {
        loaded_game.ifPresent(game -> {
            if (game instanceof ScheduledGame) ((ScheduledGame) game).resume();
        });
        return loaded_game;
    }

    public Optional<Game> pause_game() {
        loaded_game.ifPresent(game -> {
            if (game instanceof ScheduledGame) ((ScheduledGame) game).pause();
        });
        return loaded_game;
    }

    public void react_to(String agentid, String event) {
        loaded_game.ifPresent(game -> {
            game.react_to(agentid, event); // event, will be mostly button
        });
    }

    /**
     * creates a multimap which contains the role to agent assignment. but only if those agents have reported in during
     * the lifetime of the commander
     *
     * @param agents_for_role
     * @param roles           an array for the keys to be parsed e.g. {"button_agent", "led_agent",
     *                        "siren_agent","attack_spawn_agent","defend_spawn_agent"}
     * @return MultiMap with Agents assigned to their roles.
     * @throws JSONException
     */
    private Multimap<String, Agent> createAgentMap(JSONObject agents_for_role, String[] roles) throws JSONException, NoSuchElementException {
        final Multimap<String, Agent> result = HashMultimap.create();
        final ArrayList<String> missingRoleAgents = new ArrayList<>();
        for (final String role : roles) {
            if (agents_for_role.has(role)) {
                agents_for_role.getJSONArray(role).toList().forEach(agentid -> {

                    Optional<Agent> optAgent = Optional.of(new Agent(agentid.toString()));
                    //Optional<Agent> optAgent = agentsService.getAgent(agentid.toString());
                    if (optAgent.isPresent()) {
                        result.put(role, optAgent.get());
                        log.debug("{} is alive and will be used for {}", agentid, role);
                    } else {
                        missingRoleAgents.add(role);
                    }

                });
            }
        }
        if (!missingRoleAgents.isEmpty()) {
            throw new NoSuchElementException(String.format("no agent found for role %s", missingRoleAgents.toString()));
        }
        return result;
    }


}
