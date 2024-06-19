package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.configs.MyUserDetails;
import de.flashheart.rlg.commander.games.*;
import de.flashheart.rlg.commander.configs.MyYamlConfiguration;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Log4j2
@RequestMapping("ui")
public class UiController {
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


    public UiController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext, BuildProperties buildProperties, MyYamlConfiguration myYamlConfiguration) {
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
    public String upload(Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("params_active", "active");
        return "upload";
    }

    @GetMapping("/home")
    public String home(Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("home_active", "active");
        return "home";
    }

    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }

    @GetMapping("/agents")
    public String agents(Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("agents", agentsService.get_all_agents());
        // model.addAttribute("agents_active", "active");
        // adding test list for the agents web interface
        ArrayList<Triplet<String, String, JSONObject>> list_of_tests = new ArrayList<>();
        try {
            JSONArray tests = new JSONObject(IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("agent_test_commands.json"), StandardCharsets.UTF_8)).getJSONArray("tests");
            for (int test_i = 0; test_i < tests.length(); test_i++) {
                JSONObject test = tests.getJSONObject(test_i);
                list_of_tests.add(new Triplet<>(test.getString("name"), test.getString("command"), test.getJSONObject("pattern")));
            }
        } catch (Exception ignored) {
            log.error(ignored);
        } finally {
            model.addAttribute("test_list", list_of_tests);
        }
        return "agents";
    }

    @GetMapping("/server")
    public String server(@RequestParam(name = "id") int id, Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("server_status", gamesService.getGameState(id).toString(4));
        model.addAttribute("server_active", "active");
        return "server";
    }

    @GetMapping("/zeus/base")
    public String zeus(@RequestParam(name = "id") int id, Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("active_active", "active");
        gamesService.getGame(id).ifPresent(game -> game.add_model_data(model));
        return model.containsAttribute("game_mode") ? "zeus/" + model.getAttribute("game_mode") : "error";
    }

    @GetMapping("/active/base")
    public String active(@RequestParam(name = "id") int id, Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
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
    public String params(@RequestParam(name = "game_mode") String game_mode, Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("api_key", user.getApi_key());
        model.addAttribute("params_active", "active");
        model.addAttribute("intros",
                myYamlConfiguration.getIntros().entrySet().stream()
                        .map(stringStringEntry -> new Pair<>(stringStringEntry.getKey(), stringStringEntry.getValue()))
                        .sorted(Comparator.comparing(Pair::getValue1))
                        .collect(Collectors.toList())
        );
        model.addAttribute("voices",
                myYamlConfiguration.getVoices().entrySet().stream()
                        .map(stringStringEntry -> new Pair<>(stringStringEntry.getKey(), stringStringEntry.getValue()))
                        .sorted(Comparator.comparing(Pair::getValue1))
                        .collect(Collectors.toList())
        );
        return "params/" + game_mode;
    }
}
