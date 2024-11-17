package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
@Entity
@Getter
@Setter
@ToString
public class Users extends DefaultEntity {
    @Column(unique=true)
    private String username;
    private String password;
    private String apikey;
    private String locale;
    @OneToMany(fetch=FetchType.EAGER, mappedBy = "users")
    @ToString.Exclude
    private List<Roles> roles;
    @OneToMany(fetch=FetchType.LAZY, mappedBy = "owner")
    @ToString.Exclude
    private List<PlayedGames> playedGames;
    @OneToMany(fetch=FetchType.LAZY, mappedBy = "owner")
    @ToString.Exclude
    private List<SavedGames> gameTemplates;
}
