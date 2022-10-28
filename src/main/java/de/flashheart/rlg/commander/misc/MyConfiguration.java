package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.elements.Agent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {
    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }
}
