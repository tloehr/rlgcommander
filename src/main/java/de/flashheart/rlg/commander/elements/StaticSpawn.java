package de.flashheart.rlg.commander.elements;

import com.github.ankzz.dynamicfsm.fsm.FSM;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

@Getter
@Setter
@EqualsAndHashCode
public class StaticSpawn {
    String spawn_role;
    String led_id;
    String teamname;
    ArrayList<Pair<String, FSM>> agents;

    int active_spawn;

    public StaticSpawn(String spawn_role, String led_id, String teamname) {
        this.spawn_role = spawn_role;
        this.led_id = led_id;
        this.teamname = teamname;
        this.agents = new ArrayList<>();
        this.active_spawn = 0;
    }

    public void add_agent(String agent, FSM fsm) {
        agents.add(new ImmutablePair<>(agent, fsm));
    }

    public void next() {
        if (active_spawn < agents.size()) active_spawn++;
    }

    public void reset() {
        active_spawn = 0;
    }

    public String get_active_agent() {
        return agents.get(active_spawn).getLeft();
    }

    public FSM get_active_fsm() {
        return agents.get(active_spawn).getRight();
    }

    // pass on message, if agent is active. discard if not.
    public void process_message_on_active_agent(String message) {
        agents.get(active_spawn).getRight().ProcessFSM(message);
    }

    // pass on message, if agent is active. discard if not.
    public void process_message_on_active_agent(String agent, String message) {
        if (agents.get(active_spawn).getLeft().equalsIgnoreCase(agent))
            agents.get(active_spawn).getRight().ProcessFSM(message);
    }

    /**
     * checks if agent is part of the agents list
     *
     * @param agent
     * @return
     */
    public boolean is_spawn_agent(String agent) {
        return agents.stream().filter(stringFSMPair -> stringFSMPair.getLeft().equalsIgnoreCase(agent)).findFirst().isPresent();
    }
}
