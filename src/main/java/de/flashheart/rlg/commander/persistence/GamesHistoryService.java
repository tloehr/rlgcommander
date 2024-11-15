package de.flashheart.rlg.commander.persistence;

import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class GamesHistoryService implements DefaultService<GamesHistory> {
    private final GamesHistoryRepository repository;
    private final BuildProperties buildProperties;

    public GamesHistoryService(GamesHistoryRepository repository, BuildProperties buildProperties) {
        this.repository = repository;
        this.buildProperties = buildProperties;
    }

    @Override
    public JpaRepository<GamesHistory, Long> getRepository() {
        return repository;
    }

    @Override
    public GamesHistory createNew() {
        return new GamesHistory();
    }

    @Transactional
    public GamesHistory createNew(Users owner, JSONObject game_state) {
        GamesHistory gameHistory = createNew();
        gameHistory.setOwner(owner);
        gameHistory.setGame_state(game_state.toString());
        gameHistory.setMode(game_state.optString("game_mode", "ERROR"));
        gameHistory.setCmd_version(buildProperties.getVersion());
        gameHistory.setPit(
                ZonedDateTime.parse(game_state.getString("start_time")).toLocalDateTime()
        );
        return gameHistory;
    }

}
