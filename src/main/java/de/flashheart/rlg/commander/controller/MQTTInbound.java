package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(mqtturl, clientid + (int) (Math.random() * 10000) + "-mqtt-inbound", inbound_topic);
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
            //JSONObject event = new JSONObject(message.getPayload().toString());
            // GenericMessage [payload=, headers={mqtt_receivedRetained=false, mqtt_id=1, mqtt_duplicate=false, id=1ed88f18-b360-3ac6-4797-b28e2fc16857, mqtt_receivedTopic=rlg/g1/evt/ag01/btn01, mqtt_receivedQos=1, timestamp=1641739880253}]
            // rlg/g1/evt/ag01/btn01
            String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
            List<String> tokens = Collections.list(new StringTokenizer(topic, "/")).stream().map(token -> (String) token).collect(Collectors.toList());
            String gameid = tokens.get(tokens.size() - 3);
            String agentid = tokens.get(tokens.size() - 2);
            String source = tokens.get(tokens.size() - 1); // which button ?

            if (source.equalsIgnoreCase("status")) {
                log.debug(message.getPayload().toString());
//                try {
//                    JSONObject status = event.getJSONObject("status");
//                    agentsService.put(status.getString("agentid"), new Agent(status));
//                } catch (JSONException e) {
//                    //agentsService.remove(agentid);
//                }
            } else {
                gamesService.react_to(agentid, new JSONObject().put("source", source));
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
