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
public class FarcryParams extends TimedParams{
    int bomb_time;

    public FarcryParams() {
        super();
        bomb_time = 60;
    }
}
