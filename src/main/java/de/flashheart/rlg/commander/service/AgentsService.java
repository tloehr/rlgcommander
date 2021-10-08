package de.flashheart.rlg.commander.service;

import org.springframework.stereotype.Service;
import org.springframework.web.cors.reactive.PreFlightRequestWebFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
public class AgentsService {
    HashMap<String, Agent> liveAgents;

    public AgentsService() {
        this.liveAgents = new HashMap<>();
    }

    public void put(String agentid, Agent agent) {
        liveAgents.put(agentid, agent);
    }

    public void remove(String agentid) {
        liveAgents.remove(agentid);
    }

    public List<Agent> getLiveAgents() {
        return new ArrayList<>(liveAgents.values());
    }

    public Optional<Agent> getAgent(String agentid){
        return Optional.ofNullable(liveAgents.get(agentid));
    }


}
