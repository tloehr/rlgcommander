package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Agent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Service
public class AgentsService {
    ConcurrentHashMap<String, Agent> live_agents; // see MyConfiguration.java
    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;

    public AgentsService(MQTTOutbound mqttOutbound, BuildProperties buildProperties, ConcurrentHashMap<String, Agent> live_agents) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
        this.live_agents = live_agents;
    }


    public void assign_gameid_to_agents(int gameid, Set<String> agentids) {
        agentids.forEach(agentid -> {
            Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
            my_agent.setGameid(gameid);
            live_agents.put(agentid, my_agent);
        });
    }

    public void remove_gameid_from_agents(Set<String> agentids) {
        assign_gameid_to_agents(-1, agentids);
    }

    public Set<Agent> get_agents_for_gameid(int gameid) {
        return live_agents.values().stream().filter(agent -> agent.getGameid() == gameid).collect(Collectors.toSet());
    }

    public void agent_reported_status(String agentid, JSONObject status) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        my_agent.setLast_state(status);
        if (!live_agents.containsKey(agentid) || status.getInt("netmonitor_cycle") == 0)
            welcome(my_agent); // new agenta receive a default signal setting, so do reconnecting agents
        live_agents.put(agentid, my_agent);
    }

    public boolean agent_belongs_to_game(String agentid, int gameid) {
        Agent myAgent = live_agents.getOrDefault(agentid, new Agent("dummy"));
        return myAgent.getGameid() == gameid;
    }

    /**
     * return a list of all agents that are not DUMMIES and have reported their status at least once
     *
     * @return
     */
    public JSONObject get_all_agent_states() {
        final JSONObject jsonObject = new JSONObject();
        live_agents.values().stream().filter(agent -> !agent.getLast_state().isEmpty()).forEach(agent -> jsonObject.put(agent.getId(), agent.getLast_state()));
        return jsonObject;
    }

    public void welcome(Agent my_agent) {
        if (my_agent.getGameid() > -1) return;
        log.info("Sending welcome message to newly attached agent {}", my_agent.getId());
        //mqttOutbound.send("init", agentid);
        mqttOutbound.send("timers", MQTT.toJSON("_clearall", "0"), my_agent.getId());
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.ALL, "off"), my_agent.getId());
        // welcome signal
        mqttOutbound.send("visual",
                MQTT.toJSON(
                        MQTT.WHITE, "60:on,500;off,500",
                        MQTT.YELLOW, "60:on,500;off,500",
                        MQTT.BLUE, "60:on,500;off,500",
                        MQTT.RED, "60:off,500;on,500;off,1",
                        MQTT.GREEN, "60:off,500;on,500;off,1"),
                my_agent.getId());
        mqttOutbound.send("paged", MQTT.page("page0", "Welcome " + my_agent.getId(),
                "cmdr " + buildProperties.getVersion() + "b" + buildProperties.get("buildNumber"),
                "agnt ${agversion}b${agbuild}",
                "2me@flashheart.de"), my_agent.getId());
    }

    /**
     * sends a test signal to see if the agent is working properly
     *
     * @param agentid  to be tested
     * @param deviceid specific device like "led_red, sir1..."
     */
    public void test_agent(String agentid, String deviceid, String pattern) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        if (my_agent.getGameid() > -1) return; // only when not in game
        if (deviceid.toLowerCase().matches("play|stop")) {
            String song = deviceid.equalsIgnoreCase("play") ? "<random>" : "";
            mqttOutbound.send("play", MQTT.toJSON("subpath", "pause", "soundfile", song), agentid);
        } else {
            String topic = deviceid.matches(MQTT.ACOUSTICS) ? "acoustic" : "visual";
            mqttOutbound.send(topic, MQTT.toJSON(deviceid, pattern), my_agent.getId());
        }
    }

    /**
     * turn off all lights and sirens
     */
    public void powersave_unused_agents() {
        get_agents_for_gameid(-1).forEach(agent -> {
            mqttOutbound.send("visual", MQTT.toJSON(MQTT.ALL, "off"), agent.getId());
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.ALL, "off"), agent.getId());
        });
    }

    public void welcome_unused_agents() {
        get_agents_for_gameid(-1).forEach(agent -> welcome(agent));
    }

}
