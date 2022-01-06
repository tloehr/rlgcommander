package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Log4j2
public class AppStartupRunner implements ApplicationRunner {

    MQTTOutbound mqttOutbound;
    BuildProperties buildProperties;

    public AppStartupRunner(MQTTOutbound mqttOutbound, BuildProperties buildProperties) {
        this.mqttOutbound = mqttOutbound;
        this.buildProperties = buildProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        mqttOutbound.sendCMDto("all", MQTT.init_agent());
        Arrays.asList(new String[]{"sirens", "leds", "capture_points", "red_spawn", "blue_spawn", "spawns"})
                .forEach(group -> mqttOutbound.sendCMDto(group, new JSONObject()));
    }
}
