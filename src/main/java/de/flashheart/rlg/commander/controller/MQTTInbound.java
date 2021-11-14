package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.service.Agent;
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

import javax.annotation.PreDestroy;
import java.util.Arrays;

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
            JSONObject event = new JSONObject(message.getPayload().toString());

            // special case for "heartbeats" from agents
            if (event.keySet().contains("status")) {
                try {
                    JSONObject status = event.getJSONObject("status");
                    agentsService.put(status.getString("agentid"), new Agent(status));
                } catch (JSONException e) {
                    //agentsService.remove(agentid);
                }
            } else {
                gamesService.react_to(event.getString("agentid"), event);
                //gamesService.getGame().ifPresent(game -> log.debug(game.getStatus()));
            }
        };
    }

//    @PreDestroy
//    public void before_shutdown() {
//        mqttOutbound.sendCommandTo("all", MQTT.init_agent());
//        Arrays.asList(new String[]{"sirens", "leds", "capture_points", "red_spawn", "blue_spawn", "green_spawn", "spawns"})
//                .forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));
//    }

}
