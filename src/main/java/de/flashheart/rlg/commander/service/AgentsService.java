package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.entity.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Set;

@Log4j2
@Service
public class AgentsService {
    private final HashMap<String, Agent> live_agents;
    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;

    public AgentsService(MQTTOutbound mqttOutbound, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
        live_agents = new HashMap<>();
    }

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

    public void agent_reported_status(String agentid, JSONObject status) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        my_agent.setLast_state(status);
        if (!live_agents.containsKey(agentid)) welcome(agentid); // new agenta receive a default signal setting
        live_agents.put(agentid, my_agent);
    }

    public boolean agent_belongs_to_game(String agentid, int gameid) {
        Agent myAgent = live_agents.getOrDefault(agentid, new Agent("dummy"));
        return myAgent.getGameid() == gameid;
    }

    /**
     * return a list of all agents that are not DUMMIES and have reported their status at least once
     * @return
     */
    public JSONObject get_all_agent_states() {
        final JSONObject jsonObject = new JSONObject();
        live_agents.values().stream().filter(agent -> !agent.getLast_state().isEmpty()).forEach(agent -> jsonObject.put(agent.getId(), agent.getLast_state()));
        return jsonObject;
    }

    public void welcome(String agentid) {
        log.info("Sending welcome message to newly attached agent {}", agentid);
        mqttOutbound.send("init", agentid);
        mqttOutbound.send("signals", MQTT.toJSON("led_wht", "infty:on,500;off,500", "led_ylw", "infty:on,500;off,500", "led_blu", "infty:on,500;off,500",
                "led_red", "infty:off,500;on,500", "led_grn", "infty:off,500;on,500"), agentid);
        mqttOutbound.send("paged", MQTT.page("page0", "Welcome " + agentid,
                "cmdr " + buildProperties.getVersion() + "." + buildProperties.get("buildNumber"),
                "agnt ${agversion}.${agbuild}",
                "2me@flashheart.de"), agentid);
    }

}
