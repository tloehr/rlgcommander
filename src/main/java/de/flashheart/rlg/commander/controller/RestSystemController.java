package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/system")
@Log4j2
public class RestSystemController extends MyParentController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public RestSystemController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(name = "game_id") int game_id) {
        LocalDateTime now = LocalDateTime.now();
        now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
        return new ResponseEntity<>(new JSONObject().put("response", game_id).toString(4), HttpStatus.OK);
    }

    @GetMapping("/max_games")
    public ResponseEntity<?> max_number_of_games() {
        return new ResponseEntity(new JSONObject().put("max_number_of_games", GamesService.MAX_NUMBER_OF_GAMES).toString(), HttpStatus.OK);
    }



}

