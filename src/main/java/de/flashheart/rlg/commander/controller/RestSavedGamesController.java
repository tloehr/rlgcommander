package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.flashheart.rlg.commander.persistence.SavedGamesService;
import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.persistence.UsersRepository;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/saved_games")
@Log4j2
public class RestSavedGamesController extends MyParentController{
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

    @GetMapping("/")
    public ResponseEntity<?> load(@RequestParam(name = "saved_game_id") long saved_game_id) throws JsonProcessingException {
        return new ResponseEntity<>(savedGamesService.load_by_id(saved_game_id), HttpStatus.OK);
    }

    @PostMapping("/")
    public ResponseEntity<?> save(@RequestParam(name = "title") String title,
                                  @RequestBody String params,
                                  Authentication authentication) {
        Optional<Users> optUsers = Optional.ofNullable(usersRepository.findByApikey(authentication.getPrincipal().toString()));
        if (optUsers.isEmpty()) {
            throw new BadCredentialsException("Invalid API Key");
        }
        savedGamesService.save(savedGamesService.createNew(title, optUsers.get(), new JSONObject(params)));
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }
}
