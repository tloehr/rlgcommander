package de.flashheart.rlg.commander.controller;


import de.flashheart.rlg.commander.service.Agent;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @PostMapping("/game/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestBody String description) {
        try {
            return new ResponseEntity<>(gamesService.load_game(description).orElseThrow().getStatus().toString(), HttpStatus.CREATED);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @PostMapping("/game/unload")
    public ResponseEntity<?> unload_game() {
        try {
            gamesService.unload_game();
            return new ResponseEntity<>(new JSONObject().put("message", "game unloaded").toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exc.getMessage(), exc);
        }
    }

    @PostMapping("/game/start")
    public ResponseEntity<?> start_game() {
        try {
            return new ResponseEntity<>(gamesService.start_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @PostMapping("/game/stop")
    public ResponseEntity<?> stop_game() {
        try {
            return new ResponseEntity<>(gamesService.stop_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @PostMapping("/game/pause")
    public ResponseEntity<?> pause_game() {
        try {
            return new ResponseEntity<>(gamesService.pause_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @PostMapping("/game/resume")
    public ResponseEntity<?> resume_game() {
        try {
            return new ResponseEntity<>(gamesService.resume_game().orElseThrow().getStatus().toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @GetMapping("/game/status")
    public ResponseEntity<?> status_game() {
        try {
            return new ResponseEntity<>(gamesService.getGame().orElseThrow().getStatus().toString(), HttpStatus.OK);
        } catch (JSONException | NoSuchElementException exc) {
            return new ResponseEntity<>(new JSONObject().put("error", "no loaded game found").put("reason", exc.getMessage()).toString(), HttpStatus.OK);
        }
    }

    @GetMapping("/agents/list")
    public List<Agent> list_agents() {
        return agentsService.getLiveAgents();
    }

}
