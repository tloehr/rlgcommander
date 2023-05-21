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
    private final Map<String, String> wifi_icon = Map.of("no_wifi", "bi-reception-0", "bad", "bi-reception-1", "fair", "bi-reception-2", "good", "bi-reception-3", "perfect", "bi-reception-4");
    String id;
    JSONObject status;
    int gameid; // this agent may or may not belong to a gameid. gameid < 1 means not assigned
    String software_version;
    LocalDateTime timestamp; // last message from Agent
    String last_button_pressed;
    String ap;
    String ip;
    String wifi;
    int failed_pings;
    int reconnects;
    String bi_wifi_icon;

    public Agent() {
        this("dummy");
    }

    public Agent(String id) {
        this(id, new JSONObject());
    }

    public Agent(String id, JSONObject status) {
        this.id = id;
        gameid = -1;
        last_button_pressed = "none";
        setStatus(status);
    }

    public void setStatus(JSONObject status) {
        this.status = status;
        software_version = status.optString("version", "dummy");
        reconnects = status.optInt("reconnects");
        failed_pings = status.optInt("failed_pings");
        ip = status.optString("ip", "unknown");
        wifi = status.optString("wifi", "no_wifi").toLowerCase();
        bi_wifi_icon = wifi_icon.getOrDefault(wifi, "bi-question-circle-fill");
        setAp(status.optString("ap", "no_ap"));
        timestamp = LocalDateTime.now();
    }

    public void button_pressed(String button) {
        last_button_pressed = button;
        timestamp = LocalDateTime.now();
    }

    public void setAp(String ap) {
        this.ap = ap.toLowerCase();
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
