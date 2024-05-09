package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.elements.Agent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {

    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }

    /**
     * this bean contains dynamically created rest api keys after a web-user logged in
     * @return
     */
    @Bean
    public HashSet<String> rest_api_users() {
        return new HashSet<>();
    }


}
