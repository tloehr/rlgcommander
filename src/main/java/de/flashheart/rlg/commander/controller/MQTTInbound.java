package de.flashheart.rlg.commander.controller;


import de.flashheart.rlg.commander.entity.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@Log4j2
@NoArgsConstructor
/**
 * The commander subscribes to the event messages from the agents. All Messages are processed within this class.
 */
public class MQTTInbound {
    @Value("${mqtt.url}")
    public String mqtturl;
    @Value("${mqtt.inbound.topic}")
    public String inbound_topic;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.clientid}")
    public String clientid;
    MQTTOutbound mqttOutbound;
    AgentsService agentsService;
    GamesService gamesService;

    @Autowired
    public MQTTInbound(AgentsService agentsService, GamesService gamesService, MQTTOutbound mqttOutbound) {
        this.agentsService = agentsService;
        this.gamesService = gamesService;
        this.mqttOutbound = mqttOutbound;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(mqtturl, String.format("%s-%s-inbound", clientid, UUID.randomUUID()), inbound_topic);
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
            String agentid = tokens.get(tokens.size() - 2);
            String source = tokens.get(tokens.size() - 1);
            String payload = message.getPayload().toString();

            log.debug(message.toString());
            log.debug(message.getPayload().toString());

            if (source.equalsIgnoreCase("status")) {
                try {
                    JSONObject status = new JSONObject(message.getPayload().toString());
                    agentsService.store_status_from_agent(agentid, status);
                } catch (JSONException e) {
                    //agentsService.remove(agentid);
                }
            } else {
                int gameid = agentsService.getLive_agents().getOrDefault(agentid, new Agent()).getGameid();
                if (gameid > 0) {
                    try {
                        gamesService.react_to(gameid, agentid, source, new JSONObject(payload));
                    } catch (IllegalStateException ise) {
                        log.warn(ise);
                    } catch (Exception e) {
                        log.error(e);
                    }
                }
            }
        };
    }

}
