package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.misc.MyYamlConfiguration;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
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

    @SneakyThrows
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

    /**
     * remove an agent from the list.
     *
     * @param agentid
     */
    public void remove_agent(String agentid) {
        live_agents.remove(agentid);
    }

    public void agent_reported_status(String agentid, JSONObject status) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid, status));
        if (live_agents.containsKey(agentid)) my_agent.setStatus(status);
        if (!live_agents.containsKey(agentid) || status.getInt("netmonitor_cycle") == 0)
            welcome(my_agent);
        live_agents.put(agentid, my_agent);
    }

    public void agent_reported_event(String agentid, String device, JSONObject message) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        live_agents.putIfAbsent(agentid, my_agent);
        if (device.equalsIgnoreCase("rfid")) {
            my_agent.setLast_rfid_uid(message.optString("uid", "none"));
            my_agent.setTimestamp(LocalDateTime.now());
        }
        if (device.matches("btn0\\d")) {
            my_agent.setLast_button(device.toLowerCase());
            my_agent.setTimestamp(LocalDateTime.now());
        }
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
            // quickly replace user addresses of APs with defined names
            agent.setAp(myYamlConfiguration.getAccess_points().getOrDefault(agent.getAp(), agent.getAp()));
            prepared_agent_list.add(agent);
        });
        return prepared_agent_list;
    }

    public void welcome(Agent my_agent) {
        if (my_agent.getGameid() > -1) return;
        log.info("Sending welcome message to newly attached agent {}", my_agent.getId());
        //mqttOutbound.send("init", agentid);
        mqttOutbound.send("timers", MQTT.toJSON("_clearall", "0"), my_agent.getId());
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR_ALL, "off"), my_agent.getId());
        // welcome-signal
        mqttOutbound.send("visual",
                "welcome_signal",
                my_agent.getId());
        mqttOutbound.send("paged", MQTT.page("page0", "Welcome " + my_agent.getId(),
                "commander " + buildProperties.getVersion() + "b" + buildProperties.get("buildNumber"),
                "    agent ${agversion}b${agbuild}",
                "2me@flashheart.de ${wifi_signal}"), my_agent.getId());
    }

    public void test_agent(String agentid, String command, JSONObject pattern) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        if (my_agent.getGameid() > -1) return; // only when not in game
        mqttOutbound.send(command, pattern, agentid);
    }

    /**
     * turn off all lights and sirens
     */
    public void powersave_unused_agents() {
        get_agents_for_gameid(-1).forEach(agent -> {
            mqttOutbound.send("visual", MQTT.toJSON(MQTT.LED_ALL, "off"), agent.getId());
            mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR_ALL, "off"), agent.getId());
        });
    }

    public void welcome_unused_agents() {
        get_agents_for_gameid(-1).forEach(agent -> welcome(agent));
    }

}
