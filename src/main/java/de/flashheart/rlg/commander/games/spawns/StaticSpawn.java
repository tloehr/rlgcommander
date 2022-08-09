package de.flashheart.rlg.commander.games.spawns;

import com.google.common.collect.HashMultimap;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

@Getter
@Setter
@EqualsAndHashCode
public class StaticSpawn extends AbstractSpawn {
    SpawnAgent spawn;


    public StaticSpawn(String role, String led, String team, Spawns spawns) {
        super(role, led, team, spawns);
    }

}
