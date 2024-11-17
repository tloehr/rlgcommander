package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@ToString
public class PlayedGames extends DefaultEntity {
    LocalDateTime pit;
    @ManyToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    private Users owner;
    String mode;
    String game_state;
}
