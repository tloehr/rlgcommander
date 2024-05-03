package de.flashheart.rlg.commander.configs;

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

//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurerAdapter() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**")
//                        .allowedMethods("HEAD", "GET", "PUT", "POST", "DELETE", "PATCH");
//            }
//        };
//    }

}
