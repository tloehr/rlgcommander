package de.flashheart.rlg.commander.service;

import org.apache.commons.collections4.bidimap.TreeBidiMap;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

@Service
public class AgentsService {
    private final TreeBidiMap<String, String> agents_to_gameid;
    private final HashMap<String, JSONObject> agent_states;

    //    private final HashMap<String, String> agents_to_gameid;
//    private final Multimap<String, String> gameid_to_agents;
//
    public AgentsService() {
        this.agents_to_gameid = new TreeBidiMap<>();
        this.agent_states = new HashMap<>();
    }

    public void assign_gameid_to_agents(String id, Set<String> agents) {
        agents.forEach(agent -> agents_to_gameid.put(agent, id));
    }

    public Optional<String> get_gameid_for(String agent) {
        return Optional.ofNullable(agents_to_gameid.get(agent));
    }

    public Optional<String> get_agent_for(String gameid) {
        return Optional.ofNullable(agents_to_gameid.inverseBidiMap().get(gameid));
    }

    public void store_status_from_agent(String agent, JSONObject status) {
        agent_states.put(agent, status);
    }

    public JSONObject getLiveAgents() {
        final JSONObject jsonObject = new JSONObject();
        agent_states.forEach((agent, status) -> {
            jsonObject.put(agent, status);
        });
        return jsonObject;
    }
//
//    public Optional<Agent> getAgent(String agentid) {
//        return Optional.ofNullable(liveAgents.get(agentid));
//    }


}
