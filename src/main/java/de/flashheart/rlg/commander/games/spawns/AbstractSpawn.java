package de.flashheart.rlg.commander.games.spawns;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.checkerframework.checker.units.qual.A;
import org.checkerframework.common.value.qual.MinLen;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Optional;

@Getter
public abstract class AbstractSpawn {
    public static final String _state_WE_ARE_PREPARING = "WE_ARE_PREPARING";
    public static final String _state_WE_ARE_READY = "WE_ARE_READY";
    public static final String _state_IN_GAME = "IN_GAME";
    public static final String _state_COUNTDOWN_TO_START = "COUNTDOWN_TO_START";
    public static final String _state_COUNTDOWN_TO_RESUME = "COUNTDOWN_TO_RESUME";
    public static final String _msg_START_COUNTDOWN = "start_countdown";
    public static final String _msg_RESPAWN_SIGNAL = "respawn_signal";

    String role;
    String led;
    String team;
    private final Spawns spawns;
    Multimap<Integer, SpawnAgent> spawn_agents;

    public AbstractSpawn(String role, String led, String team, Spawns spawns) {
        this.role = role;
        this.led = led;
        this.team = team;
        this.spawns = spawns;
        spawn_agents = HashMultimap.create();
    }

    public void add_agent(String agent, int section) {
        spawn_agents.put(section, new SpawnAgent(agent, this));
    }

    public boolean has_agent_in(int section, String agent) {
        return spawn_agents.get(section).stream().anyMatch(spawnAgent -> spawnAgent.getAgent().equalsIgnoreCase(agent));
    }

    public void process_message(int section, String agent, String message) {
        spawn_agents.get(section).stream()
                .filter(spawnAgent -> spawnAgent.getAgent().equalsIgnoreCase(agent))
                .findFirst()
                .ifPresent(spawnAgent -> spawnAgent.process_message(message));
    }

    public void process_message(int section, String message) {
        spawn_agents.values().forEach(spawnAgent -> spawnAgent.process_message(message));
    }

}
