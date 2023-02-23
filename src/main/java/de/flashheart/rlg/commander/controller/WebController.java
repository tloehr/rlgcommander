package de.flashheart.rlg.commander.controller;

import de.flashheart.rlg.commander.games.CenterFlags;
import de.flashheart.rlg.commander.games.Game;
import de.flashheart.rlg.commander.misc.JavaTimeConverter;
import de.flashheart.rlg.commander.service.AgentsService;
import de.flashheart.rlg.commander.service.GamesService;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

@Controller
@RequestMapping("ui")
public class WebController {
    GamesService gamesService;
    AgentsService agentsService;
    ApplicationContext applicationContext;

    public WebController(GamesService gamesService, AgentsService agentsService, ApplicationContext applicationContext) {
        this.gamesService = gamesService;
        this.agentsService = agentsService;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }

    @GetMapping("/score")
    public String scores(@RequestParam(name = "id") int id, Model model) {
        model.addAttribute("id", id);
        try {
            Optional<Game> loaded_game = gamesService.getGame(id);
            JSONObject game_state = loaded_game.get().getState();
            JSONObject firstEvent = game_state.getJSONArray("in_game_events").getJSONObject(0);
            model.addAttribute("comment", game_state.getString("comment"));
            model.addAttribute("pit", JavaTimeConverter.from_iso8601(firstEvent.getString("pit")));
            model.addAttribute("mode", game_state.getString("mode"));
            model.addAttribute("events", game_state.getJSONArray("in_game_events").toList());

            ArrayList<Triple<String, String, String>> scores = new ArrayList<>();

            loaded_game.get().getRoles().get("capture_points").stream().sorted().forEach(agent -> {
                scores.add(new ImmutableTriple<>(agent,
                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong(agent))),
                        JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong(agent)))
                ));
            });


            model.addAttribute("scores", scores);
            model.addAttribute("blue_total",  JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("blue").getLong("all"))));
            model.addAttribute("red_total",  JavaTimeConverter.format(Instant.ofEpochMilli(game_state.getJSONObject("scores").getJSONObject("red").getLong("all"))));

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
}
