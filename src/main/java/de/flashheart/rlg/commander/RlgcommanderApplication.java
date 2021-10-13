package de.flashheart.rlg.commander;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.mechanics.Game;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PreDestroy;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
@EnableCaching
@Log4j2
public class RlgcommanderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RlgcommanderApplication.class, args);
    }

}
