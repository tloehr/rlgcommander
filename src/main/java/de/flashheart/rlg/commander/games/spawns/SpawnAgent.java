package de.flashheart.rlg.commander.games.spawns;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.games.Game;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@Log4j2
@Getter
public class SpawnAgent {
    final String agent;
    private final AbstractSpawn spawn;
    FSM fsm;
    String led_id;

    public SpawnAgent(String agent, AbstractSpawn spawn) {
        this.agent = agent;
//        try {
//            this.fsm = create_Spawn_FSM();
//        } catch (Exception e) {
//            log.error(e);
//        }
        this.spawn = spawn;
    }



    //    private FSM create_Spawn_FSM() throws ParserConfigurationException, IOException, SAXException {
//        FSM fsm = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/spawn.xml"), null);
//        fsm.setStatesAfterTransition(Game._state_PROLOG, (state, obj) -> {
//            sip.send("play", MQTT.toJSON("subpath", "intro", "soundfile", "<none>"), agent);
//            sip.send("signals", MQTT.toJSON(MQTT.LED_ALL, "off", sip.getLed_id(), "fast"), agent);
//            sip.send("paged", MQTT.merge(
//                    MQTT.page("page0",
//                            "I am ${agentname} and will", "be Your spawn.", "You are " + sip.getTeam_name(), "!! Standby !!"),
//                    MQTT.page("page1", game_description)), agent
//            );
//        });
//        fsm.setStatesAfterTransition(AbstractSpawn._state_WE_ARE_PREPARING, (state, obj) -> {
//            sip.send("signals", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,200;on,400;off,75;on,100;off,1"), agent);
//            sip.send("paged", MQTT.page("page0", " !! NOT READY !! ", "Press button", "when Your team", "is ready"), agent);
//        });
//        fsm.setStatesAfterTransition(AbstractSpawn._state_WE_ARE_READY, (state, obj) -> {
//            sip.send("signals", MQTT.toJSON(MQTT.BUZZER, "1:on,75;off,100;on,400;off,1"), agent);
//            // if all teams are ready, the GAME is ready to start
//            if (all_spawns.values().stream().allMatch(fsm1 -> fsm1.getCurrentState().equals(AbstractSpawn._state_WE_ARE_READY)))
//                game_fsm.ProcessFSM(Game._msg_READY);
//            else
//                sip.send("paged", MQTT.page("page0", " !! WE ARE READY !! ", "Waiting for others", "If NOT ready:", "Press button again"), agent);
//        });
//        fsm.setStatesAfterTransition(AbstractSpawn._state_COUNTDOWN_TO_START, (state, obj) -> {
//            sip.send("timers", MQTT.toJSON("countdown", Integer.toString(starter_countdown)), agent);
//            sip.send("paged", MQTT.page("page0", " The Game starts ", "        in", "       ${countdown}", ""), agent);
//            sip.send("play", MQTT.toJSON("subpath", "intro", "soundfile", intro_mp3_file), agent);
//        });
//        fsm.setStatesAfterTransition(AbstractSpawn._state_COUNTDOWN_TO_RESUME, (state, obj) -> {
//            sip.send("timers", MQTT.toJSON("countdown", Integer.toString(resume_countdown)), agent); // sending to everyone
//            sip.send("paged", MQTT.page("page0", " The Game resumes ", "        in", "       ${countdown}", ""), agent);
//        });
//        fsm.setStatesAfterTransition(Game._state_EPILOG, (state, obj) -> {
//            sip.send("paged", getPages(), agent);
//        });
//        fsm.setStatesAfterTransition(Game._state_PAUSING, (state, obj) -> {
//            sip.send("paged", MQTT.merge(
//                            getPages(), MQTT.page("pause", "", "      PAUSE      ", "", "")),
//                    agent);
//        });
//        // making the signal to spawn more abstract. e.g. Conquest spawns on a button, Farcry on a timer
//        fsm.setAction(AbstractSpawn._state_IN_GAME, AbstractSpawn._msg_RESPAWN_SIGNAL, new FSMAction() {
//            @Override
//            public boolean action(String curState, String message, String nextState, Object args) {
//                if (!game_fsm.getCurrentState().equals(Game._state_RUNNING)) return false;
//                respawn(role, agent);
//                return true;
//            }
//        });
//
//        return fsm;
//    }

    public void process_message(String  message) {
        fsm.ProcessFSM(message);
    }

}
