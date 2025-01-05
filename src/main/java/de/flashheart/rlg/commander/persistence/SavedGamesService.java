package de.flashheart.rlg.commander.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

@Service
@Log4j2
public class SavedGamesService implements DefaultService<SavedGames> {
    private final SavedGamesRepository savedGamesRepository;
    private final BuildProperties buildProperties;

    public SavedGamesService(SavedGamesRepository savedGamesRepository, BuildProperties buildProperties) {
        this.savedGamesRepository = savedGamesRepository;
        this.buildProperties = buildProperties;
    }

    @Override
    public JpaRepository<SavedGames, Long> getRepository() {
        return savedGamesRepository;
    }

    @Override
    public SavedGames createNew() {
        return new SavedGames();
    }

    @Transactional
    public SavedGames createNew(String title, Users owner, JSONObject game_parameters) {
        SavedGames savedGame = createNew();
        savedGame.setMode(game_parameters.optString("game_mode", "ERROR"));
        savedGame.setText(title.trim().isEmpty() ? savedGame.getMode() + "_" + RandomGenerator.getDefault().nextInt(1, 1000) : title);
        savedGame.setOwner(owner);
        savedGame.setDefaults(false);
        savedGame.setGame(game_parameters);
        savedGame.setPit(ZonedDateTime.now());
        save(savedGame);
        return savedGame;
    }

    @Transactional
    public void toggle_default_for(long pk) {
        Optional<SavedGames> opt_new_default = savedGamesRepository.findById(pk);
        if (opt_new_default.isEmpty()) return;
        SavedGames new_default = opt_new_default.get();
        if (new_default.getDefaults())
            // simply switch off - that's it
            new_default.setDefaults(false);
        else {
            Optional<SavedGames> old_default = savedGamesRepository.findDefaultFor(new_default.getMode());
            // there can be only one default for this mode
            old_default.ifPresent(old -> {
                old.setDefaults(false);
                save(old);
            });
            new_default.setDefaults(true);
        }
        save(new_default);
    }

    public Optional<SavedGames> find_default_for(String mode) {
        Optional<SavedGames> opt_default_game = savedGamesRepository.findDefaultFor(mode);
        if (opt_default_game.isPresent()) {
            SavedGames default_game = opt_default_game.get();
            default_game.getGame().put("saved_game_pk", default_game.getId());
            return Optional.of(default_game);
        }
        return Optional.empty();
    }

    public List<SavedGames> list_saved_games_by_mode(String mode) {
        return savedGamesRepository.findByMode(mode);
    }

    public List<SavedGames> list_saved_games_by_text(String text) {
        return savedGamesRepository.findByTextLikeIgnoreCase(text);
    }

    /**
     * returns the game parameters
     *
     * @param saved_game_pk the PK of the requested game
     * @return the stored parameters as string
     * @throws JsonProcessingException shouldn't happen
     */
    public JSONObject load_game_by_id(long saved_game_pk) throws JsonProcessingException {
        Optional<SavedGames> game = savedGamesRepository.findById(saved_game_pk);
        return game.isPresent() ? game.get().getGame().put("saved_game_pk", saved_game_pk) : new JSONObject();
    }

    public String list_games() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for the jdk8 datetimes
        return objectMapper.writeValueAsString(savedGamesRepository.findAll());//gson.toJson(savedGamesRepository.findAll(), SavedGames.class);
    }

    /**
     * the primary key is stored in the game_parameters
     *
     * @param user
     * @param game_parameters
     */
    @Transactional
    public void update(Users user, JSONObject game_parameters) {
        long pk = game_parameters.optLong("saved_game_pk");
        Optional<SavedGames> opt_game = savedGamesRepository.findById(pk);
        opt_game.ifPresent(game -> game.setGame(game_parameters));
        save(opt_game.get());
    }

}
