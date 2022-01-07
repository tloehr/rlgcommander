package de.flashheart.rlg.commander.controller;

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

import java.util.UUID;

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

//    public void sendCommandTo(Collection<Agent> agents, JSONObject... jsonObject) {
//        agents.forEach(agent -> sendCommandTo(agent, jsonObject));
//    }
//
//    // sends a simple command without any parameters
//    public void sendCommandTo(String subchannel, String command) {
//        String header = outbound_topic + subchannel;
//        gateway.sendToMqtt(new JSONObject().put(command, JSONObject.NULL).toString(), header);
//    }

    public void sendSignalTo(String subchannel, String... signals) {
        //String header = outbound_topic + subchannel;
        sendCMDto(subchannel, MQTT.signal(signals));
    }

    public void sendSubscriptionList(String agentid, JSONArray subs) {
        String header = subscription_topic + agentid;

        log.debug("sending: {} - {}", header, subs);
        gateway.sendToMqtt(subs.toString(), header);
    }


    public void initAgents() {

    }

    public void send_paged(JSONObject pages, String... recipient) {
        gateway.sendToMqtt(pages.toString(), prefix + "disp/" + recipient);
    }


    public void sendCMDto(String subchannel, JSONObject... data) {
        String header = cmd_topic + subchannel;
        String _data = MQTT.merge(data).toString();
        log.debug("sending: {} - {}", header, _data);
        gateway.sendToMqtt(_data, header);
    }
//
//    public void sendSignalTo(Agent agent, String command, String parameter) {
//        sendCommandTo(agent, MQTT.signal(command, parameter));
//    }

    public void sendCMDto(Agent agent, JSONObject... jsonObject) {
        sendCMDto(agent.getAgentid(), jsonObject);
    }

}
