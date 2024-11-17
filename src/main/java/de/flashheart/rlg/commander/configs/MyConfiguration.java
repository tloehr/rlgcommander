package de.flashheart.rlg.commander.configs;

import com.google.common.collect.HashBiMap;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.persistence.Gson_InstantTypeAdapter;
import de.flashheart.rlg.commander.persistence.LocalDateTimeConverter;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {

    @Bean
    public GsonBuilder jpa_gson_builder() {
        // didn't use it - but maybe in Future
        GsonBuilder builder = new GsonBuilder();
//        builder.registerTypeAdapter(new TypeToken<LocalDateTime>() {
//        }.getType(), new LocalDateTimeConverter());
        builder.registerTypeAdapter(Instant.class, new Gson_InstantTypeAdapter());
        builder.addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                final Expose expose = fieldAttributes.getAnnotation(Expose.class);
                return expose != null && !expose.serialize();
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        });
        builder.addDeserializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                final Expose expose = fieldAttributes.getAnnotation(Expose.class);
                return expose != null && !expose.deserialize();
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        });
        return builder;
    }

    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public HashBiMap<String, String> agent_replacement_map() {
        return HashBiMap.create();
    }

    @Bean
    public MultiKeyMap<String, JSONObject> recent_commands() {
        return new MultiKeyMap<>();
    }

    /**
     * this bean contains dynamically created rest api keys after a web-user logged in
     *
     * @return
     */
    @Bean
    public HashSet<String> rest_api_users() {
        return new HashSet<>();
    }


}
