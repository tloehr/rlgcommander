package de.flashheart.rlg.commander.persistence;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.json.JSONObject;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@ToString
@Table(name = "played_games")
public class PlayedGames extends DefaultEntity {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    ZonedDateTime pit;
    @ManyToOne
    @ToString.Exclude
    @JsonIgnore
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    private Users owner;
    String mode;
    @Convert(converter = JpaJsonConverter.class)
    JSONObject game;
}
