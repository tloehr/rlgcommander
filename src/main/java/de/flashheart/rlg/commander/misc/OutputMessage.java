package de.flashheart.rlg.commander.misc;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OutputMessage {
    private int game_id;
    private String game_state;
    private String time;

}
