package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

import java.util.ArrayList;

@Getter
@Setter
public class RollingSpawn extends AbstractSpawn {
    int active_spawn;
    final ArrayList<SpawnAgent> spawn_list;

    public RollingSpawn(String spawn_role, String led_id, String teamname) {
        super(spawn_role, led_id, teamname);
        spawn_list = new ArrayList<>();
        active_spawn = 0;
    }

}
