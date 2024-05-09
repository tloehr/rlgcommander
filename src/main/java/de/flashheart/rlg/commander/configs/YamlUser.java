package de.flashheart.rlg.commander.configs;

import lombok.Getter;
import lombok.Setter;

/**
 * this class is used in combination with the user list in application.yml
 */
@Getter
@Setter
public class YamlUser {
    private String username;
    private String password;
    private String api_key;
}
