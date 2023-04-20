package de.flashheart.rlg.commander;

import de.flashheart.rlg.commander.misc.MyYamlConfiguration;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
public class RlgcommanderApplication implements ApplicationRunner {
    @Autowired
    MyYamlConfiguration myYamlConfiguration;
    public static void main(String[] args) {
        SpringApplication.run(RlgcommanderApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments arg0) throws Exception {
//        System.out.println("Hello World from Application Runner");
//        myYamlConfiguration.getIntro();

    }
}
