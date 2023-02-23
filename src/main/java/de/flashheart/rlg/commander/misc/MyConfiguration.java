package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.elements.Agent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import org.thymeleaf.spring5.ISpringTemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {
    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }

//    @Bean
//    public ISpringTemplateEngine templateEngine(ITemplateResolver templateResolver) {
//        SpringTemplateEngine engine = new SpringTemplateEngine();
//        engine.addDialect(new Java8TimeDialect());
//        engine.setTemplateResolver(templateResolver);
//        return engine;
//    }
}
