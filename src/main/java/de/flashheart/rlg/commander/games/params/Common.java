package de.flashheart.rlg.commander.games.params;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Getter
@Setter
@Log4j2
@AllArgsConstructor
public class Common {
    int id;
    String comment;
    int starter_countdown;
}
