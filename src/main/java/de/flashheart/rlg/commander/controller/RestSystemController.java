package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.flashheart.rlg.commander.configs.ApiKeyAuthentication;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.persistence.RolesService;
import de.flashheart.rlg.commander.persistence.SavedGames;
import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.persistence.UsersService;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/system")
@Log4j2
public class RestSystemController extends MyParentController {
    private final UsersService usersService;
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public RestSystemController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext, UsersService usersService) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
        this.usersService = usersService;
    }


    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(name = "game_id") int game_id) {
        LocalDateTime now = LocalDateTime.now();
        now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
        return new ResponseEntity<>(new JSONObject().put("response", game_id).toString(4), HttpStatus.OK);
    }

    @GetMapping("/max_games")
    public ResponseEntity<?> max_number_of_games() {
        return new ResponseEntity<>(new JSONObject().put("max_number_of_games", GamesService.MAX_NUMBER_OF_GAMES).toString(), HttpStatus.OK);
    }

    @PutMapping("/new_user")
    public ResponseEntity<?> new_user(@RequestParam String username, @RequestParam String password, @RequestParam(defaultValue = "") String[] roles, ApiKeyAuthentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority(RolesService.ADMIN))) {
            return new ResponseEntity<>("{}", HttpStatus.UNAUTHORIZED);
        }
        usersService.save(usersService.createNew(username, password, roles));
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PatchMapping("/set_password")
    public ResponseEntity<?> set_password(@RequestParam(required = false) Long user_pk, @RequestParam String password, ApiKeyAuthentication authentication) {
        Optional<Users> optionalUser = user_pk == null ?
                Optional.of(authentication.getUser()) :
                usersService.getRepository().findById(user_pk);
        if (optionalUser.isEmpty()) {
            return new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        // either I am an admin or it's my own password
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority(RolesService.ADMIN))
                && !optionalUser.get().equals(authentication.getUser())) {
            return new ResponseEntity<>("{}", HttpStatus.UNAUTHORIZED);
        }
        usersService.set_password(optionalUser.get(), password);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}

