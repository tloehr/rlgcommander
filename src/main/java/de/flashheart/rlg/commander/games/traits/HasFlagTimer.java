package de.flashheart.rlg.commander.games.traits;

public interface HasFlagTimer {
    String _msg_FLAG_TIME_IS_UP = "flag_time_is_up";
    void flag_time_is_up(String agent_id);
}
