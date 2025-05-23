package de.flashheart.rlg.commander.controller;


import com.google.common.collect.HashBiMap;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@Log4j2
/**
 * The commander subscribes to the event messages from the agents. All Messages are processed within this class.
 */
public class MQTTInbound {
    @Value("${mqtt.host}")
    public String mqtt_host;
    @Value("${mqtt.port}")
    public String mqtt_port;
    @Value("${mqtt.inbound.topic}")
    public String inbound_topic;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.client_id}")
    public String client_id;
    MQTTOutbound mqttOutbound;
    AgentsService agentsService;
    GamesService gamesService;
    ConcurrentHashMap<String, Agent> live_agents; // see MyConfiguration.java
    HashBiMap<String, String> agent_replacement_map;

    public MQTTInbound(AgentsService agentsService, GamesService gamesService, MQTTOutbound mqttOutbound, ConcurrentHashMap<String, Agent> live_agents, HashBiMap<String, String> agent_replacement_map) {
        this.agentsService = agentsService;
        this.gamesService = gamesService;
        this.mqttOutbound = mqttOutbound;
        this.live_agents = live_agents;
        this.agent_replacement_map = agent_replacement_map;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(String.format("tcp://%s:%s", mqtt_host, mqtt_port), String.format("%s-inbound", client_id), inbound_topic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {
            String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
            List<String> tokens = Collections.list(new StringTokenizer(topic, "/")).stream().map(token -> (String) token).collect(Collectors.toList());
            String incoming_from = tokens.get(tokens.size() - 2);
            String source = tokens.get(tokens.size() - 1);
            String payload = message.getPayload().toString();

            if (source.equalsIgnoreCase("status")) {
                log.trace(message.toString());
                log.trace(message.getPayload().toString());
                try {
                    JSONObject status = new JSONObject(message.getPayload().toString());
                    agentsService.agent_reported_status(incoming_from, status);
                } catch (JSONException e) {
                    log.error(e.getMessage());
                }
            } else { // must be a button or rfid
                log.trace(message.toString());
                log.trace(message.getPayload().toString());

                // ignoring replaced agent's event
                if (agent_replacement_map.containsKey(incoming_from)) return;

                String forward_to_agent = incoming_from;
                if (agent_replacement_map.containsValue(incoming_from)) {
                    forward_to_agent = agent_replacement_map.inverse().get(incoming_from);
                    log.debug("INBOUND: message from agent {} redirected to agent {}", incoming_from, forward_to_agent);
                }
                // the game_id is inherited from the old_agent
                int game_id = live_agents.containsKey(forward_to_agent) ? live_agents.get(forward_to_agent).getGameid() : -1;

                agentsService.agent_reported_event(forward_to_agent, source, new JSONObject(payload));
                // report to running game
                if (game_id > 0) {
                    try {
                        gamesService.process_message(game_id, forward_to_agent, source, new JSONObject(payload));
                    } catch (IllegalStateException ise) {
                        log.warn(ise.getMessage());
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
            }
        };
    }

}
