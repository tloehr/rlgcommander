package de.flashheart.rlg.commander.games.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
public class StateTransitionEvent {
    protected final String oldState;
    protected final String message;
    protected final String newState;
}

