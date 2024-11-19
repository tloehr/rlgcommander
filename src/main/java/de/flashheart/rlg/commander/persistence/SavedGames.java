package de.flashheart.rlg.commander.persistence;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@ToString
public class SavedGames extends DefaultEntity {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    ZonedDateTime pit;
    String text;
    String mode;
    String parameters;
    @ManyToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    @ToString.Exclude
    @JsonIgnore
    private Users owner;
}
