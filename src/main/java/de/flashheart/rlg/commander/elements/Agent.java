package de.flashheart.rlg.commander.elements;

import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
@Getter
@Setter
public class Agent implements Comparable<Agent> {
    String id;
    JSONObject status;
    int gameid; // this agent may or may not belong to a gameid. gameid < 1 means not assigned
    String software_version;
    LocalDateTime timestamp; // last message from Agent
    String last_button;
    String last_rfid_uid;
    String ap;
    String ip;
    int signal_quality; // in percent
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
        last_button = "none";
        last_rfid_uid = "none";
        setStatus(status);
    }

    public void setStatus(JSONObject status) {
        this.status = status;
        software_version = status.optString("version", "dummy");
        reconnects = status.optInt("reconnects");
        failed_pings = status.optInt("failed_pings");
        ip = status.optString("ip", "unknown");
        signal_quality = status.optInt("signal_quality", -1);
        ap = status.optString("ap", "no_ap").toLowerCase();
        timestamp = LocalDateTime.now();
    }


    public String getBi_wifi_icon() {
        if (signal_quality >= 85)
            return "bi-reception-4";
        if (signal_quality >= 55)
            return "bi-reception-3";
        if (signal_quality >= 25)
            return "bi-reception-2";
        if (signal_quality == 0)
            return "bi-reception-0";
        if (signal_quality > 0)
            return "bi-reception-1";

        return "bi-question-circle-fill";
    }

    public String getLast_seen() {
        return Duration.between(timestamp, LocalDateTime.now()).toSeconds() + "s ago";
    }

    @Override
    public int compareTo(Agent o) {
        return id.compareTo(o.id);
    }
}
