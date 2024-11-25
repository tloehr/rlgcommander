package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mchange.util.DuplicateElementException;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/agent")
@Log4j2
public class RestAgentController extends MyParentController{
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public RestAgentController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list_agents() {
        return new ResponseEntity<>(agentsService.get_all_agent_states().toString(), HttpStatus.OK);
    }

//    @PostMapping("/test")
//    public ResponseEntity<?> test(@RequestParam(name = "agent_id") String agent_id, @RequestParam(name = "command") String command, @RequestParam(name = "pattern") String pattern) {
//        agentsService.test_agent(agent_id, command, new JSONObject(pattern));
//        return new ResponseEntity(HttpStatus.ACCEPTED);
//    }

    @PostMapping("/replace")
    public ResponseEntity<?> replace(@RequestParam(name = "old") String old_agent, @RequestParam(name = "new") String new_agent) {
        agentsService.replace_agent(old_agent, new_agent);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

//    @DeleteMapping("/replacements")
//    public ResponseEntity<?> clear_replacements() {
//        agentsService.clear_replacements();
//        return new ResponseEntity(HttpStatus.ACCEPTED);
//    }

    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestParam(value = "agents") List<String> agents, @RequestBody String body) {
        agentsService.test_agents(agents, body);
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @DeleteMapping
    public ResponseEntity<?> remove_agent(@RequestParam(value = "agents") List<String> agents) {
        agents.forEach(agent -> agentsService.remove_agent(agent));
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/sleep")
    public ResponseEntity<?> sleep() {
        agentsService.powersave_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @PostMapping("/wakeup")
    public ResponseEntity<?> wakeup() {
        agentsService.welcome_unused_agents();
        return new ResponseEntity(HttpStatus.ACCEPTED);
    }





}
