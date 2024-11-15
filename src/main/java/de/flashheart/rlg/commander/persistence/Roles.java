package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@ToString
public class Roles extends DefaultEntity {
    private String role;
    @ManyToOne
    @JoinColumn(name = "users_id", referencedColumnName = "id")
    private Users users;
}
