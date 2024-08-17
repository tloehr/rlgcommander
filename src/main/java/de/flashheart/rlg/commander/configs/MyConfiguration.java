package de.flashheart.rlg.commander.configs;

import com.google.common.collect.HashBiMap;
import de.flashheart.rlg.commander.elements.Agent;
import netscape.javascript.JSObject;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MyConfiguration {

    @Bean
    public ConcurrentHashMap<String, Agent> live_agents() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public HashBiMap<String, String> agent_replacement_map() {
        return HashBiMap.create();
    }

    @Bean
    public MultiKeyMap<String, JSONObject> recent_commands(){
        return new MultiKeyMap<>();
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
