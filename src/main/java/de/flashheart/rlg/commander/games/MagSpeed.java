package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.elements.Team;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.Scheduler;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Helper game mode for Patrick's mag_fed tournament
 */
@Log4j2
public class MagSpeed extends TimedOnly {
    private final String winning_token_uid;
    private Optional<Team> winner;

    public MagSpeed(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound, MessageSource messageSource, Locale locale) throws ParserConfigurationException, IOException, SAXException, JSONException {
        super(game_parameters, scheduler, mqttOutbound, messageSource, locale);
        this.winner = Optional.empty();
        this.winning_token_uid = game_parameters.getString("winning_token_uid");
    }

    /**
     * the game ends when the timer ran out OR when somebody scans the winning rfid at the spawn agent
     *
     * @param agent
     * @param uid
     */
    @Override
    protected void on_spawn_rfid_scanned(String agent, Team team, String uid) {
        super.on_spawn_rfid_scanned(agent, team, uid);
        if (uid.equals(winning_token_uid)) {
            game_fsm.ProcessFSM(_msg_GAME_OVER);
            winner = Optional.of(team);
            add_in_game_event(new JSONObject().put("item", "capture_point").put("agent", agent).put("state", "winner"));
        } else log.warn("unknown TAG UID - ignoring");
    }

    @Override
    public String getGameMode() {
        return "mag_speed";
    }

    @Override
    public void fill_thymeleaf_model(Model model) {
        super.fill_thymeleaf_model(model);
        model.addAttribute("winner", winner);
    }
}
