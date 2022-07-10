package de.flashheart.rlg.commander.controller;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /**
     * emits server sent events on game state changes
     * test with: curl -N "http://localhost:8090/api/game-sse?id=1"
     *
     * @return
     */
    @GetMapping("/game-sse")
    public SseEmitter game_state_event_emitter(@RequestParam(name = "id") int id) {
        SseEmitter emitter = new SseEmitter(-1l);
        ExecutorService sseMvcExecutor = Executors.newSingleThreadExecutor();
        sseMvcExecutor.execute(() -> {
            gamesService.addStateReachedListener(id, stateReachedEvent -> {
                try {
                    SseEmitter.SseEventBuilder event = SseEmitter.event().
                            data(stateReachedEvent.getState()). // gamesService.getGame(id).get().getState().toString()
                                    id(Long.toString(System.currentTimeMillis())).
                            name("StateReachedEvent");
                    emitter.send(event);
                } catch (Exception e) {
                    log.warn(e);
                    emitter.completeWithError(e);
                }
            });
        });
        return emitter;
    }

    @PostMapping("/game/load")
    // https://stackoverflow.com/a/57024167
    public ResponseEntity<?> load_game(@RequestParam(name = "id") int id, @RequestBody String description) throws
            ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return new ResponseEntity<>(gamesService.load_game(id, description).getState().toString(4), HttpStatus.CREATED);
    }

    // set values for a running game in pause mode
//    @PostMapping("/game/admin")
//    public ResponseEntity<?> admin_game(@RequestParam(name = "id") String id, @RequestBody String description) {
//        return new ResponseEntity<>(gamesService.admin_set_values(id, description).orElseThrow().getStatus().toString(), HttpStatus.OK);
//    }

    @PostMapping("/game/process")
    public ResponseEntity<?> process_message(@RequestParam(name = "id") int id,
                                             @RequestParam(name = "message") String message) {
        gamesService.process_message(id, message);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/game/unload")
    public ResponseEntity<?> stop_game(@RequestParam(name = "id") int id) {
        gamesService.unload_game(id);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @GetMapping("/game/status")
    public ResponseEntity<?> status_game(@RequestParam(name = "id") int id) {
        return new ResponseEntity<>(gamesService.getGameState(id).toString(4), HttpStatus.OK);
    }

    @GetMapping("/game/parameters")
    public ResponseEntity<?> game_parameters(@RequestParam(name = "id") int id) {
        try {
            return new ResponseEntity<>(gamesService.getGame(id).orElseThrow().getGame_parameters().toString(4), HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity(new JSONObject().put("error", e.getMessage()).toString(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @GetMapping("/game/list_games")
    public ResponseEntity<?> list_games() {
        return new ResponseEntity(gamesService.get_games().toString(4), HttpStatus.OK);
    }

    @GetMapping("/system/get_max_number_of_games")
    public ResponseEntity<?> max_number_of_games() {
        return new ResponseEntity(new JSONObject().put("max_number_of_games", GamesService.MAX_NUMBER_OF_GAMES).toString(), HttpStatus.OK);
    }

    @GetMapping("/system/list_agents")
    public ResponseEntity<?> list_agents() {
        return new ResponseEntity<>(agentsService.get_all_agent_states().toString(), HttpStatus.OK);
    }

    @PostMapping("/system/test_agent")
    public ResponseEntity<?> test_agent(@RequestParam(name = "agentid") String
                                                agentid, @RequestParam(name = "deviceid") String deviceid) {
        agentsService.test_agent(agentid, deviceid);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/powersave_agents")
    public ResponseEntity<?> powersave() {
        agentsService.powersave_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/system/welcome_agents")
    public ResponseEntity<?> welcome() {
        agentsService.welcome_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }
}
