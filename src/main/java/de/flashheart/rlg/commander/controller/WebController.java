package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.games.*;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.misc.MyYamlConfiguration;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Triplet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Log4j2
@RequestMapping("ui")
public class WebController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;
    BuildProperties buildProperties;
    MyYamlConfiguration myYamlConfiguration;

    private static final Map<String, String> game_page_map = Map.of(
            CenterFlags.class.getName(), "active/center_flags.html",
            Conquest.class.getName(), "active/error",
            Farcry.class.getName(), "active/notyet",
            Signal.class.getName(), "active/notyet",
            Stronghold.class.getName(), "active/notyet",
            TimedOnly.class.getName(), "active/notyet",
            Game._state_EMPTY, "active/empty"
    );

    private static final Map<String, String> gamemode_page_map = Map.of(
            "center_flags", "params/center_flags.html",
            "conquest", "params/notyet",
            "farcry", "params/notyet",
            "signal", "params/notyet",
            "stronghold", "params/notyet",
            "timed_only", "params/notyet"
    );

    private static final Map<String, String> gamemode_names = Map.of(
            "center_flags", "Center Flags",
            "conquest", "error",
            Farcry.class.getName(), "error",
            Signal.class.getName(), "error",
            Stronghold.class.getName(), "error",
            TimedOnly.class.getName(), "error"
    );

    private static final Map<String, int[]> active_command_buttons_enabled =
            Map.of(
                    Game._state_PROLOG, new int[]{1, 1, 1, 1, 0, 0, 0, 0},
                    Game._state_TEAMS_NOT_READY, new int[]{0, 1, 1, 1, 0, 0, 0, 0},
                    Game._state_TEAMS_READY, new int[]{0, 1, 0, 1, 0, 0, 0, 0},
                    Game._state_RUNNING, new int[]{0, 1, 0, 0, 1, 0, 0, 1},
                    Game._state_PAUSING, new int[]{0, 1, 0, 0, 0, 1, 0, 0},
                    Game._state_RESUMING, new int[]{0, 1, 0, 0, 0, 0, 1, 0},
                    Game._state_EPILOG, new int[]{0, 1, 0, 0, 0, 0, 0, 0},
                    Game._state_EMPTY, new int[]{0, 0, 0, 0, 0, 0, 0, 0}
            );


    public WebController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext, BuildProperties buildProperties, MyYamlConfiguration myYamlConfiguration) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
        this.myYamlConfiguration = myYamlConfiguration;
    }

    @ModelAttribute("server_version")
    public String getServerVersion() {
        return String.format("v%s b%s", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }

    @GetMapping("/home")
    public String app(Model model) {
        return "home";
    }

    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }

    @GetMapping("/agents")
    public String showStudentList(Model model) {
        model.addAttribute("agents", agentsService.get_all_agents().stream().sorted().toList());
        return "agents";
    }

    @GetMapping("/active/base")
    public String active(@RequestParam(name = "id") int id, Model model) {
        JSONObject game_state = gamesService.getGameState(id);
        String classname = game_state.optString("class", Game._state_EMPTY);
        model.addAttribute("classname", classname);
        model.addAttribute("has_zeus", false);
        model.addAttribute("active_command_buttons_enabled", new JSONArray(active_command_buttons_enabled.get(Game._state_EMPTY)));
        gamesService.getGame(id).ifPresent(game -> {
            model.addAttribute("gameid", id);
            game.add_model_data(model);
            model.addAttribute("active_command_buttons_enabled", new JSONArray(active_command_buttons_enabled.get(game.get_current_state())));
        });

        return game_page_map.getOrDefault(classname, "error");
    }

    @GetMapping("/params/base")
    public String params(@RequestParam(name = "id") int id, @RequestParam(name = "gamemode") String gamemode, Model model) {
        model.addAttribute("gameid", id);
        model.addAttribute("intros",
                myYamlConfiguration.getIntro().entrySet().stream()
                        .map(stringStringEntry -> new Pair<>(stringStringEntry.getKey(), stringStringEntry.getValue()))
                        .sorted(Comparator.comparing(Pair::getValue1))
                        .collect(Collectors.toList())
        );
        return gamemode_page_map.getOrDefault(gamemode, "error");
    }
//
//    @GetMapping("/score")
//    public String scores(@RequestParam(name = "id") int id, Model model) {
//        model.addAttribute("id", id);
//        try {
//            CenterFlags centerFlags = (CenterFlags) gamesService.getGame(id).get();
//            JSONObject game_state = centerFlags.getState();
//            JSONObject firstEvent = game_state.getJSONArray("in_game_events").getJSONObject(0);
//            model.addAttribute("comment", game_state.getString("comment"));
//            model.addAttribute("pit", JavaTimeConverter.from_iso8601(firstEvent.getString("pit")));
//
//            model.addAttribute("match_length", JavaTimeConverter.format(Instant.ofEpochSecond(game_state.getInt("match_length"))));
//            model.addAttribute("remaining", JavaTimeConverter.format(Instant.ofEpochSecond(game_state.getInt("remaining"))));
//            model.addAttribute("mode", game_state.getString("mode"));
//
//
//            ArrayList<Triplet<LocalDateTime, String, String>> events = new ArrayList<>();
//            game_state.getJSONArray("in_game_events").forEach(o -> {
//                JSONObject event_object = (JSONObject) o;
//                events.add(new Triplet<>(JavaTimeConverter.from_iso8601(event_object.get("pit").toString()),
//                        centerFlags.get_in_game_event_description(new JSONObject(event_object.get("event").toString())),
//                        event_object.get("new_state").toString()));
//            });
//            model.addAttribute("events", events.stream()
//                    .sorted((o1, o2) -> o1.compareTo(o2) * -1)
//                    .collect(Collectors.toList()));
//
//            // classname, agent, blue scores, red scores
//            ArrayList<Quartet<String, String, String, String>> scores = new ArrayList<>();
//
//            centerFlags.getRoles().get("capture_points").stream().sorted().forEach(agent -> {
//                String css_classname = "table-light";
//                if (game_state.getJSONObject("agent_states").getString(agent).toLowerCase().matches("red|game_over_red"))
//                    css_classname = "table-danger";
//                if (game_state.getJSONObject("agent_states").getString(agent).toLowerCase().matches("blue|game_over_blue"))
//                    css_classname = "table-primary";
//
//                scores.add(new Quartet<>(css_classname, agent,
//                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong(agent))),
//                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong(agent)))
//                ));
//
//            });
//
//            model.addAttribute("scores", scores);
//            model.addAttribute("blue_total", JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong("all"))));
//            model.addAttribute("red_total", JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong("all"))));
//
//            return "score";
//        } catch (IllegalStateException ise) {
//            model.addAttribute("message", "This game is not loaded.");
//            return "error";
//        } catch (ArrayIndexOutOfBoundsException ae) {
//            model.addAttribute("message", "The server does not allow for a game with the requested id.");
//            return "error";
//        } catch (Exception e) {
//            model.addAttribute("message", e.getMessage());
//            return "error";
//        }
//
//    }

//
//    @GetMapping(value = "/process")
//    public String run(@RequestParam(name = "id") int id,
//                      @RequestParam(name = "event") String event,
//                      HttpServletRequest request,
//                      Model model) {
//        try {
//            model.addAttribute("id", id);
//            log.debug(request.getHeader("Referer"));
//            log.debug("{} {}", id, event);
//            gamesService.process_message(id, event);
//            //todo: error message in running game
//            return "redirect:" + request.getHeader("Referer");
//        } catch (Exception e) {
//            model.addAttribute("error_message", e.getMessage());
//        }
//        return "error";
//    }

}
