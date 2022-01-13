package de.flashheart.rlg.commander.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentsService {
    HashMap<String, String> agents_to_gameid;
    private final Multimap<String, String> gameid_to_agents;


    public AgentsService() {
        this.agents_to_gameid = new HashMap<>();
        this.gameid_to_agents = HashMultimap.create();
    }

    public void assign_gameid_to_agents(String id, Set<String> agents){
        agents.forEach(agent -> {
            gameid_to_agents.put(id, agent);
            agents_to_gameid.put(agent, id);
        });
    }

    public Optional<String> get_gameid_for(String agent){
        return Optional.ofNullable(agents_to_gameid.get(agent));
    }

//    public void put(String agentid, Agent agent) {
//        liveAgents.put(agentid, agent);
//    }
//
//    public void remove(String agentid) {
//        liveAgents.remove(agentid);
//    }
//
//    public List<Agent> getLiveAgents() {
//        return new ArrayList<>(liveAgents.values());
//    }
//
//    public Optional<Agent> getAgent(String agentid) {
//        return Optional.ofNullable(liveAgents.get(agentid));
//    }


}
