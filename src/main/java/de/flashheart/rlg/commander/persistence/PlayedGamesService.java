package de.flashheart.rlg.commander.persistence;

import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class PlayedGamesService implements DefaultService<PlayedGames> {
    private final PlayedGamesRepository repository;

    public PlayedGamesService(PlayedGamesRepository repository) {
        this.repository = repository;
    }

    @Override
    public JpaRepository<PlayedGames, Long> getRepository() {
        return repository;
    }

    @Override
    public PlayedGames createNew() {
        return new PlayedGames();
    }

    @Transactional
    public PlayedGames createNew(Users owner, JSONObject game_state) {
        PlayedGames playedGames = createNew();
        playedGames.setOwner(owner);
        playedGames.setGame_state(game_state.toString());
        playedGames.setMode(game_state.optString("game_mode", "ERROR"));
        playedGames.setPit(
                ZonedDateTime.parse(game_state.getString("start_time")).toLocalDateTime()
        );
        return playedGames;
    }

}
