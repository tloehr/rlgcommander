package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.configs.ApiKeyAuthentication;
import de.flashheart.rlg.commander.games.GameSetupException;
import de.flashheart.rlg.commander.persistence.SavedGamesService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/game")
@Log4j2
public class RestGameController extends MyParentController {
    private final GamesService gamesService;
    private final SavedGamesService savedGamesService;


    public RestGameController(GamesService gamesService, SavedGamesService savedGamesService) {
        this.gamesService = gamesService;
        this.savedGamesService = savedGamesService;
    }

    @PutMapping("/")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestParam int game_id,
                                       @RequestParam(required = false, defaultValue = "de") String locale,
                                       @RequestBody String description,
                                       ApiKeyAuthentication authentication) throws GameSetupException, IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        
        gamesService.load_game(game_id, description, authentication.getUser(), Locale.forLanguageTag(locale));

        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestParam int game_id,
                                  ApiKeyAuthentication authentication) {
        JSONObject game_state = gamesService.getGameState(game_id);
        if (!game_state.has("setup"))
            throw new IllegalArgumentException(String.format("Game %s does not exist", game_id));
        String title = game_state.getJSONObject("setup").getString("comment") + " @ " + LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM));
        savedGamesService.createNew(title, authentication.getUser(), gamesService.getGameState(game_id).getJSONObject("setup"));
        return new ResponseEntity<>(new JSONObject().put("name", title).toString(), HttpStatus.CREATED);
    }

    @PostMapping("/zeus")
    public ResponseEntity<?> zeus(@RequestParam(name = "game_id") int game_id,
                                  @RequestBody String params) {
        gamesService.zeus(game_id, params);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestParam(name = "game_id") int game_id,
                                     @RequestParam(name = "message") String message) {
        gamesService.process_message(game_id, message);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }


    @DeleteMapping("/")
    public ResponseEntity<?> unload(@RequestParam(name = "game_id") int game_id) throws IOException {
        gamesService.unload_game(game_id);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status_game(@RequestParam(name = "game_id") int game_id) {
        return new ResponseEntity<>(gamesService.getGameState(game_id).toString(4), HttpStatus.OK);
    }

    @GetMapping("/current_state")
    public ResponseEntity<?> current_state_game(@RequestParam(name = "game_id") int game_id) {
        return new ResponseEntity<>(gamesService.getGameState(game_id).optString("game_state", "EMPTY"), HttpStatus.OK);
    }

    @GetMapping("/parameters")
    public ResponseEntity<?> parameters(@RequestParam(name = "game_id") int game_id) {
        try {
            return new ResponseEntity<>(gamesService.getGame(game_id).orElseThrow().getGame_parameters().toString(4), HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(new JSONObject().put("error", e.getMessage()).toString(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return new ResponseEntity(gamesService.get_games().toString(4), HttpStatus.OK);
    }

}
