package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.games.CenterFlags;
import de.flashheart.rlg.commander.games.params.Common;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.javatuples.Quartet;
import org.javatuples.Triplet;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Controller
@Log4j2
@RequestMapping("ui")
public class WebController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;
    BuildProperties buildProperties;

    public WebController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext, BuildProperties buildProperties) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }

    @GetMapping("/app")
    public String app(Model model) {
        model.addAttribute("server_version", String.format("v%sb%s", buildProperties.getVersion(), buildProperties.get("buildNumber")));
        return "app";
    }

    @GetMapping("/agents")
    public String showStudentList(Model model) {
        model.addAttribute("agents", agentsService.get_all_agents());
        model.addAttribute("server_version", String.format("v%sb%s", buildProperties.getVersion(), buildProperties.get("buildNumber")));
        return "agents";
    }

    @GetMapping("/setup/farcry")
    public String setup_farcry(Model model) {
        model.addAttribute("server_version", String.format("v%sb%s", buildProperties.getVersion(), buildProperties.get("buildNumber")));
        return "setup/farcry";
    }

    @PostMapping("/load/farcry")
     public String greetingSubmit(@ModelAttribute Common common, Model model) {
        model.addAttribute("server_version", String.format("v%sb%s", buildProperties.getVersion(), buildProperties.get("buildNumber")));
       return "app";
     }

//    @RequestMapping(value = "/load/farcry", method = RequestMethod.POST)
//    public String load_farcry(@RequestParam(name = "id") int id,
//                              @RequestParam(name = "message") String message,
//
//                              Model model) {
//        log.debug(message);
//
//        HttpServletRequest request = null;
//        return "redirect:" + request.getHeader("Referer");
//    }

    @GetMapping("/score")
    public String scores(@RequestParam(name = "id") int id, Model model) {
        model.addAttribute("id", id);
        try {
            CenterFlags centerFlags = (CenterFlags) gamesService.getGame(id).get();
            JSONObject game_state = centerFlags.getState();
            JSONObject firstEvent = game_state.getJSONArray("in_game_events").getJSONObject(0);
            model.addAttribute("comment", game_state.getString("comment"));
            model.addAttribute("pit", JavaTimeConverter.from_iso8601(firstEvent.getString("pit")));

            model.addAttribute("match_length", JavaTimeConverter.format(Instant.ofEpochSecond(game_state.getInt("match_length"))));
            model.addAttribute("remaining", JavaTimeConverter.format(Instant.ofEpochSecond(game_state.getInt("remaining"))));
            model.addAttribute("mode", game_state.getString("mode"));


            ArrayList<Triplet<LocalDateTime, String, String>> events = new ArrayList<>();
            game_state.getJSONArray("in_game_events").forEach(o -> {
                JSONObject event_object = (JSONObject) o;
                events.add(new Triplet<>(JavaTimeConverter.from_iso8601(event_object.get("pit").toString()),
                        centerFlags.get_in_game_event_description(new JSONObject(event_object.get("event").toString())),
                        event_object.get("new_state").toString()));
            });
            model.addAttribute("events", events.stream()
                    .sorted((o1, o2) -> o1.compareTo(o2) * -1)
                    .collect(Collectors.toList()));

            // classname, agent, blue scores, red scores
            ArrayList<Quartet<String, String, String, String>> scores = new ArrayList<>();

            centerFlags.getRoles().get("capture_points").stream().sorted().forEach(agent -> {
                String css_classname = "table-light";
                if (game_state.getJSONObject("agent_states").getString(agent).toLowerCase().matches("red|game_over_red"))
                    css_classname = "table-danger";
                if (game_state.getJSONObject("agent_states").getString(agent).toLowerCase().matches("blue|game_over_blue"))
                    css_classname = "table-primary";

                scores.add(new Quartet<>(css_classname, agent,
                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong(agent))),
                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong(agent)))
                ));

            });

            model.addAttribute("scores", scores);
            model.addAttribute("blue_total", JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong("all"))));
            model.addAttribute("red_total", JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong("all"))));

            return "score";
        } catch (IllegalStateException ise) {
            model.addAttribute("message", "This game is not loaded.");
            return "error";
        } catch (ArrayIndexOutOfBoundsException ae) {
            model.addAttribute("message", "The server does not allow for a game with the requested id.");
            return "error";
        } catch (Exception e) {
            model.addAttribute("message", e.getMessage());
            return "error";
        }

    }


    @RequestMapping(value = "/game/process", method = RequestMethod.POST)
    public String run(@RequestParam(name = "id") int id,
                      @RequestParam(name = "message") String message,
                      Model model) {
        try {
            gamesService.process_message(id, message);
            return "redirect:/ui/score?id=" + id;
        } catch (IllegalStateException ise) {
            model.addAttribute("message", "This game is not loaded.");
        } catch (ArrayIndexOutOfBoundsException ae) {
            model.addAttribute("message", "The server does not allow for a game with the requested id.");
        } catch (Exception e) {
            model.addAttribute("message", e.getMessage());
        }
        return "redirect:/ui/error";
    }

}
