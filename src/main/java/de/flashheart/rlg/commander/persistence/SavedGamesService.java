package de.flashheart.rlg.commander.persistence;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import jakarta.transaction.Transactional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

@Service
public class SavedGamesService implements DefaultService<SavedGames> {
    private final SavedGamesRepository savedGamesRepository;
    private final BuildProperties buildProperties;
    private final Gson gson;

    public SavedGamesService(SavedGamesRepository savedGamesRepository, BuildProperties buildProperties) {
        this.savedGamesRepository = savedGamesRepository;
        this.buildProperties = buildProperties;
        // didn't use it - but maybe in Future
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(new TypeToken<LocalDateTime>() {
        }.getType(), new LocalDateTimeConverter());
        builder.addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                final Expose expose = fieldAttributes.getAnnotation(Expose.class);
                return expose != null && !expose.serialize();
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        });
        builder.addDeserializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                final Expose expose = fieldAttributes.getAnnotation(Expose.class);
                return expose != null && !expose.deserialize();
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        });
        gson = builder.create();
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
        SavedGames gameTemplate = createNew();
        gameTemplate.setMode(game_parameters.optString("game_mode", "ERROR"));
        gameTemplate.setText(title.trim().isEmpty() ? gameTemplate.getMode() + "_" + RandomGenerator.getDefault().nextInt(1, 1000) : title);
        gameTemplate.setOwner(owner);
        gameTemplate.setParameters(game_parameters.toString());
        gameTemplate.setCmd_version(buildProperties.getVersion());
        gameTemplate.setPit(LocalDateTime.now());
        return gameTemplate;
    }

    public List<SavedGames> list_saved_games() {
        return list_saved_games(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public List<SavedGames> list_saved_games(Optional<String> text, Optional<String> mode, Optional<String> owner) {
        return savedGamesRepository.findAll();
    }

//    public JSONArray list_saved_games() {
//        return list_saved_games(Optional.empty(), Optional.empty(), Optional.empty());
//    }
//
//    public JSONArray list_saved_games(Optional<String> text, Optional<String> mode, Optional<String> owner) {
//        return new JSONArray(
//                savedGamesRepository.findAll()
//                        .stream()
//                        .map(gson::toJson)
//                        .collect(Collectors.toList())
//        );
//    }
}
