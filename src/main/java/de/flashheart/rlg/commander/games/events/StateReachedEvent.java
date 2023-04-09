package de.flashheart.rlg.commander.games.events;

import de.flashheart.rlg.commander.games.Game;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
public class StateReachedEvent {
    public static final StateReachedEvent EMPTY = new StateReachedEvent(Game._state_EMPTY);
    protected final String state;
}

