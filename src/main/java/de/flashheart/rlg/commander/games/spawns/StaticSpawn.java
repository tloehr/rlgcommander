package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class StaticSpawn extends AbstractSpawn {
    int active_spawn;
    SpawnAgent spawn;

    public StaticSpawn(String spawn_role, String led_id, String teamname, MQTTOutbound mqttOutbound) {
        super(spawn_role, led_id, teamname, mqttOutbound);
    }

    @Override
    AbstractSpawn add_agent(String agent) {
        spawn = new SpawnAgent(agent, this);
        return this;
    }

    @Override
    void cleanup() {

    }
}
