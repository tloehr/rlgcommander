package de.flashheart.rlg.commander.games.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
public class StateReachedEvent {
    protected final String state;
}

