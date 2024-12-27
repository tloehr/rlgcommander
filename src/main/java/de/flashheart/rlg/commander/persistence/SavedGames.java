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
@Table(name = "saved_games")
public class SavedGames extends DefaultEntity {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    ZonedDateTime pit;
    String text;
    String mode;
    @Convert(converter = JpaJsonConverter.class)
    JSONObject game;
    Boolean defaults;
    @ManyToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    @ToString.Exclude
    @JsonIgnore
    private Users owner;
}
