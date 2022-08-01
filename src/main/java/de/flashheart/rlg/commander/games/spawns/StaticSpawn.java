package de.flashheart.rlg.commander.games.spawns;

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

    public StaticSpawn(String role, String led, String team) {
        super(role, led, team);
    }

    @Override
    public void add_agent(String agent) {
        spawn = new SpawnAgent(agent, this);
    }


}
