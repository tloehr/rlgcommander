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
public class SpawnParams extends PausableParams{
    String red_spawn;
    String blue_spawn;

    public SpawnParams() {
        super();
        this.red_spawn = "ag30";
        this.blue_spawn = "ag31";
    }
}
