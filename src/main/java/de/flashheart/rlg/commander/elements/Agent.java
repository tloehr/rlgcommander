package de.flashheart.rlg.commander.elements;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

@Log4j2
@Getter
@Setter
public class Agent {
    String id;
    int gameid; // this agent may or may not belong to a gameid. gameid < 1 means not assigned
    JSONObject last_state;

    public Agent() {
        this("dummy");
    }

    public Agent(String id) {
        this.id = id;
        last_state = new JSONObject();
        gameid = -1;
    }
}
