package de.flashheart.rlg.commander.websockets;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OutputMessage {

    private String from;
    private String text;
    private String time;

}
