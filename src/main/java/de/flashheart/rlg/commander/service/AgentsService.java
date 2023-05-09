package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.misc.MyYamlConfiguration;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Service
public class AgentsService {
    ConcurrentHashMap<String, Agent> live_agents; // see MyConfiguration.java
    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;
    MyYamlConfiguration myYamlConfiguration;

    public AgentsService(MQTTOutbound mqttOutbound, BuildProperties buildProperties, ConcurrentHashMap<String, Agent> live_agents, MyYamlConfiguration myYamlConfiguration) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
        this.live_agents = live_agents;
        this.myYamlConfiguration = myYamlConfiguration;
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
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid, status));
        if (live_agents.containsKey(agentid)) my_agent.setStatus(status);
        if (!live_agents.containsKey(agentid) || status.getInt("netmonitor_cycle") == 0)
            welcome(my_agent);
        live_agents.put(agentid, my_agent);
    }

    public boolean agent_belongs_to_game(String agentid, int gameid) {
        Agent myAgent = live_agents.getOrDefault(agentid, new Agent());
        return myAgent.getGameid() == gameid;
    }

    /**
     * return a list of all agents that are not DUMMIES and have reported their status at least once
     *
     * @return
     */
    public JSONObject get_all_agent_states() {
        final JSONObject jsonObject = new JSONObject();
        live_agents.values().stream().filter(agent -> !agent.getStatus().isEmpty()).forEach(agent -> jsonObject.put(agent.getId(), agent.getStatus()));
        return jsonObject;
    }

    public List<Agent> get_all_agents() {
        ArrayList<Agent> prepared_agent_list = new ArrayList<>();
        live_agents.values().stream().sorted().forEach(agent -> {
            agent.setAp(myYamlConfiguration.getAccess_points().getOrDefault(agent.getAp().toLowerCase(), agent.getAp().toLowerCase()));
            prepared_agent_list.add(agent);
        });
        return prepared_agent_list;
    }

    public void welcome(Agent my_agent) {
        if (my_agent.getGameid() > -1) return;
        log.info("Sending welcome message to newly attached agent {}", my_agent.getId());
        //mqttOutbound.send("init", agentid);
        mqttOutbound.send("timers", MQTT.toJSON("_clearall", "0"), my_agent.getId());
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.ALL, "off"), my_agent.getId());
        // welcome-signal
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
