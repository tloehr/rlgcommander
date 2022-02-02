package de.flashheart.rlg.commander.controller;


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
            //JSONObject event = new JSONObject(message.getPayload().toString());
            // GenericMessage [payload=, headers={mqtt_receivedRetained=false, mqtt_id=1, mqtt_duplicate=false, id=1ed88f18-b360-3ac6-4797-b28e2fc16857, mqtt_receivedTopic=rlg/g1/evt/ag01/btn01, mqtt_receivedQos=1, timestamp=1641739880253}]
            //2022-01-20 11:49:42.658 DEBUG 57357 --- [27-mqtt-inbound] d.f.r.commander.controller.MQTTInbound   : GenericMessage [payload=down, headers={mqtt_receivedRetained=false, mqtt_id=1, mqtt_duplicate=false, id=81c28622-3d91-66c4-9ba1-0b7432b043d2, mqtt_receivedTopic=rlg/evt/ag01/btn01, mqtt_receivedQos=2, timestamp=1642675782657}]
            //2022-01-20 11:49:42.751 DEBUG 57357 --- [27-mqtt-inbound] d.f.r.commander.controller.MQTTInbound   : GenericMessage [payload=up, headers={mqtt_receivedRetained=false, mqtt_id=2, mqtt_duplicate=false, id=20ce02d3-172d-4fe5-e6e3-89998c1bf9a8, mqtt_receivedTopic=rlg/evt/ag01/btn01, mqtt_receivedQos=2, timestamp=1642675782751}]
            // rlg/g1/evt/ag01/btn01
            String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
            List<String> tokens = Collections.list(new StringTokenizer(topic, "/")).stream().map(token -> (String) token).collect(Collectors.toList());
            String agent = tokens.get(tokens.size() - 2);
            String source = tokens.get(tokens.size() - 1);
            String payload = message.getPayload().toString();

//            {
//                "mqtt-broker":"localhost", "wifi":"PERFECT", "mqtt_connect_tries":1, "network_stats":{
//                "access_point":"Not-Associated", "essid":"!DESKTOP!", "last_ping":"02.02.22, 16:04:03", "link":
//                "not zelda", "freq":"good vibes", "ping_max":"0.043", "bitrate":"super", "ap":"!DESKTOP!", "txpower":
//                "high", "ping_success":"reached successfully", "powermgt":"off", "ping_loss":"0%", "ping_min":
//                "max ", "ping_avg":" 0.043", "signal":"-30", "ping_host":"localhost"
//            },"version":"1.0.1.348", "timestamp":"02.02.22, 16:04:03"
//            }

            if (source.equalsIgnoreCase("status")) {
                log.debug(message.getPayload().toString());
                try {
                    JSONObject status = new JSONObject(message.getPayload().toString());
                    agentsService.store_status_from_agent(agent, status);
                } catch (JSONException e) {
                    //agentsService.remove(agentid);
                }
            } else {
                agentsService.get_gameid_for(agent).ifPresent(gameid -> {
                    log.debug(message.toString());
                    gamesService.react_to(gameid, agent, source, new JSONObject(payload));
                });
            }
        };
    }

}
