package de.flashheart.rlg.commander.games.helper;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Small Utility class to represent a team, including number of respawns.
 */
@Getter
@ToString
public class Team {
    private final String role;
    private final String name;
    private final String color;
    private final String led_device_id;
    private int respawns;

    public Team(String role, String name, String led_device_id) {
        this.role = role;
        this.name = name;
        this.color = StringUtils.substringBefore(role, "_");
        this.led_device_id = led_device_id;
        reset_respawns();
    }

    public void reset_respawns() {
        respawns = 0;
    }

    public void add_respawn(int amount) {
        respawns += amount;
    }

}
