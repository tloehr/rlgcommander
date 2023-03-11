package de.flashheart.rlg.commander.games.params;

import de.flashheart.rlg.commander.games.Pausable;
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
public class PausableParams extends GameParams{
    int resume_countdown;

    public PausableParams(){
        super();
        resume_countdown = 0;
    }
}
