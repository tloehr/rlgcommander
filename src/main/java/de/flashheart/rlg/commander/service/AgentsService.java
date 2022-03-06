package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.entity.Agent;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

@Service
public class AgentsService {
    private final HashMap<String, Agent> live_agents;
    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;

    //    private final HashMap<String, String> agents_to_gameid;
//    private final Multimap<String, String> gameid_to_agents;
//
    public AgentsService(MQTTOutbound mqttOutbound, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
        live_agents = new HashMap<>();
    }

//    public Optional<String> get_agent_for(String gameid) {
//        return Optional.ofNullable(agents_to_gameid.inverseBidiMap().get(gameid));
//    }


    public HashMap<String, Agent> getLive_agents() {
        return live_agents;
    }

    public void assign_gameid_to_agents(int gameid, Set<String> agentids) {
        agentids.forEach(agentid -> {
            Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
            my_agent.setGameid(gameid);
            live_agents.put(agentid, my_agent);
            if (my_agent.getGameid() == -1) welcome(agentid);
        });
    }

    public void store_status_from_agent(String agentid, JSONObject status) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        my_agent.setLast_state(status);
        if (my_agent.getGameid() == -1) welcome(agentid);
        live_agents.put(agentid, my_agent);
    }

    public boolean agent_belongs_to_game(String agentid, int gameid) {
        Agent myAgent = live_agents.getOrDefault(agentid, new Agent("dummy"));
        return myAgent.getGameid() == gameid;
    }

    public JSONObject get_all_agent_states() {
        final JSONObject jsonObject = new JSONObject();
        live_agents.values().forEach(agent -> {
            jsonObject.put(agent.getId(), agent.getLast_state());
        });
        return jsonObject;
    }

    public void welcome(String agentid) {
        mqttOutbound.send("init", agentid);
        mqttOutbound.send("signals", MQTT.toJSON("led_wht", "infty:on,500;off,500", "led_ylw", "infty:on,500;off,500", "led_blu", "infty:on,500;off,500",
                "led_red", "infty:off,500;on,500", "led_grn", "infty:off,500;on,500"), agentid);
        mqttOutbound.send("paged", MQTT.page("page0", "Waiting for a game",
                "cmdr " + buildProperties.getVersion() + "." + buildProperties.get("buildNumber"),
                "agnt ${agversion}.${agbuild}",
                "RLGS2 @flashheart.de"), agentid);
    }

}
