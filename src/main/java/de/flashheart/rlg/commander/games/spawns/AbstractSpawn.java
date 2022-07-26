package de.flashheart.rlg.commander.games.spawns;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONObject;

import java.util.ArrayList;

@AllArgsConstructor
@Getter
public abstract class AbstractSpawn implements SpawnDataProvider {
    public static final String _state_WE_ARE_PREPARING = "WE_ARE_PREPARING";
    public static final String _state_WE_ARE_READY = "WE_ARE_READY";
    public static final String _state_IN_GAME = "IN_GAME";
    public static final String _state_COUNTDOWN_TO_START = "COUNTDOWN_TO_START";
    public static final String _state_COUNTDOWN_TO_RESUME = "COUNTDOWN_TO_RESUME";
    public static final String _msg_START_COUNTDOWN = "start_countdown";
    public static final String _msg_RESPAWN_SIGNAL = "respawn_signal";

    String spawn_role;
    String led_id;
    String team_name;


}
