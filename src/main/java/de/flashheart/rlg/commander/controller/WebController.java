package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.games.*;
import de.flashheart.rlg.commander.misc.MyYamlConfiguration;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.javatuples.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/upload")
    public String upload(Model model) {
        model.addAttribute("params_active", "active");
        return "upload";
    }

    @GetMapping("/home")
    public String app(Model model) {
        model.addAttribute("home_active", "active");
        return "home";
    }

    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }

    @GetMapping("/agents")
    public String agents(Model model) {
        model.addAttribute("agents", agentsService.get_all_agents());
        model.addAttribute("agents_active", "active");
        return "agents";
    }

    @GetMapping("/server")
    public String server(@RequestParam(name = "id") int id, Model model) {
        model.addAttribute("server_status", gamesService.getGameState(id).toString(4));
        model.addAttribute("server_active", "active");
        return "server";
    }

    @GetMapping("/zeus/base")
    public String zeus(@RequestParam(name = "id") int id, Model model) {
        model.addAttribute("active_active", "active");
        gamesService.getGame(id).ifPresent(game -> game.add_model_data(model));
        return model.containsAttribute("game_mode") ? "zeus/" + model.getAttribute("game_mode") : "error";
    }

    @GetMapping("/active/base")
    public String active(@RequestParam(name = "id") int id, Model model) {
        model.addAttribute("active_active", "active");
        String game_mode = "";

        if (gamesService.getGame(id).isEmpty()) {
            game_mode = "empty";
            model.addAttribute("classname", Game._state_EMPTY);
            model.addAttribute("has_zeus", false);
            model.addAttribute("active_command_buttons_enabled", new JSONArray(active_command_buttons_enabled.get(Game._state_EMPTY)));
            model.addAttribute("game_mode", "empty");
        } else {
            //log.debug("add_model_data from webcontroller");
            Game game = gamesService.getGame(id).get();
            game.add_model_data(model);
            model.addAttribute("active_command_buttons_enabled", new JSONArray(active_command_buttons_enabled.get(game.get_current_state())));
            game_mode = game.getGameMode();
        }

        return "active/" + game_mode;
    }

    @GetMapping("/params/base")
    public String params(@RequestParam(name = "game_mode") String game_mode, Model model) {
        model.addAttribute("params_active", "active");
        model.addAttribute("intros",
                myYamlConfiguration.getIntro().entrySet().stream()
                        .map(stringStringEntry -> new Pair<>(stringStringEntry.getKey(), stringStringEntry.getValue()))
                        .sorted(Comparator.comparing(Pair::getValue1))
                        .collect(Collectors.toList())
        );
        return "params/" + game_mode;
    }

}
