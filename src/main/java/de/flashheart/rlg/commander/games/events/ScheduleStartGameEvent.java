package de.flashheart.rlg.commander.games.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@ToString
@Getter
@AllArgsConstructor
public class ScheduleStartGameEvent {
    LocalDateTime start_game_at;
}
