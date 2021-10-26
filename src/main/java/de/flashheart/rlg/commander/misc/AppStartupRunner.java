package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Log4j2
public class AppStartupRunner implements ApplicationRunner {

    MQTTOutbound mqttOutbound;

    public AppStartupRunner(MQTTOutbound mqttOutbound) {
        this.mqttOutbound = mqttOutbound;
    }

    @Override
    public void run(ApplicationArguments args) {
        mqttOutbound.sendCommandTo("all", new JSONObject().put("init", ""));
        Arrays.asList(new String[]{"sirens","leds","capture_points","red_spawn","blue_spawn","green_spawn"}).forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));
    }
}