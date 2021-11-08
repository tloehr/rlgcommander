package de.flashheart.rlg.commander.controller;


import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<?> load_game(@RequestBody String description) {
        return new ResponseEntity<>(gamesService.load_game(description).orElseThrow().getStatus().toString(), HttpStatus.CREATED);
    }

    @PostMapping("/game/start")
    public ResponseEntity<?> start_game() {
        return new ResponseEntity<>(gamesService.start_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @PostMapping("/game/reset")
    public ResponseEntity<?> reset_game() {
        return new ResponseEntity<>(gamesService.reset_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }


    @PostMapping("/game/unload")
    public ResponseEntity<?> stop_game() {
        gamesService.unload_game();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/game/pause")
    public ResponseEntity<?> pause_game() {
        return new ResponseEntity<>(gamesService.pause_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @PostMapping("/game/resume")
    public ResponseEntity<?> resume_game() {
        return new ResponseEntity<>(gamesService.resume_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @GetMapping("/game/status")
    public ResponseEntity<?> status_game() {
        return new ResponseEntity<>(gamesService.getGame().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @GetMapping("/system/shutdown")
    public ResponseEntity<?> system_shutdown() {
        gamesService.shutdown_agents();
        SpringApplication.exit(applicationContext, () -> 0);
        return new ResponseEntity<>(gamesService.getGame().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @GetMapping("/agents/list")
    public List<Agent> list_agents() {
        return agentsService.getLiveAgents();
    }

}
