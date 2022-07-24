package de.flashheart.rlg.commander.controller;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MyGateway {
    void sendToMqtt(String data, @Header(MqttHeaders.RETAINED) boolean retained, @Header(MqttHeaders.TOPIC) String topic);
}
