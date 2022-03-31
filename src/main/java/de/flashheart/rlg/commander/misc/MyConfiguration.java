package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.entity.Agent;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {
    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentHashMap<String, String> most_recent_messages_to_agents() {
        return new ConcurrentHashMap<>();
    }
}
