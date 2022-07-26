package de.flashheart.rlg.commander.games.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@AllArgsConstructor
public class RespawnEvent {
    String role;
    String agent;
}

