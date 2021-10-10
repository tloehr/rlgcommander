package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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

import java.util.Arrays;
import java.util.List;

@Configuration
@Log4j2
@NoArgsConstructor
/**
 * The commander subscribes to the event messages from the agents. All Messages are processed within this class.
 */
public class MQTTInbound {
    @Value("${mqtt.url}")
    public String mqtturl;
    @Value("${mqtt.clientid}")
    public String clientid;
    @Value("${mqtt.inbound.topic}")
    public String inbound_topic;

    AgentsService agentsService;
    GamesService gamesService;

    @Autowired
    public MQTTInbound(AgentsService agentsService, GamesService gamesService) {
        this.agentsService = agentsService;
        this.gamesService = gamesService;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(mqtturl, clientid, inbound_topic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(2); // brauchen wir das ?
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {

            String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
            String payload = message.getPayload().toString();

            List<String> topic_elements = Arrays.asList(StringUtils.splitByWholeSeparatorPreserveAllTokens(topic, "/"));
            String agentid = topic_elements.get(topic_elements.size() - 1);
            log.debug(topic + " -> " + payload);

            JSONObject event = new JSONObject(payload);

//            // split the topic
//            List<String> topic_elements = Arrays.asList(StringUtils.splitByWholeSeparatorPreserveAllTokens(topic, "/"));
//            String event = topic_elements.get(topic_elements.size() - 1); //topic.substring(topic.lastIndexOf('/') + 1);
//            String agentid = topic_elements.get(topic_elements.size() - 2);
//            log.debug("{} sent event {}", agentid, event);
            // special case for "heartbeats" from agents

            if (event.keySet().contains("status")) {
                try {
                    agentsService.put(agentid, new Agent(event.getJSONObject("status")));
                } catch (JSONException e) {
                    agentsService.remove(agentid);
                }
            } else if (event.keySet().contains("button_pressed")) {
                gamesService.react_to(agentid, event.getString("button_pressed"));
                //gamesService.getGame().ifPresent(game -> log.debug(game.getStatus()));
            }
        };
    }
}
