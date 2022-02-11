package de.flashheart.rlg.commander.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.Optional;

@Log4j2
@Getter
@Setter
public class Agent {
    String id;
    Optional<String> gameid; // that this agent currently belongs to
    JSONObject last_state;

    public Agent() {
        this("dummy");
    }

    public Agent(String id) {
        this.id = id;
        last_state = new JSONObject();
        gameid = Optional.empty();
    }
}
