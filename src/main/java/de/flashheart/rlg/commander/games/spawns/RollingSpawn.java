package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@EqualsAndHashCode
public class RollingSpawn extends AbstractSpawn {
    int active_spawn;
    final ArrayList<SpawnAgent> spawn_list;

    public RollingSpawn(String spawn_role, String led_id, String teamname, MQTTOutbound mqttOutbound, ) {
        super(spawn_role, led_id, teamname, mqttOutbound, gamedescription, pages);
        spawn_list = new ArrayList<>();
    }

    @Override
    AbstractSpawn add_agent(String agent) {
        spawn_list.add(new SpawnAgent(agent, this));
        return this;
    }

    void next() {
        if (active_spawn >= spawn_list.size() + 1) return;
        active_spawn++;
    }

    void reset(){
        active_spawn = 0;
    }

    @Override
    void cleanup() {
        spawn_list.clear();
    }
}
