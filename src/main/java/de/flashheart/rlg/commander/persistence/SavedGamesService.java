package de.flashheart.rlg.commander.persistence;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.random.RandomGenerator;

@Service
public class SavedGamesService implements DefaultService<SavedGames> {
    private final SavedGamesRepository savedGamesRepository;
    private final BuildProperties buildProperties;
    private final Gson gson;

    public SavedGamesService(SavedGamesRepository savedGamesRepository, BuildProperties buildProperties, GsonBuilder jpaGsonBuilder) {
        this.savedGamesRepository = savedGamesRepository;
        this.buildProperties = buildProperties;
        gson = jpaGsonBuilder.create();
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
        savedGame.setParameters(game_parameters.toString());
        savedGame.setPit(LocalDateTime.now());
        return savedGame;
    }

    public List<SavedGames> list_saved_games() {
        return list_saved_games(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public List<SavedGames> list_saved_games(Optional<String> text, Optional<String> mode, Optional<String> owner) {
        return savedGamesRepository.findAll();
    }

    /**
     * loads the entity and returns a json version of it
     *
     * @param saved_game_id the PK of the requested game
     * @return a JSON representation
     * @throws NoSuchElementException if no entity was found
     */
    public String load_by_id(long saved_game_id) throws NoSuchElementException {
        Optional<SavedGames> game = savedGamesRepository.findById(saved_game_id);
        if (game.isPresent())
            return gson.toJson(game.get(), SavedGames.class);
        throw new NoSuchElementException("Game with id " + saved_game_id + " does not exist");
    }

    public String list_games() {
        return gson.toJson(savedGamesRepository.findAll(), SavedGames.class);
    }
}
