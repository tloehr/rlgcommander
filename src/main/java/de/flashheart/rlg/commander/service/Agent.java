package de.flashheart.rlg.commander.service;

import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONObject;

import java.time.LocalDateTime;

@Getter
@Setter
public class Agent {

    public static final String HAS_SIRENS = "has_sirens";
    public static final String HAS_LEDS = "has_leds";
    public static final String HAS_SOUND = "has_sound";
    public static final String HAS_LINE_DISPLAY = "has_line_display";
    public static final String HAS_MATRIX_DISPLAY = "has_matrix_display";
    public static final String HAS_RFID = "has_rfid";

    private String agentid;
    private String gameid;
    private LocalDateTime lastheartbeat;
    private int wifi;
    private boolean has_siren;
    private boolean has_leds;
    private boolean has_sound;
    private boolean has_line_display;
    private boolean has_matrix_display;
    private boolean has_rfid;

    /**
     * dummy agent for testing
     *
     * @param agentid
     */
    public Agent(String agentid) {
        this.agentid = agentid;
        this.gameid = "g1";
        this.lastheartbeat = LocalDateTime.now();
        this.wifi = 3;
        this.has_siren = true;
        this.has_leds = true;
        this.has_sound = true;
        this.has_line_display = true;
        this.has_matrix_display = true;
        this.has_rfid = true;
    }

    public Agent(JSONObject jsonObject) {
        this.agentid = jsonObject.getString("agentid");
        this.gameid = jsonObject.getString("gameid");
        this.lastheartbeat = JavaTimeConverter.from_iso8601(jsonObject.getString("timestamp"));
        this.wifi = jsonObject.getInt("wifi");
        this.has_siren = jsonObject.getBoolean(HAS_SIRENS);
        this.has_leds = jsonObject.getBoolean(HAS_LEDS);
        this.has_sound = jsonObject.getBoolean(HAS_SOUND);
        this.has_line_display = jsonObject.getBoolean(HAS_LINE_DISPLAY);
        this.has_matrix_display = jsonObject.getBoolean(HAS_MATRIX_DISPLAY);
        this.has_rfid = jsonObject.getBoolean(HAS_RFID);
    }

    public JSONObject toJson() {
        return new JSONObject().put("agentid", agentid)
                .put("gameid", gameid)
                .put("timestamp", JavaTimeConverter.to_iso8601(lastheartbeat))
                .put("wifi", wifi)
                .put(HAS_SIRENS, has_siren)
                .put(HAS_LEDS, has_leds)
                .put(HAS_LINE_DISPLAY, has_line_display)
                .put(HAS_MATRIX_DISPLAY, has_matrix_display)
                .put(HAS_SOUND, has_sound)
                .put(HAS_RFID, has_rfid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Agent agent = (Agent) o;

        return new EqualsBuilder().append(getAgentid(), agent.getAgentid()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getAgentid()).toHashCode();
    }

    @Override
    public String toString() {
        return agentid;
    }
}
