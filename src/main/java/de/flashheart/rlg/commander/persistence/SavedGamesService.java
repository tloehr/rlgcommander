package de.flashheart.rlg.commander.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.random.RandomGenerator;

@Service
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
        savedGame.setParameters(game_parameters.toString());
        savedGame.setPit(ZonedDateTime.now());
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
    public String load_by_id(long saved_game_id) throws NoSuchElementException, JsonProcessingException {
        Optional<SavedGames> game = savedGamesRepository.findById(saved_game_id);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for the jdk8 datetimes
        if (game.isPresent())
            return objectMapper.writeValueAsString(game.get());
        //return gson.toJson(game.get(), SavedGames.class);
        throw new NoSuchElementException("Game with id " + saved_game_id + " does not exist");
    }

    public String list_games() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for the jdk8 datetimes
        return objectMapper.writeValueAsString(savedGamesRepository.findAll());//gson.toJson(savedGamesRepository.findAll(), SavedGames.class);
    }
}
