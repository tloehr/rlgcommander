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
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
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

        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm");
        String buildDate = LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()).format(FORMATTER);

        long seconds_since_first_day_2021 = ChronoUnit.MINUTES.between(LocalDateTime.of(2021,11,9,0,0,0), LocalDateTime.ofInstant(buildProperties.getTime(), ZoneId.systemDefault()));

        mqttOutbound.sendCommandTo("all",
                MQTT.page_content("page0", "RLG-Commander", "v" + buildProperties.getVersion() + " b" + seconds_since_first_day_2021,
                        "RLG-Agent", "v${agversion} b${agbuild}"));
    }
}
