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


    public RollingSpawn(String role, String led, String team, Spawns spawns) {
        super(role, led, team, spawns);
    }

}
