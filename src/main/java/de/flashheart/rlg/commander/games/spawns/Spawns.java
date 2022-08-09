package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.games.events.SpawnEvent;
import de.flashheart.rlg.commander.games.events.SpawnListener;
import de.flashheart.rlg.commander.games.events.StateReachedEvent;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

@Log4j2
public class Spawns {
    public static final String SPAWN_TYPE_STATIC = "static";
    public static final String SPAWN_TYPE_ROLLING = "rolling";
    final HashMap<String, AbstractSpawn> all_spawns;
    final List<SpawnListener> spawnListeners;
    private final MQTTOutbound mqttOutbound;
    private final JSONObject game_parameters;
    private final String spawn_type;
    private final int starter_countdown;
    private final String intro_mp3_file;
    private final boolean wait4teams2B_ready;

    int active_secion;
    int max_sections;

    public Spawns(MQTTOutbound mqttOutbound, JSONObject game_parameters) throws JSONException {
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
 *           ["ag30"]
 *         ]
 *       },
 *       {
 *         "role": "blue_spawn",
 *         "led": MQTT.BLUE,
 *         "name": "Team Blue",
 *         "agents": [
 *           ["ag31"]
 *         ]
 *       }
 *     ]
 *   },
 */

        this.mqttOutbound = mqttOutbound;
        this.game_parameters = game_parameters;
        all_spawns = new HashMap<>();
        spawnListeners = new ArrayList<>();
        reset();

        this.wait4teams2B_ready = game_parameters.getBoolean("wait4teams2B_ready");
        this.starter_countdown = game_parameters.getInt("starter_countdown");
        this.intro_mp3_file = game_parameters.getString("intro_mp3_file");

        JSONObject spawns = game_parameters.getJSONObject("spawns");
        spawn_type = spawns.getString("type");

        game_parameters.getJSONArray("teams").forEach(j -> {
                    JSONObject teams = (JSONObject) j;
                    final String role = teams.getString("role");
                    final String led = teams.getString("led");
                    final String team = teams.getString("team");

                    if (spawn_type.equalsIgnoreCase(SPAWN_TYPE_STATIC)) all_spawns.put(role, new StaticSpawn(role, led, team, this));
                    else if (spawn_type.equalsIgnoreCase(SPAWN_TYPE_ROLLING))
                        all_spawns.put(role, new RollingSpawn(role, led, team, this));
                    else throw new JSONException(spawn_type + " is not an acceptable spawn type");


                    // this is a list of a list of spawn agents
                    // every inner list combines all agents for a section
                    //
                    MutableInt section_number = new MutableInt(0);
                    teams.getJSONArray("agents").forEach(o -> {
                                JSONArray section = (JSONArray) o;
                                section.forEach(spawn_agent_in_this_section -> {
                                    all_spawns.get(role).add_agent(spawn_agent_in_this_section.toString(), section_number.intValue());
                                    section_number.increment();
                                });
                            }
                    );
                    max_sections = section_number.intValue() - 1;
                }
        );

    }

    public void next() {
        if (active_secion < max_sections) active_secion++;
    }

    public void reset() {
        active_secion = 0;
    }

    public void addSpawnListener(SpawnListener spawnListener){
        spawnListeners.add(spawnListener);
    }

    private void fireSpawnEvent(SpawnEvent event) {
        spawnListeners.forEach(spawnListener -> spawnListener.handleRespawnEvent(event));
    }



    public boolean process_message(String agent, String item, JSONObject message) {
        MutableBoolean message_processed = new MutableBoolean(false);
        all_spawns.values().stream().forEach(spawn -> {
            boolean already_processed = message_processed.getValue();
            message_processed.setValue(already_processed || spawn.process_message(active_secion, agent, item, message));
        });
        return message_processed.booleanValue();
    }


}
