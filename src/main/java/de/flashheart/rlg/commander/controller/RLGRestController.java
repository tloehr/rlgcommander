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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("api")
@Log4j2
public class RLGRestController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public RLGRestController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext) {
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
    @PostMapping("/game/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestParam(name = "id") int id, @RequestBody String description) {
        ResponseEntity r;
        try {
            log.debug(description);
            Game game = gamesService.load_game(id, description);
            r = new ResponseEntity<>(game.getState().toString(4), HttpStatus.ACCEPTED);
        } catch (Exception e) {
            // remember the constructor of the game class must be public
            log.warn(e);
            e.printStackTrace();
            r = new ResponseEntity<>(e, HttpStatus.NOT_ACCEPTABLE);
        }
        return r;
    }

    @PostMapping("/game/zeus")
    public ResponseEntity<?> admin_game(@RequestParam(name = "id") int id,
                                        @RequestBody String params) {
        gamesService.zeus(id, params);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/game/process")
    public ResponseEntity<?> process_message(@RequestParam(name = "id") int id,
                                             @RequestParam(name = "message") String message) {
        gamesService.process_message(id, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }


    @PostMapping("/game/unload")
    public ResponseEntity<?> stop_game(@RequestParam(name = "id") int id) {
        ResponseEntity r;
        try {
            gamesService.unload_game(id);
            r = new ResponseEntity(HttpStatus.ACCEPTED);
        } catch (Exception e) {
            // remember the constructor of the game class must be public
            log.warn(e);
            e.printStackTrace();
            r = new ResponseEntity<>(e, HttpStatus.NOT_ACCEPTABLE);
        }
        return r;
    }

    @GetMapping("/game/status")
    public ResponseEntity<?> status_game(@RequestParam(name = "id") int id) {
        return new ResponseEntity<>(gamesService.getGameState(id).toString(4), HttpStatus.OK);
    }

    @GetMapping("/game/current_state")
    public ResponseEntity<?> current_state_game(@RequestParam(name = "id") int id) {
        return new ResponseEntity<>(gamesService.getGameState(id).optString("game_state", "EMPTY"), HttpStatus.OK);
    }

    @GetMapping("/game/parameters")
    public ResponseEntity<?> game_parameters(@RequestParam(name = "id") int id) {
        try {
            return new ResponseEntity<>(gamesService.getGame(id).orElseThrow().getGame_parameters().toString(4), HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity(new JSONObject().put("error", e.getMessage()).toString(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @GetMapping("/game/list_games")
    public ResponseEntity<?> list_games() {
        return new ResponseEntity(gamesService.get_games().toString(4), HttpStatus.OK);
    }

    @GetMapping("/system/get_max_number_of_games")
    public ResponseEntity<?> max_number_of_games() {
        return new ResponseEntity(new JSONObject().put("max_number_of_games", GamesService.MAX_NUMBER_OF_GAMES).toString(), HttpStatus.OK);
    }

    @GetMapping("/system/list_agents")
    public ResponseEntity<?> list_agents() {
        return new ResponseEntity<>(agentsService.get_all_agent_states().toString(), HttpStatus.OK);
    }

    @PostMapping("/system/test_agent")
    public ResponseEntity<?> test_agent2(@RequestParam(name = "agent_id") String agent_id, @RequestParam(name = "command") String command, @RequestParam(name = "pattern") String pattern) {
        agentsService.test_agent(agent_id, command, new JSONObject(pattern));
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/add_replacement")
    public ResponseEntity<?> add_replacement(@RequestParam(name = "old") String old_agent, @RequestParam(name = "new") String new_agent) {
        agentsService.replace_agent(old_agent, new_agent);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @DeleteMapping("/system/clear_replacements")
    public ResponseEntity<?> clear_replacements() {
        agentsService.clear_replacements();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/test_agents")
    public ResponseEntity<?> test_agents(@RequestParam(value = "agents") List<String> agents, @RequestBody String body) {
        agentsService.test_agents(agents, body);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @DeleteMapping("/system/remove_agents")
    public ResponseEntity<?> remove_agent(@RequestParam(value = "agents") List<String> agents) {
        agents.forEach(agent -> agentsService.remove_agent(agent));
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/powersave_agents")
    public ResponseEntity<?> powersave() {
        agentsService.powersave_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/welcome_agents")
    public ResponseEntity<?> welcome() {
        agentsService.welcome_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }
}
