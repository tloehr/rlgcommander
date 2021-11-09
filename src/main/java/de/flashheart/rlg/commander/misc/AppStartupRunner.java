package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
        mqttOutbound.sendCommandTo("all", MQTT.init_agent());
        Arrays.asList(new String[]{"sirens", "leds", "capture_points", "red_spawn", "blue_spawn", "green_spawn", "spawns"})
                .forEach(group -> mqttOutbound.sendCommandTo(group, new JSONObject()));

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault());


        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

        String formatted = LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()).format(FORMATTER);

        mqttOutbound.sendCommandTo("all",
                //todo: another page vor vanity
                MQTT.page_content("page0", "RLG2 Cmd", "V" + buildProperties.getVersion(),
                        "Build:" + formatted, "${agversion}"));
        // Artifact's name from the pom.xml file
        log.debug(buildProperties.getName());
        // Artifact version
        log.debug(buildProperties.getVersion());
        // Date and Time of the build
        log.debug(buildProperties.getTime());
        // Artifact ID from the pom file
        log.debug(buildProperties.getArtifact());
        // Group ID from the pom file
        log.debug(buildProperties.getGroup());
    }
}
