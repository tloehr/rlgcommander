package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;

@Configuration
@Log4j2
@NoArgsConstructor
public class MQTTOutbound {
    @Value("${mqtt.url}")
    public String mqtturl;
    @Value("${mqtt.clientid}")
    public String clientid;
    @Value("${mqtt.outbound.topic}")
    public String outbound_topic;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.retained}")
    public boolean retained;

    ApplicationContext applicationContext;
    AgentsService agentsService;
    MyGateway gateway;

    @Autowired
    public MQTTOutbound(ApplicationContext applicationContext, AgentsService agentsService) {
        this.applicationContext = applicationContext;
        this.agentsService = agentsService;
        gateway = applicationContext.getBean(MyGateway.class);
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() { // for outbound only
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqtturl});
        options.setMaxInflight(30);
//        options.setUserName("username");
//        options.setPassword("password".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {  // for outbound only
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(UUID.randomUUID().toString(), mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(outbound_topic);
        messageHandler.setDefaultQos(qos);
        messageHandler.setDefaultRetained(retained);
        messageHandler.setQosExpressionString("null");
        messageHandler.setRetainedExpressionString("null");
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    //todo: vergessen?
    public void setRemainingTime(LocalDateTime estimatedEndTime) {
        final long remaining = LocalDateTime.now().until(estimatedEndTime, ChronoUnit.MILLIS);
        gateway.sendToMqtt(new JSONObject().put("time", remaining).toString(), outbound_topic + "all/remaining");
    }

    public void sendCommandTo(Collection<Agent> agents,  JSONObject jsonObject) {
        agents.forEach(agent -> sendCommandTo(agent, jsonObject));
    }

    public void sendCommandTo(String subchannel, JSONObject jsonObject) {
        gateway.sendToMqtt(jsonObject.toString(), outbound_topic + subchannel+ "/");
      }

    public void sendCommandTo(Agent agent,  JSONObject jsonObject) {
        sendCommandTo(agent.getAgentid(), jsonObject);
    }

    @PreDestroy
    void cleanup() {
        // das klappt so nicht
        // agentStandbyPattern(agentsService.getLiveAgents());
    }



//    public void agentStandbyPattern(Collection<Agent> agents) {
//        sendCommandTo(agents, new JSONObject()
//                .put("sir_all", "off")
//                .put("led_wht", "∞:on,350;off,3700")
//                .put("led_red", "∞:off,350;on,350;off,3350")
//                .put("led_ylw", "∞:off,700;on,350;off,3000")
//                .put("led_grn", "∞:off,1050;on,350;off,2650")
//                .put("led_blu", "∞:off,1400;on,350;off,2300")
//        );
//        sendCommandTo(agents, "line_display", new JSONObject()
//                .put("1", "Agent").put("2", "standing by")
//        );
//        sendCommandTo(agents, "matrix_display", new JSONObject()
//                .put("1", "Agent").put("2", "standing by")
//        );
//    }


}
