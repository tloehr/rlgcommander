package de.flashheart.rlg.commander.elements;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;

@Log4j2
@Getter
@Setter
public class Agent implements Comparable<Agent> {
    // EVENT Sources
    public static final int BTN01 = 1;
    public static final int BTN02 = 2;
    public static final int RFID = 3;

    String id;
    JSONObject status;
    int gameid; // this agent may or may not belong to a gameid. gameid < 1 means not assigned
    String software_version;
    LocalDateTime timestamp; // last message from Agent
    HashMap<Integer, EVENT> events;
    boolean rfid; // does this agent have a working rfid device ?
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
        events = new HashMap<>();
        rfid = false;
        setStatus(status);
    }

    public void event_occured(int source, String details) {
        events.put(source, new EVENT(details));
        timestamp = LocalDateTime.now();
    }

    public void setStatus(JSONObject status) {
        this.status = status;
        software_version = status.optString("version", "dummy");
        reconnects = status.optInt("reconnects");
        failed_pings = status.optInt("failed_pings");
        ip = status.optString("ip", "unknown");
        signal_quality = status.optInt("signal_quality", -1);
        rfid = status.optBoolean("has_rfid");
        // clean string from anything but letters and numbers
        ap = status.optString("ap", "000000000000").toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
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
        return getSecondsAgo(timestamp) + "s";
    }

    public long getSecondsAgo(LocalDateTime since) {
        return Duration.between(since, LocalDateTime.now()).toSeconds();
    }

    @Override
    public int compareTo(Agent o) {
        return id.compareTo(o.id);
    }

    @Getter
    @Setter
    private class EVENT {
        private String details;
        private LocalDateTime event_time;

        public EVENT(String details) {
            this.details = details;
            this.event_time = LocalDateTime.now();
        }
    }
}
