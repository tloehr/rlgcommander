package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.events.SpawnListener;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.Scheduler;

import java.lang.reflect.Array;
import java.util.*;

@Log4j2
public class Spawns {
    public static final String SPAWN_TYPE_STATIC = "static";
    final HashMap<String, AbstractSpawn> all_spawns;
    final List<SpawnListener> respawn_listeners;
    final List<String> spawn_agents;
    private final MQTTOutbound mqttOutbound;
    private final JSONObject game_parameters;
    private final String spawn_type;
    private final int starter_countdown;
    private final String intro_mp3_file;
    private final boolean wait4teams2B_ready;
//    private final ArrayList<SpawnListener> listeners;
    private final ArrayList<SpawnListener> listeners;

    public Spawns(MQTTOutbound mqttOutbound, JSONObject game_parameters) {
/**
 *  "spawns": {
 *     "type": "static",
 *     "wait4teams2B_ready": true,
 *     "intro_mp3_file": "<random>",
 *     "starter_countdown": 30,
 *     "resume_countdown": 0,
 *     "teams": [
 *       {
 *         "role": "red_spawn",
 *         "led": MQTT.RED,
 *         "name": "Team Red",
 *         "agents": [
 *           "ag30"
 *         ]
 *       },
 *       {
 *         "role": "blue_spawn",
 *         "led": MQTT.BLUE,
 *         "name": "Team Blue",
 *         "agents": [
 *           "ag31"
 *         ]
 *       }
 *     ]
 *   },
 */

        this.listeners = new ArrayList<>();
        this.mqttOutbound = mqttOutbound;
        this.game_parameters = game_parameters;
        all_spawns = new HashMap<>();
        respawn_listeners = new ArrayList<>();
        spawn_agents = new ArrayList<>();
        this.wait4teams2B_ready = game_parameters.getBoolean("wait4teams2B_ready");
        this.starter_countdown = game_parameters.getInt("starter_countdown");
        this.intro_mp3_file = game_parameters.getString("intro_mp3_file");
        //this.runGameJob = new JobKey("run_the_game", uuid.toString());
        JSONObject spawns = game_parameters.getJSONObject("spawns");
        spawn_type = spawns.getString("type");
        Set<String> spawn_roles = spawns.keySet();
        game_parameters.getJSONArray("teams").forEach(j -> {
                    JSONObject json = (JSONObject) j;
                    String role = json.getString("role");
                    String led = json.getString("led");
                    String team = json.getString("team");

                    if (spawn_type.equalsIgnoreCase(SPAWN_TYPE_STATIC)) all_spawns.put(role, new StaticSpawn(role, led, team));


                    json.getJSONArray("agents").forEach(agent -> {
                                all_spawns.get(role).add_agent(agent.toString());
                            }
                    );
                }
        );

    }

    public void process_message(String sender, String item, JSONObject message) {

    }


}
