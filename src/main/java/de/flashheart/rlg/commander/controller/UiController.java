package de.flashheart.rlg.commander.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.flashheart.rlg.commander.configs.MyUserDetails;
import de.flashheart.rlg.commander.elements.Agent;
import de.flashheart.rlg.commander.games.*;
import de.flashheart.rlg.commander.configs.MyYamlConfiguration;
import de.flashheart.rlg.commander.persistence.SavedGamesService;
import de.flashheart.rlg.commander.persistence.UsersService;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class UiController extends MyParentController {
    @Value("${server.locale.default}")
    public String default_locale;
    @Value("${mqtt.outbound.notification_topic}")
    public String mqtt_client_notification_topic;
    @Value("${mqtt.host}")
    public String mqtt_host;
    @Value("${mqtt.ws_port}")
    public String mqtt_ws_port;

    private final GamesService gamesService;
    private final AgentsService agentsService;
    private final BuildProperties buildProperties;
    private final MyYamlConfiguration myYamlConfiguration;
    private final SavedGamesService savedGamesService;
    private final UsersService usersService;

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

    public UiController(GamesService gamesService, AgentsService agentsService, BuildProperties buildProperties, MyYamlConfiguration myYamlConfiguration, SavedGamesService savedGamesService, UsersService usersService) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.buildProperties = buildProperties;
        this.myYamlConfiguration = myYamlConfiguration;
        this.savedGamesService = savedGamesService;
        this.usersService = usersService;
    }

    @ModelAttribute("server_version")
    public String getServerVersion() {
        return String.format("v%s b%s", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    @ModelAttribute("api_key")
    public String getApiKey(@AuthenticationPrincipal MyUserDetails user) {
        return user.getApi_key();
    }

    @ModelAttribute("mqtt")
    public String getMqtt() {
        return new JSONObject()
                .put("host", mqtt_host)
                .put("ws_port", mqtt_ws_port)
                .put("client_notification_topic", mqtt_client_notification_topic)
                .toString();
    }

    @GetMapping("/params/upload")
    public String upload(Model model) {
        model.addAttribute("params_active", "active");
        return "params/upload";
    }

    @GetMapping("/home")
    public String home(Model model, @RequestParam(name = "locale") String locale, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("home_active", "active");
        return "home_" + (locale.isEmpty() ? user.getLocale() : locale);
    }

    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }

    @GetMapping("/agents")
    public String agents(Model model) {
        model.addAttribute("agents", agentsService.get_all_agents());
        model.addAttribute("agent_replacement_map", agentsService.get_agent_replacement_map());
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

    @GetMapping("/system")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String system(Model model) {
        model.addAttribute("system_active", "active");
        model.addAttribute("users", usersService.getRepository().findAll(Sort.by(Sort.Direction.ASC, "username")));
        return "system";
    }

    @GetMapping("/server")
    public String server(@RequestParam int game_id, Model model) {
        model.addAttribute("server_status", gamesService.get_full_state(game_id).toString(4));
        model.addAttribute("server_active", "active");
        return "server";
    }

    @GetMapping("/zeus/base")
    public String zeus(@RequestParam int game_id, Model model) {
        model.addAttribute("active_active", "active");
        gamesService.getGame(game_id).ifPresent(game -> game.fill_thymeleaf_model(model));
        return model.containsAttribute("game_mode") ? "zeus/" + model.getAttribute("game_mode") : "error";
    }

    @GetMapping("/active/base")
    public String active(@RequestParam int game_id, Model model) throws JsonProcessingException {
        model.addAttribute("active_active", "active");
        String game_mode = "";
        model.addAttribute("game_id", game_id);
        if (gamesService.getGame(game_id).isEmpty()) {
            game_mode = "empty";
            model.addAttribute("classname", Game._state_EMPTY);
            model.addAttribute("has_zeus", false);
            model.addAttribute("active_command_buttons_enabled", active_command_buttons_enabled.get(Game._state_EMPTY));
            model.addAttribute("game_mode", "empty");
            model.addAttribute("game_fsm_current_state", Game._state_EMPTY);
        } else {
            Game game = gamesService.getGame(game_id).get();
            game.fill_thymeleaf_model(model);
            model.addAttribute("active_command_buttons_enabled", active_command_buttons_enabled.get(game.game_fsm_get_current_state()));
            game_mode = game.getGameMode();
        }

        return "active/" + game_mode;
    }

    @GetMapping("/params/base")
    public String params(@RequestParam(name = "game_mode") String game_mode, Model model) {
        model.addAttribute("params_active", "active");

        //model.addAttribute("game_template", "nothing_here_yet");
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
        model.addAttribute("standbys",
                myYamlConfiguration.getStandbys().entrySet().stream()
                        .map(stringStringEntry -> new Pair<>(stringStringEntry.getKey(), stringStringEntry.getValue()))
                        .sorted(Comparator.comparing(Pair::getValue1))
                        .collect(Collectors.toList())
        );
        return "params/" + game_mode;
    }

    @GetMapping("/user")
    public String user(Model model, @AuthenticationPrincipal MyUserDetails user) {
        model.addAttribute("user_active", "active");
        usersService.getRepository()
                .findByUsername(user.getUsername())
                .ifPresent(users -> model.addAttribute("user", users));
        return "user";
    }

    // https://www.baeldung.com/spring-thymeleaf-fragments
    @GetMapping("fragments/{fragment}")
    public String get_fragment(@PathVariable String fragment,
                               @RequestParam(required = false, defaultValue = "center_flags") String game_mode,
                               Model model) {
        if (fragment.equalsIgnoreCase("user_table")) {
            model.addAttribute("users", usersService.getRepository().findAll(Sort.by(Sort.Direction.ASC, "username")));
        } else if (fragment.equalsIgnoreCase("saved_games_table")) {
            model.addAttribute("saved_games", savedGamesService.list_saved_games_by_mode(game_mode));
        }
        return String.format("fragments :: %s", fragment);
    }

    @GetMapping("/agent_table_row/{agent_id}")
    public String get_single_agent_row(@PathVariable String agent_id) {
        Optional<Agent> optionalAgent = agentsService.get_all_agents()
                .stream()
                .filter(agent -> agent.getId().equals(agent_id))
                .findFirst();
        if (optionalAgent.isEmpty()) return "agents";
        return String.format("fragments :: agent_table_row(agent='%s')", new JSONObject(optionalAgent.get()));
    }
}
