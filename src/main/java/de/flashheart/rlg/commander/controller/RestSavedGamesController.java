package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.flashheart.rlg.commander.configs.ApiKeyAuthentication;
import de.flashheart.rlg.commander.persistence.*;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/saved_games")
@Log4j2
public class RestSavedGamesController extends MyParentController {
    private final SavedGamesService savedGamesService;
    private final UsersRepository usersRepository;
    public RestSavedGamesController(SavedGamesService savedGamesService, UsersRepository usersRepository) {
        this.savedGamesService = savedGamesService;
        this.usersRepository = usersRepository;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(name = "text", required = false) Optional<String> text,
            @RequestParam(name = "mode", required = false) Optional<String> mode,
            @RequestParam(name = "owner", required = false) Optional<String> owner
    ) throws JsonProcessingException {
        return new ResponseEntity<>(savedGamesService.list_games(), HttpStatus.OK);
    }

    @GetMapping("/default")
    public ResponseEntity<?> load_default(@RequestParam String mode) {
        Optional<SavedGames> default_game = savedGamesService.find_default_for(mode);
        return default_game.map(savedGames ->
                        new ResponseEntity<>(savedGames.getGame().toString(), HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>("{}", HttpStatus.OK));
    }

    @PatchMapping("/default")
    public ResponseEntity<?> toggle_default(@RequestParam long saved_game_pk) {
        savedGamesService.toggle_default_for(saved_game_pk);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/")
    public ResponseEntity<?> load_single(@RequestParam long saved_game_pk) throws JsonProcessingException {
        return new ResponseEntity<>(savedGamesService.load_game_by_id(saved_game_pk).toString(), HttpStatus.OK);
    }

    @PutMapping("/")
    public ResponseEntity<?> save(@RequestParam String title,
                                  @RequestBody String params,
                                  ApiKeyAuthentication authentication) {
        savedGamesService.save(savedGamesService.createNew(title, authentication.getUser(), new JSONObject(params)));
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/")
    public ResponseEntity<?> delete(@RequestParam Long saved_game_pk, ApiKeyAuthentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority(RolesService.ADMIN))) {
            return new ResponseEntity<>("{}", HttpStatus.UNAUTHORIZED);
        }
        Optional<SavedGames> optionalSavedGame = savedGamesService.getRepository().findById(saved_game_pk);
        if (optionalSavedGame.isEmpty()) {
            return new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        if (!optionalSavedGame.get().getOwner().equals(authentication.getUser())) {
            return new ResponseEntity<>("{}", HttpStatus.UNAUTHORIZED);
        }
        savedGamesService.delete(optionalSavedGame.get());
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
