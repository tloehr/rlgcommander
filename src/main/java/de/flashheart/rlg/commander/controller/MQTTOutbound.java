package de.flashheart.rlg.commander.controller;

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

import java.util.Collection;
import java.util.HashSet;

@Configuration
@Log4j2
@NoArgsConstructor
public class MQTTOutbound {
    @Value("${mqtt.url}")
    public String mqtturl;
    @Value("${mqtt.prefix}")
    public String prefix;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.retained}")
    public boolean retained;
    @Value("${mqtt.clientid}")
    public String clientid;

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
        options.setMaxInflight(1000);
//        options.setUserName("username");
//        options.setPassword("password".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {  // for outbound only
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(clientid + (int) (Math.random() * 10000) + "-mqtt-outbound", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(prefix);
        messageHandler.setDefaultQos(qos);
        messageHandler.setDefaultRetained(retained);
        return messageHandler;
    }


    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    public void send(String cmd, String agent) {
        send(cmd, new JSONObject(), agent);
    }

    public void send(String cmd, JSONObject payload, String agent) {
        if (agent.isEmpty()) return;
        HashSet<String> agents = new HashSet<>();
        agents.add(agent);
        send(cmd, payload, agents);
    }

    public void send(String cmd, JSONObject payload, Collection<String> agents) {
        if (agents.isEmpty()) return;
        agents.forEach(agent -> {
            log.debug("sending {}", prefix + "/" + agent + "/" + cmd);
            send(agent + "/" + cmd, payload);
        });
    }

    public void send(String topic, JSONObject payload) {
        gateway.sendToMqtt(payload.toString(), prefix + topic);
    }

    public void send(String topic, JSONObject payload, boolean retained) {
        gateway.sendToMqtt(payload.toString(), retained, prefix + topic);
    }


}
