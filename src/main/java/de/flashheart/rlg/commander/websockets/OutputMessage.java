package de.flashheart.rlg.commander.websockets;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OutputMessage {
    private String game_state;
    private String time;

}
