package de.flashheart.rlg.commander.games.params;

import de.flashheart.rlg.commander.games.Timed;
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
public class TimedParams extends SpawnParams {
    int game_time;
    public TimedParams(){
        super();
        game_time = 600;
    }
}
