package de.flashheart.rlg.commander;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

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
