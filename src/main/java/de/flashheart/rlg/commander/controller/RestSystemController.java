package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.configs.ApiKeyAuthentication;
import de.flashheart.rlg.commander.persistence.RolesService;
import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.persistence.UsersService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/system")
@Log4j2
public class RestSystemController extends MyParentController {
    private final UsersService usersService;

    public RestSystemController(UsersService usersService) {
        this.usersService = usersService;
    }


    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(name = "game_id") int game_id) {
        LocalDateTime now = LocalDateTime.now();
        now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
        return new ResponseEntity<>(new JSONObject().put("response", game_id).toString(4), HttpStatus.OK);
    }
    
    @PutMapping("/new_user")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> new_user(@RequestParam String username, @RequestParam String password, @RequestParam(defaultValue = "") String[] roles) {
        usersService.createNew(username, password, roles);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PatchMapping("/toggle_role")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> toggle_role(@RequestParam Long user_pk, @RequestParam String role_name, ApiKeyAuthentication authentication) throws IllegalAccessException {
        Users principal = authentication.getUser();
        if (principal.getId().equals(user_pk))
            throw new IllegalAccessException("You are not allowed to toggle your own role.");
        usersService.toggle_role(user_pk, role_name);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PatchMapping("/create_new_api_key")
    public ResponseEntity<?> create_new_api_key(@RequestParam(required = false) Long user_pk, ApiKeyAuthentication authentication) {
        Optional<Users> optionalUser = user_pk == null ?
                Optional.of(authentication.getUser()) :
                usersService.getRepository().findById(user_pk);
        if (optionalUser.isEmpty()) {
            return new ResponseEntity<>("{}", HttpStatus.NOT_FOUND);
        }
        // either I am an admin or it's my own api_key
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority(RolesService.ADMIN))
                && !optionalUser.get().equals(authentication.getUser())) {
            return new ResponseEntity<>("{}", HttpStatus.UNAUTHORIZED);
        }
        usersService.create_new_api_key(optionalUser.get());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PatchMapping("/set_user_password")
    public ResponseEntity<?> set_user_password(@RequestParam(required = false) Long user_pk, @RequestParam String password, ApiKeyAuthentication authentication) {
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

    @PatchMapping("/set_user_language")
    public ResponseEntity<?> set_user_language(@RequestParam String lang, ApiKeyAuthentication authentication) {
        if (!List.of("en", "de", "ru").contains(lang)) throw new RuntimeException("Language not supported");
        usersService.change_locale(authentication.getUser().getId(), lang);
        return new ResponseEntity<>(HttpStatus.OK);
    }


}

