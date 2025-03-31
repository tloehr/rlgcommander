package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <h1>Stronghold 2</h1>
 * This is a variation of the original stronghold game mode. Here we have a time limit for every
 * ring.
 *
 * <h2>game_time</h2>
 * <li>we have a time limit per ring which will add up to the total game_time</li>
 * <li>if the last wall falls early - game over</li>
 * <li>if the time runs out on a wall - game is over</li>
 * <li>if a wall is taken, the remaining time is added to the time limit for that wall - so a quick team will have a benefit</li>
 */
@Log4j2
public class Stronghold2 extends Stronghold {
    ArrayList<Long> ring_times;
    private final DecimalFormat df = new DecimalFormat("#.00");
    private final DecimalFormat df2 = new DecimalFormat("#.##");

    private record ring_percent_values(double num_of_agents, double percent, double percent_per_agent,
                                       long ring_time_limit) {
    }

    final ArrayList<ring_percent_values> ring_values;
    final double total_sum_all_agents;

    public Stronghold2(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        // game_time always equals the time limit of the first ring.
        // the game_time extends as the rings are broken
        // the game_parameters have to be set correctly by the frontend
        this.ring_values = new ArrayList<>();
        this.ring_times = new ArrayList<>();
        this.total_sum_all_agents = map_agent_to_ring_color.size();

        List.of("blu", "grn", "ylw", "red").forEach(color -> {
            if (rings_total.contains(color)) {
                long ring_time_limit = game_parameters.getJSONObject("ring_time_limits").getLong(color);
                ring_times.add(ring_time_limit);

                double num_of_agents = roles.get(color).size();
                double percent = num_of_agents / total_sum_all_agents * 100;
                double percent_per_agent = percent / num_of_agents;
                ring_values.add(new ring_percent_values(num_of_agents, percent, percent_per_agent, ring_time_limit));
            } else {
                ring_values.add(new ring_percent_values(0d, 0d, 0d, 0L));
            }
        });

        setGameDescription(game_parameters.getString("comment"),
                String.format("Number of rings: %s", rings_total.size()),
                String.format("Ring-Times: %s", ring_times.toString()),
                " ".repeat(18) + "${wifi_signal}"
        );
    }

    @Override
    protected void activate_ring() {
        super.activate_ring();
        if (rings_to_go.size() < rings_total.size() && rings_total.size() > 1) {  // not with the first or only ring
            extend_game_time(ring_times.get(rings_taken.size()));
        }
    }

    @Override
    protected JSONObject getSpawnPages(String state) {
        JSONObject pages = super.getSpawnPages(state);
        if (state.matches(_state_PAUSING + "|" + _state_RUNNING)) {
            pages = MQTT.merge(pages, MQTT.page("page0",
                    "Time:  ${remaining}  ${wifi_signal}",
                    "${rings_taken}/${rings_in_use} -> ${active_ring}",
                    "${extended_time_label}",
                    "${extended_time}"));
        }
        return pages;
    }


    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);

        // defaults
        model.addAttribute("text_wall_red", "");
        model.addAttribute("text_wall_yellow", "");
        model.addAttribute("text_wall_green", "");
        model.addAttribute("text_wall_blue", "");
        model.addAttribute("percent_wall_red", 0d);
        model.addAttribute("percent_wall_yellow", 0d);
        model.addAttribute("percent_wall_green", 0d);
        model.addAttribute("percent_wall_blue", 0d);

        rings_to_go.forEach(color -> {
            int index = rings_total.indexOf(color);
            double minutes = (double) ring_times.get(index) / 60d;
            model.addAttribute("time_limit_" + color, df.format(minutes) + "m");
            if (is_in_a_running_state()) {
                String active_color = rings_to_go.getFirst();
                double percent = color.equals(active_color) ?
                        ((double) stable_agents().size()) * ring_values.get(index).percent_per_agent :
                        ring_values.get(index).percent;

                model.addAttribute("text_wall_" + color,
                        color.equals(active_color) ?
                                String.join(",", stable_agents()) :
                                "+" + df2.format(minutes) + "m"
                );
                model.addAttribute("percent_wall_" + color, percent);
            } else {
                model.addAttribute("text_wall_" + color,
                        String.join(",", roles.get(color)) + " +"
                                + df2.format(minutes) + "m"
                );
                model.addAttribute("percent_wall_" + color, ring_values.get(index).percent);
            }
        });
        // model.addAttribute("all_rings", rings_total);
    }

    @Override
    public String getGameMode() {
        return "stronghold2";
    }


    @Override
    public boolean hasZeus() {
        return false;
    }

}
