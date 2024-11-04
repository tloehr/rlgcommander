package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/game")
@Log4j2
public class RestGameController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public RestGameController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
    }

    @ExceptionHandler({JSONException.class,
            NoSuchElementException.class,
            ArrayIndexOutOfBoundsException.class,
            ClassNotFoundException.class,
            NoSuchMethodException.class,
            InvocationTargetException.class,
            InstantiationException.class,
            IllegalAccessException.class,
            IllegalStateException.class})
    public ResponseEntity<ErrorMessage> handleException(Exception exc) {
        log.warn(exc.getMessage());
        return new ResponseEntity(exc, HttpStatus.NOT_ACCEPTABLE);
    }

    /**
     * emits server sent events on game state changes test with: curl -N "http://localhost:8090/api/game-sse?id=1"
     *
     * @return
     */
    @PostMapping("/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestParam(name = "game_id") int game_id, @RequestBody String description) {
        ResponseEntity r;
        try {
            log.debug(description);
            Game game = gamesService.load_game(game_id, description);
            r = new ResponseEntity<>(game.getState().toString(4), HttpStatus.ACCEPTED);
        } catch (Exception e) {
            // remember the constructor of the game class must be public
            log.warn(e);
            e.printStackTrace();
            r = new ResponseEntity<>(e, HttpStatus.NOT_ACCEPTABLE);
        }
        return r;
    }

    @PostMapping("/zeus")
    public ResponseEntity<?> zeus(@RequestParam(name = "game_id") int game_id,
                                  @RequestBody String params) {
        gamesService.zeus(game_id, params);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestParam(name = "game_id") int game_id,
                                     @RequestParam(name = "message") String message) {
        gamesService.process_message(game_id, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }


    @PostMapping("/unload")
    public ResponseEntity<?> unload(@RequestParam(name = "game_id") int game_id) {
        ResponseEntity r;
        try {
            gamesService.unload_game(game_id);
            r = new ResponseEntity(HttpStatus.ACCEPTED);
        } catch (Exception e) {
            // remember the constructor of the game class must be public
            log.warn(e);
            e.printStackTrace();
            r = new ResponseEntity<>(e, HttpStatus.NOT_ACCEPTABLE);
        }
        return r;
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
            return new ResponseEntity(new JSONObject().put("error", e.getMessage()).toString(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return new ResponseEntity(gamesService.get_games().toString(4), HttpStatus.OK);
    }

}
