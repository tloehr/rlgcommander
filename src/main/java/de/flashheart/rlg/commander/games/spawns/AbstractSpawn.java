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
    Multimap<Integer, SpawnAgent> spawn_agents;
    private final MQTTOutbound mqttOutbound;

    public AbstractSpawn(String role, String led, String team, MQTTOutbound mqttOutbound) {
        this.role = role;
        this.led = led;
        this.team = team;
        this.mqttOutbound = mqttOutbound;
        spawn_agents = HashMultimap.create();
    }

    public void add_agent(String agent, int section) {
        spawn_agents.put(section, new SpawnAgent(agent, this));
    }

    public boolean process_message(int section, String agent, String item, JSONObject message) {
        Optional<SpawnAgent> found = spawn_agents.get(section).stream().filter(spawnAgent -> spawnAgent.getAgent().equalsIgnoreCase(agent)).findFirst();
        found.ifPresent(spawnAgent -> spawnAgent.process_message(item, message));
        return found.isPresent();
    }


}
