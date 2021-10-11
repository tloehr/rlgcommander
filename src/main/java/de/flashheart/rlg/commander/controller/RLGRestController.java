package de.flashheart.rlg.commander.controller;


import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
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

    public RLGRestController(GamesService gamesService, AgentsService agentsService) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
    }

    @ExceptionHandler({JSONException.class, NoSuchElementException.class})
    public ResponseEntity<ErrorMessage> handleException(Exception exc) {
        log.warn(exc.getMessage());
        ErrorMessage message = new ErrorMessage(new Throwable(exc.getMessage())
                //new Throwable(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString())
        );

        return new ResponseEntity<>(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PostMapping("/game/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestBody String description) {
//        try {
//            return new ResponseEntity<>(gamesService.load_game(description).orElseThrow().getStatus().toString(), HttpStatus.CREATED);
//        } catch (JSONException | NoSuchElementException exc) {
//            throw new ResponseStatusException(HttpStatus.OK, exc.getMessage(), exc);
//        }
        return new ResponseEntity<>(gamesService.load_game(description).orElseThrow().getStatus().toString(), HttpStatus.CREATED);
    }

    @PostMapping("/game/unload")
    public ResponseEntity<?> unload_game() {
        gamesService.unload_game();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/game/start")
    public ResponseEntity<?> start_game() {
        return new ResponseEntity<>(gamesService.start_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
    }

    @PostMapping("/game/stop")
    public ResponseEntity<?> stop_game() {
        return new ResponseEntity<>(gamesService.stop_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
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

    @GetMapping("/agents/list")
    public List<Agent> list_agents() {
        return agentsService.getLiveAgents();
    }

}
