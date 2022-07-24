package de.flashheart.rlg.commander.games.traits;

public interface HasBombtimer {
    String _msg_BOMB_TIME_IS_UP = "bomb_time_is_up";
    void bomb_time_is_up(String bombid);
}
