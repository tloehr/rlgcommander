package de.flashheart.rlg.commander.games.params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Setter
@Log4j2
@AllArgsConstructor
@ToString
public class GameParams {
    String comment;
    int starter_countdown;
    String intro_mp3;
    boolean game_lobby;
    boolean silent_game;

    public GameParams() {
        comment = "no Comment";
        starter_countdown = 30;
        intro_mp3 = "bf3";
        game_lobby = true;
        silent_game = false;
    }
}
