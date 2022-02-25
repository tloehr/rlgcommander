package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.OvertimeJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * see https://battlefield.fandom.com/wiki/Tickets#Rush
 * <li>attackers have a limited amount of respawn tickets</li>
 * <li>defenders have an un-limited amount of respawn tickets</li>
 * <li>when BOTH M-COMs or the last remaining one have been armed, attackers can still spawn even when their ticket
 * level has reached ZERO</li>
 */
@Log4j2
public class Rush extends Scheduled {
    private final int bomb_timer;
    private final int respawn_tickets;
    final JobKey mcom1, mcom2;
    private final Map<String, FSM> agentFSMs;
    private final ArrayList<FSM> sectors;
    String active_sector = ""; // which sector is currently active.

    public Rush(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);
        log.debug("    ____             __\n" +
                "   / __ \\__  _______/ /_\n" +
                "  / /_/ / / / / ___/ __ \\\n" +
                " / _, _/ /_/ (__  ) / / /\n" +
                "/_/ |_|\\__,_/____/_/ /_/\n");

        this.bomb_timer = game_parameters.getInt("bomb_timer"); // in seconds
        this.respawn_tickets = game_parameters.getInt("respawn_tickets");

        agentFSMs = new HashMap<>();
        sectors = new ArrayList<>();

        // timer job keys for both mcoms in a sector
        mcom1 = new JobKey("mcom1", uuid.toString());
        mcom2 = new JobKey("mcom2", uuid.toString());

        // prepare FSMs
        for (String sector : new String[]{"sector1", "sector2", "sector3"}) {
            if (roles.containsKey(sector)) {
                String agent1 = roles.get(sector).toArray()[0].toString();
                String agent2 = roles.get(sector).toArray()[1].toString();
                agentFSMs.put(agent1, createMCOM(agent1));
                agentFSMs.put(agent2, createMCOM(agent2));
                sectors.add(createSector(sector, agent1, agent2));
            }
        }

    }

    /**
     * FSMs for every M-Com in the game
     *
     * @param agent
     * @return
     */
    private FSM createMCOM(final String agent) {
        try {
            FSM mcom = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/mcom.xml"), null);

            mcom.setStatesAfterTransition("PROLOG", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
            });
            mcom.setStatesAfterTransition("DEFUSED", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
            });
            mcom.setStatesAfterTransition("FUSED", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
                LocalDateTime explosion_time = LocalDateTime.now().plusSeconds(bomb_timer);
                create_job(overtimeJobKey, regular_end_time, OvertimeJob.class);
            });
            mcom.setStatesAfterTransition("EXPLODED", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
            });
            mcom.setStatesAfterTransition("EPILOG", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
            });

            return mcom;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void reset() {
        super.reset();
        active_sector = "";
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("reset"));
        sectors.forEach(fsm -> fsm.ProcessFSM("reset"));
    }

    private FSM createSector(final String sector, final String agent1, final String agent2) {
        try {
            FSM mcom = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/sector.xml"), null);

            mcom.setStatesAfterTransition("PROLOG", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setAction("PROLOG", "start", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", sector, curState, nextState);
                    active_sector = sector;
                    return true;
                }
            });
            mcom.setStatesAfterTransition("BOTH_DEFUSED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("ONE_MCOM_FUSED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("BOTH_MCOMS_FUSED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("LAST_MCOM_FUSED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("LAST_MCOM_DEFUSED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("BOTH_MCOMS_OVERTIME", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("LAST_MCOM_OVERTIME", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("SECTOR_DEFENDED", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });
            mcom.setStatesAfterTransition("SECTOR_TAKEN", (state, o) -> {
                log.info("=====> {}:{}", sector, state);
            });

            return mcom;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void react_to(String sender, String source, JSONObject event) throws IllegalStateException {
        super.react_to(sender, source, event);

        if (!source.equalsIgnoreCase("btn01")) {
            log.debug("no btn01 event. discarding.");
            return;
        }
        if (!event.getString("button").equalsIgnoreCase("up")) {
            log.debug("only reacting on button UP. discarding.");
            return;
        }

        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_internal")) {
            farcryFSM.ProcessFSM(event.getString("message"));
        } else if (hasRole(sender, "button") && event.getString("button").equalsIgnoreCase("up")) {
            farcryFSM.ProcessFSM(event.getString("button_pressed").toUpperCase());
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }


    @Override
    public void cleanup() {
        super.cleanup();
        agentFSMs.clear();
    }

    @Override
    public JSONObject getStatus() {
        final JSONObject statusObject = super.getStatus()
                .put("mode", "rush")
                .put("bomb_time", bomb_timer)
                .put("respawn_tickets", respawn_tickets);


        return statusObject;
    }

}
