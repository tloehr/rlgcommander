package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

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
        mqttOutbound.send("all/init", new JSONObject());
    }
}
