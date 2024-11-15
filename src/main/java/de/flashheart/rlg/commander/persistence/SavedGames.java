package de.flashheart.rlg.commander.persistence;

import com.google.gson.annotations.Expose;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.json.JSONObject;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@ToString
public class SavedGames extends DefaultEntity {
    LocalDateTime pit;
    String text;
    String mode;
    String cmd_version;
    //@Convert(converter = JpaJsonConverter.class)
    String parameters;
    @ManyToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    @ToString.Exclude
    @Expose(serialize = false, deserialize = false)
    private Users owner;
}
