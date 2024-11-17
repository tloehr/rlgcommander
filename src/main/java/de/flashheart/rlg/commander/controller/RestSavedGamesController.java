package de.flashheart.rlg.commander.controller;

import com.google.gson.GsonBuilder;
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
public class RestSavedGamesController {
    private final SavedGamesService savedGamesService;
    private final UsersRepository usersRepository;
    private final GsonBuilder jpa_gson_builder;

    public RestSavedGamesController(SavedGamesService savedGamesService, UsersRepository usersRepository, GsonBuilder jpa_gson_builder) {
        this.savedGamesService = savedGamesService;
        this.usersRepository = usersRepository;
        this.jpa_gson_builder = jpa_gson_builder;
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

    @GetMapping("/list")
    public ResponseEntity<?> list_saved_games(
            @RequestParam("text") Optional<String> text,
            @RequestParam("mode") Optional<String> mode,
            @RequestParam("owner") Optional<String> owner) {
        return new ResponseEntity<>(savedGamesService.list_games(), HttpStatus.OK);
    }

    @GetMapping("/load")
    public ResponseEntity<?> load(@RequestParam(name = "saved_game_id") long saved_game_id) {
        return new ResponseEntity<>(savedGamesService.load_by_id(saved_game_id), HttpStatus.OK);
    }

    @PostMapping("/save")
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
