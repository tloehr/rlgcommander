package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashBiMap;
import de.flashheart.rlg.commander.misc.ClientNotificationMessage;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.io.IOUtils;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.javatuples.Pair;
import org.json.JSONException;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

@Configuration
@Log4j2
@NoArgsConstructor
public class MQTTOutbound {
    @Value("${mqtt.host}")
    public String mqtt_host;
    @Value("${mqtt.port}")
    public String mqtt_port;
    @Value("${mqtt.outbound.topic}")
    public String topic;
    @Value("${mqtt.outbound.notification_topic}")
    public String notification_topic;
    @Value("${mqtt.qos}")
    public int qos;
    @Value("${mqtt.client_id}")
    public String client_id;
    @Value("${mqtt.max_inflight}")
    public int max_inflight;

    ApplicationContext applicationContext;
    MyGateway gateway;
    HashBiMap<String, String> agent_replacement_map;
    MultiKeyMap<String, JSONObject> recent_commands;
    JSONObject standard_signals;

    @SneakyThrows
    @Autowired
    public MQTTOutbound(ApplicationContext applicationContext, HashBiMap<String, String> agent_replacement_map, MultiKeyMap<String, JSONObject> recent_commands) {
        this.applicationContext = applicationContext;
        this.gateway = applicationContext.getBean(MyGateway.class);
        this.agent_replacement_map = agent_replacement_map;
        this.recent_commands = recent_commands;
        standard_signals = new JSONObject(IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("standard_signals.json"), StandardCharsets.UTF_8));
    }


    @Bean
    public MqttPahoClientFactory mqttClientFactory() { // for outbound only
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{String.format("tcp://%s:%s", mqtt_host, mqtt_port)});
        options.setMaxInflight(max_inflight);
//        options.setUserName("username");
//        options.setPassword("password".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {  // for outbound only
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(String.format("%s-outbound", client_id), mqttClientFactory());
        messageHandler.setAsync(true);
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * sends a mqtt command to an agent
     *
     * @param cmd        the sub channel in the command topic
     * @param signal_key needs to be defined in <b>standard_signals.json</b>. Will be used to when sent to the agent.
     * @param agent      the recipient
     */
    public void send(String cmd, String signal_key, String agent) {
        send(cmd, standard_signals.optJSONObject(signal_key), agent);
    }

    public void send(String cmd, JSONObject payload, String agent) {
        if (agent.isEmpty()) return;
        HashSet<String> agents = new HashSet<>();
        agents.add(agent);
        send(cmd, payload, agents);
    }


    public void send(String cmd, String signal_key, Collection<String> agents) {
        send(cmd, standard_signals.optJSONObject(signal_key, new JSONObject().put("scheme", signal_key)), agents);
    }

    public void send(String cmd, JSONObject payload, Collection<String> agents) {
        if (agents.isEmpty()) return;
        agents.forEach(agent -> {
            log.trace("sending {}", topic + agent + "/" + cmd);
            send(agent, cmd, payload);
        });
    }

    public void resend_commands(String agent) {
        ArrayList<Pair<String, JSONObject>> filtered_entries_to_be_resent = new ArrayList<>();
        recent_commands.entrySet().stream()
                .filter(entry -> entry.getKey().getKey(0).equals(agent))
                .forEach(filtered_entry -> filtered_entries_to_be_resent.add(new Pair<>(filtered_entry.getKey().getKey(1), filtered_entry.getValue())));
        filtered_entries_to_be_resent.forEach(pair -> send(agent, pair.getValue0(), pair.getValue1()));
    }

    /**
     * this method sends notifications for the clients
     *
     * @param channel the game_id of the game this event belongs to. 0 is for agent events.
     * @param msg
     */
    public void notify(int channel, ClientNotificationMessage msg) {
        String my_topic = notification_topic + "/" + channel;
        log.trace("sending {}", my_topic);
        JSONObject message;
        try {
            message = new JSONObject(msg);
        } catch (JSONException e) {
            log.warn(e.getMessage());
            message = new JSONObject(e);
        }
        gateway.sendToMqtt(message.toString(), 0, false, my_topic);
    }

    /**
     * inner method where all the commands for the agents run through.
     *
     * @param agent
     * @param cmd
     * @param payload
     */
    private void send(String agent, String cmd, JSONObject payload) {
        log.trace("sending {}", topic + agent + "/" + cmd);
        boolean retained = !cmd.equals(MQTT.CMD_ACOUSTIC) && !cmd.equals(MQTT.CMD_PLAY) && !cmd.equals(MQTT.CMD_TIMERS);
        // check if this agent is substituted
        // if not it's simply the original agent
        String replaced_agent = agent_replacement_map.getOrDefault(agent, agent);
        if (!replaced_agent.equals(agent)) log.debug("OUTBOUND: agent {} replaced by {}", agent, replaced_agent);
        if (retained) { // only retained are worth remembering
            recent_commands.put(agent, cmd, payload); // remember the recent commands sent to an agent
            // always the original agent - never a replacement
            log.trace("adding cmd {} for agent {}", cmd, replaced_agent);
        }
        gateway.sendToMqtt(payload.toString(), qos, retained, this.topic + replaced_agent + "/" + cmd);
    }

}
