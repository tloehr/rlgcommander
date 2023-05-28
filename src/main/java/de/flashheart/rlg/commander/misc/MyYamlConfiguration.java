package de.flashheart.rlg.commander.misc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "rlgs")
@Getter
@Setter
public class MyYamlConfiguration {
    private Map<String, String> intro;
    private Map<String, String> access_points;
    private Map<String, String> score_broadcast;
}
