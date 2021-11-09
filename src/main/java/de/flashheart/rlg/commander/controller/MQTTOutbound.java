package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.mechanics.Game;
import de.flashheart.rlg.commander.misc.Tools;
import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.json.JSONArray;
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
import java.util.Arrays;
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

    public void sendCommandTo(Collection<Agent> agents, JSONObject... jsonObject) {
        agents.forEach(agent -> sendCommandTo(agent, jsonObject));
    }

    public void sendCommandTo(String subchannel, JSONObject... data) {
        String header = outbound_topic + subchannel;
        String _data = Tools.merge(data).toString();
        log.debug("sending: {} - {}", header, _data);
        gateway.sendToMqtt(_data, header);
    }

    public void sendCommandTo(Agent agent, JSONObject... jsonObject) {
        sendCommandTo(agent.getAgentid(), jsonObject);
    }

}
