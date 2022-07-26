package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.games.events.RespawnEvent;
import de.flashheart.rlg.commander.games.events.RespawnListener;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Log4j2
public class Spawns {
    final HashMap<String, AbstractSpawn> all_spawns;
    final List<RespawnListener> respawn_listeners;

    public Spawns() {
        all_spawns = new HashMap<>();
        respawn_listeners = new ArrayList<>();
    }

    public Spawns add_static_spawn(String role, String led, String team) {
        all_spawns.put(role, new StaticSpawn(role, led, team));
        return this;
    }

    public void addRespawnListener(RespawnListener toAdd) {
        respawn_listeners.add(toAdd);
    }

    private void fireStateReached(RespawnEvent event) {
        respawn_listeners.forEach(respawnListener -> respawnListener.onRespawn(event));
    }


}
