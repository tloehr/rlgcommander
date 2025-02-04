package de.flashheart.rlg.commander.misc;

import lombok.Getter;

/**
 * client notifications about game_state changes
 */
@Getter
public class GameStateChangedMessage extends ClientNotificationMessage{
    private final int game_id;
    private final String game_state;
    public GameStateChangedMessage(int game_id, String game_state) {
        super("game_state_change");
        this.game_id = game_id;
        this.game_state = game_state;
    }
}
