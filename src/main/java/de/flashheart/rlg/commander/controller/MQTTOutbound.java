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

import java.util.Arrays;
import java.util.Collection;

@Configuration
@Log4j2
@NoArgsConstructor
public class MQTTOutbound {
    @Value("${mqtt.url}")
    public String mqtturl;
    @Value("${mqtt.clientid}")
    public String clientid;
    @Value("${mqtt.prefix}")
    public String prefix;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.retained}")
    public boolean retained;

    private final String TOPIC_GAMEID = "g1"; // maybe for multiple games in a later version

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
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(clientid, mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(prefix);
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


    public void send(String channel, JSONObject payload, Collection<String> agents) {
        Arrays.asList(agents).forEach(agent -> {
            gateway.sendToMqtt(payload.toString(), prefix + channel + "/" + agent);
        });

    }


}
