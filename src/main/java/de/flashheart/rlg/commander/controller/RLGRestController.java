package de.flashheart.rlg.commander.controller;


import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;

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

    @ExceptionHandler({JSONException.class, NoSuchElementException.class})
    public ResponseEntity<ErrorMessage> handleException(Exception exc) {
        log.warn(exc.getMessage());
        return new ResponseEntity<>(new ErrorMessage(new Throwable(exc)), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PostMapping("/game/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestParam(name = "id") String id, @RequestBody String description) {
        ResponseEntity<?> responseEntity;
        try {
            responseEntity = new ResponseEntity<>(gamesService.load_game(id, description).getStatus().toString(), HttpStatus.CREATED);
        } catch (Exception e) {
            String msg = e.toString();
            if (e instanceof InvocationTargetException) {
                msg = ((InvocationTargetException) e).getTargetException().toString();
            }
            responseEntity = new ResponseEntity<>(msg, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return responseEntity;
    }

    // set values for a running game in pause mode
    @PostMapping("/game/admin")
    public ResponseEntity<?> admin_game(@RequestParam(name = "id") String id, @RequestBody String description) {
        return new ResponseEntity<>(gamesService.admin_set_values(id, description).orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @PostMapping("/game/start")
    public ResponseEntity<?> start_game(@RequestParam(name = "id") String id) {
        try {
            gamesService.start_game(id);
            return new ResponseEntity(HttpStatus.ACCEPTED);
        } catch (IllegalStateException e) {
            return new ResponseEntity(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @PostMapping("/game/reset")
    public ResponseEntity<?> reset_game(@RequestParam(name = "id") String id) {
        gamesService.reset_game(id);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/game/unload")
    public ResponseEntity<?> stop_game(@RequestParam(name = "id") String id) {
        gamesService.unload_game(id);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/game/pause")
    public ResponseEntity<?> pause_game(@RequestParam(name = "id") String id) {
        try {
            gamesService.pause_game(id);
            return new ResponseEntity(HttpStatus.ACCEPTED);
        } catch (IllegalStateException e) {
            return new ResponseEntity(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @PostMapping("/game/resume")
    public ResponseEntity<?> resume_game(@RequestParam(name = "id") String id) {
        try {
            gamesService.resume_game(id);
            return new ResponseEntity(HttpStatus.ACCEPTED);
        } catch (IllegalStateException e) {
            return new ResponseEntity(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @GetMapping("/game/status")
    public ResponseEntity<?> status_game(@RequestParam(name = "id") String id) {
        return new ResponseEntity<>(gamesService.getGame(id).orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @GetMapping("/system/shutdown")
    public ResponseEntity<?> system_shutdown() {
        gamesService.shutdown_agents();
        //SpringApplication.exit(applicationContext, () -> 0);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/test")
    public ResponseEntity<?> test_rest(@RequestParam(name = "id") String id, @RequestBody String description) {
        log.debug("id: {}", id);
        log.debug("description: {}", description);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/list_games")
    public ResponseEntity<?> list_games() {
        return new ResponseEntity(gamesService.get_running_games().toString(), HttpStatus.OK);
    }

    @GetMapping("/system/list_agents")
    public ResponseEntity<?> list_agents() {
        return new ResponseEntity<>(agentsService.get_all_agent_states().toString(), HttpStatus.OK);
    }

}
