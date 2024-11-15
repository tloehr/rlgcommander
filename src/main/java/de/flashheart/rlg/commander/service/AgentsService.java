package de.flashheart.rlg.commander.service;

import com.google.common.collect.HashBiMap;
import de.flashheart.rlg.commander.Exception.AgentInUseException;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.configs.MyYamlConfiguration;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Log4j2
@Service
public class AgentsService {
    private final HashBiMap<String, String> agent_replacement_map;
    ConcurrentHashMap<String, Agent> live_agents; // see MyConfiguration.java
    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;
    MyYamlConfiguration myYamlConfiguration;

    @SneakyThrows
    public AgentsService(MQTTOutbound mqttOutbound, BuildProperties buildProperties, ConcurrentHashMap<String, Agent> live_agents, MyYamlConfiguration myYamlConfiguration, HashBiMap<String, String> agent_replacement_map) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
        this.live_agents = live_agents;
        this.myYamlConfiguration = myYamlConfiguration;
        this.agent_replacement_map = agent_replacement_map;
    }

    public void assign_game_id_to_agents(int game_id, Set<String> agent_ids) throws AgentInUseException {
        // first check that the agents are still free to use
        agent_ids.forEach(agent_id -> {
            if (live_agents.containsKey(agent_id) && !live_agents.get(agent_id).isFree()) {
                throw new AgentInUseException(String.format("Agent %s is already in use", agent_id));
            }
        });
        // if the agents are all free to use, we can carry on
        agent_ids.forEach(agent_id -> {
            Agent my_agent = live_agents.getOrDefault(agent_id, new Agent(agent_id));
            my_agent.setGameid(game_id);
            live_agents.put(agent_id, my_agent);
        });
    }

    public void free_agents(Set<String> agent_ids) {
        agent_ids.forEach(agent_id -> {
            if (live_agents.containsKey(agent_id)) {
                live_agents.get(agent_id).setFree();
            }
        });
    }


    public Set<Agent> get_agents_for(int gameid) {
        return live_agents.values().stream().filter(agent -> agent.getGameid() == gameid).collect(Collectors.toSet());
    }

    /**
     * remove an agent from the list.
     *
     * @param agentid
     */
    public void remove_agent(String agentid) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        if (my_agent.getGameid() > -1) return; // only when not in game
        live_agents.remove(agentid);
    }

    public void agent_reported_status(String agentid, JSONObject status) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid, status));
        if (live_agents.containsKey(agentid)) my_agent.setStatus(status);
        // status_counter==0 is valid when an agent restarts - so it will receive another welcome message
        // even if it was known before
        if (!live_agents.containsKey(agentid) || status.getInt("status_counter") == 0)
            welcome(my_agent);
        live_agents.put(agentid, my_agent);
    }

    public void agent_reported_event(String agentid, String device, JSONObject message) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        // we will send a welcome message, if the agent is yet unknown
        if (!live_agents.containsKey(agentid))
            welcome(my_agent);
        live_agents.putIfAbsent(agentid, my_agent);

        if (device.equalsIgnoreCase("rfid"))
            my_agent.event_occured(Agent.RFID, message.optString("uid", "none"));
        if (device.matches("btn01"))
            my_agent.event_occured(Agent.BTN01, "");
        if (device.matches("btn02"))
            my_agent.event_occured(Agent.BTN02, "");

    }
//
//    public boolean agent_belongs_to_game(String agentid, int gameid) {
//        Agent myAgent = live_agents.getOrDefault(agentid, new Agent());
//        return myAgent.getGameid() == gameid;
//    }

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
        mqttOutbound.send(MQTT.CMD_TIMERS, MQTT.toJSON("_clearall", "0"), my_agent.getId());
        mqttOutbound.send(MQTT.CMD_PLAY, MQTT.toJSON("channel", "", "subpath", "", "soundfile", ""), my_agent.getId());
        mqttOutbound.send(MQTT.CMD_ACOUSTIC, MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), my_agent.getId());
        // welcome-signal
        mqttOutbound.send(MQTT.CMD_VISUAL,
                "welcome_signal",
                my_agent.getId());
        mqttOutbound.send(MQTT.CMD_PAGED, MQTT.page("page0", "Welcome " + my_agent.getId(),
                "commander " + buildProperties.getVersion() + "b" + buildProperties.get("buildNumber"),
                "${agtype} ${agversion}b${agbuild}",
                "2me@flashheart.de ${wifi_signal}"), my_agent.getId());
    }

    public void test_agent(String agentid, String command, JSONObject pattern) {
        Agent my_agent = live_agents.getOrDefault(agentid, new Agent(agentid));
        if (my_agent.getGameid() > -1) return; // only when not in game
        if (agent_replacement_map.values().contains(agentid))
            return; // agents that are used for replacements are also out of bounds
        mqttOutbound.send(command, pattern, agentid);
    }

    public void test_agents(List<String> agents, String str_body) {
        JSONObject body = new JSONObject(str_body);
        agents.forEach(agent -> test_agent(agent, body.getString("command"), body.getJSONObject("pattern")));
    }

    /**
     * turn off all lights and sirens of unused agents.
     * but not those which are in use as a replacement
     */
    public void powersave_unused_agents() {
        get_agents_for(-1).stream()
                .filter(agent -> !agent_replacement_map.containsValue(agent))
                .forEach(agent ->
                        powersave(agent.getId())
                );
    }

    private void powersave(String agent) {
        mqttOutbound.send("visual", MQTT.toJSON(MQTT.LED_ALL, MQTT.OFF), agent);
        mqttOutbound.send("acoustic", MQTT.toJSON(MQTT.SIR_ALL, MQTT.OFF), agent);
    }

    /**
     * if an agent breaks during a match, we will want to replace it without
     * restarting the match
     * <p>
     * as a real replacement would be quite difficult and complicated it is much better to stick
     * with the original configuration and simply replace the agent-ids on their way in and out
     * so that the mqtt_outbound class quickly replaces the id before broadcasting.
     * so does the process_external_messages method, too.
     * <p>
     * here we are only maintaining a replacement map, that is used by those to inbound and outbound facilities.
     *
     * @param old_agent the broken agent to be replaced
     * @param new_agent the agent to step in. if this field is empty, an assigment is removed.
     */
    public void replace_agent(String old_agent, String new_agent) {
        if (new_agent.isEmpty()) {
            if (agent_replacement_map.containsKey(old_agent)) {
                powersave(agent_replacement_map.get(old_agent));
            }
            agent_replacement_map.remove(old_agent);
            //todo this is not working
            mqttOutbound.resend_commands(old_agent);
            return;
        }
        // ensures, that key value pairs are unique.
        agent_replacement_map.inverse().remove(new_agent);
        agent_replacement_map.put(old_agent, new_agent);
        mqttOutbound.resend_commands(old_agent);

        log.debug("agent_replacement_map: {}", agent_replacement_map);
    }

    /**
     * send a welcome message to all unused agents.
     * But not to those which are used as a replacement.
     */
    public void welcome_unused_agents() {
        get_agents_for(-1).stream()
                .filter(agent -> !agent_replacement_map.containsValue(agent))
                .forEach(this::welcome);
    }

    public void clear_replacements() {
        agent_replacement_map.clear();
    }

    public HashBiMap<String, String> get_agent_replacement_map() {
        return agent_replacement_map;
    }
}
