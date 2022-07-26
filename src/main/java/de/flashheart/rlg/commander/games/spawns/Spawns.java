package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.events.RespawnEvent;
import de.flashheart.rlg.commander.games.events.RespawnListener;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

@Log4j2
public class Spawns {
    public static final String SPAWN_TYPE_STATIC = "static";
    final HashMap<String, AbstractSpawn> all_spawns;
    final List<RespawnListener> respawn_listeners;
    final List<String> spawn_agents;
    private final MQTTOutbound mqttOutbound;
    private final JSONObject game_parameters;
    private final String spawn_type;

    public Spawns(MQTTOutbound mqttOutbound, JSONObject game_parameters) {
        this.mqttOutbound = mqttOutbound;
        this.game_parameters = game_parameters;
        all_spawns = new HashMap<>();
        respawn_listeners = new ArrayList<>();
        spawn_agents = new ArrayList<>();
        JSONObject spawns = game_parameters.getJSONObject("spawns");
        spawn_type = spawns.getString("type");
        Set<String> spawn_roles = spawns.keySet();
        spawn_roles.forEach(spawn_role -> {
            String led = spawns.getJSONObject(spawn_role).getString("led");
            String team = spawns.getJSONObject(spawn_role).getString("team");

//            if (spawn_type.equalsIgnoreCase(SPAWN_TYPE_STATIC))
//                        all_spawns.put(spawn_role, new StaticSpawn(spawn_role, led, team));


                    spawns.getJSONArray(spawn_role).forEach(agent -> {
                                spawn_agents.add(agent.toString());


                            }

                    );
                }
        );
    }

    public void process_message(String sender, String item, JSONObject message) {

    }


}
