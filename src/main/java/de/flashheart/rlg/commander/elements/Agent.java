package de.flashheart.rlg.commander.elements;

import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDateTime;

@Log4j2
@Getter
public class Agent implements Comparable<Agent> {
    String id;

    JSONObject status;

    int gameid; // this agent may or may not belong to a gameid. gameid < 1 means not assigned
    String software_version;
    LocalDateTime timestamp;
    String ap;
    String wifi;
    int failed_pings;
    int reconnects;

    public Agent() {
        this("dummy");
    }

    public Agent(String id) {
        this(id, new JSONObject());
    }

    public Agent(String id, JSONObject status) {
        this.id = id;
        gameid = -1;
        setStatus(status);
    }

    public void setStatus(JSONObject status) {
        this.status = status;
        software_version = status.optString("version", "dummy");
        reconnects = status.optInt("reconnects");
        failed_pings = status.optInt("failed_pings");
        wifi = status.optString("wifi", "no_wifi");
        ap = status.optString("ap", "no_ap");
        timestamp = LocalDateTime.now();
    }

    public void setGameid(int gameid) {
        this.gameid = gameid;
    }


    public String getLast_seen() {
        return Duration.between(timestamp, LocalDateTime.now()).toSeconds() + "s ago";
    }


    @Override
    public int compareTo(Agent o) {
        return id.compareTo(o.id);
    }
}
