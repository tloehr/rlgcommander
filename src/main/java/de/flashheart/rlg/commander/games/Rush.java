package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.MComJob;
import de.flashheart.rlg.commander.jobs.OvertimeJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobDataMap;
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
import java.util.Optional;

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
    // final JobKey mcom1, mcom2;
    private final Map<String, FSM> agentFSMs;
    private final ArrayList<FSM> sectors;
    int active_sector = -1; // which sector is currently active.
    private final int MAX_NUMBER_OF_SECTORS;

    public Rush(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);
        log.debug("    ____             __\n" +
                "   / __ \\__  _______/ /_\n" +
                "  / /_/ / / / / ___/ __ \\\n" +
                " / _, _/ /_/ (__  ) / / /\n" +
                "/_/ |_|\\__,_/____/_/ /_/\n");

        this.bomb_timer = game_parameters.getInt("bomb_timer"); // in seconds
        this.respawn_tickets = game_parameters.getInt("respawn_tickets");
        this.agentFSMs = new HashMap<>();
        this.sectors = new ArrayList<>();
        this.MAX_NUMBER_OF_SECTORS = game_parameters.getJSONArray("sectors").length();

        // read sectors information
        // prepare FSMs
        for (int iSector = 0; iSector < MAX_NUMBER_OF_SECTORS; iSector++) {
            JSONObject sector = game_parameters.getJSONArray("sectors").getJSONObject(iSector);
            agentFSMs.put(sector.getString("mcom1"), createMCOM(sector.getString("mcom1"), iSector + 1));
            agentFSMs.put(sector.getString("mcom2"), createMCOM(sector.getString("mcom2"), iSector + 1));
            sectors.add(createSector(iSector + 1, sector.getString("mcom1"), sector.getString("mcom2")));
            // create functional group "mcom"
            roles.put("mcom", sector.getString("mcom1"));
            roles.put("mcom", sector.getString("mcom2"));
            roles.put("sirens", sector.getString("siren"));
        }
    }

    /**
     * FSMs for every M-Com in the game
     *
     * @param agent
     * @return
     */
    private FSM createMCOM(final String agent, int iSector) {
        // timer job keys for both mcoms in a sector
        final JobKey mcomJob = new JobKey(String.format("mcomJob-%s", agent), uuid.toString());
        final JobDataMap jdm = new JobDataMap();
        jdm.put("agent", agent);
        jdm.put("sector", iSector);

        try {
            FSM mcom = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/mcom.xml"), null);

            mcom.setStatesAfterTransition("PROLOG", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
            });
            mcom.setStatesAfterTransition("DEFUSED", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
                deleteJob(mcomJob);
            });
            mcom.setStatesAfterTransition("FUSED", (state, o) -> {
                log.info("=====> {}:{}", agent, state);
                LocalDateTime explosion_time = LocalDateTime.now().plusSeconds(bomb_timer);
                create_job(mcomJob, explosion_time, MComJob.class, Optional.of(jdm));
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
        active_sector = -1;
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("reset"));
        sectors.forEach(fsm -> fsm.ProcessFSM("reset"));
    }

    private FSM createSector(final int iSector, final String mcom1, final String mcom2) {
        try {
            FSM sectorFSM = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/sector.xml"), null);

            sectorFSM.setStatesAfterTransition("PROLOG", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setAction("PROLOG", "start", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", iSector, curState, nextState);
                    active_sector = iSector;
                    return true;
                }
            });
            sectorFSM.setStatesAfterTransition("BOTH_DEFUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("ONE_MCOM_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("BOTH_MCOMS_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_DEFUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("BOTH_MCOMS_OVERTIME", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_OVERTIME", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
            });
            sectorFSM.setStatesAfterTransition("SECTOR_DEFENDED", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
                game_over();
            });
            sectorFSM.setStatesAfterTransition("SECTOR_TAKEN", (state, o) -> {
                log.info("=====> Sector{}:{}", iSector, state);
                active_sector = iSector + 1;
                if (active_sector > MAX_NUMBER_OF_SECTORS) game_over();
                else sectors.get(active_sector).ProcessFSM("start");
            });

            return sectorFSM;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void react_to(String sender, String item, JSONObject event) throws IllegalStateException {
        super.react_to(sender, item, event);

        if (!item.equalsIgnoreCase("btn01")) {
            log.debug("no btn01 event. discarding.");
            return;
        }
        if (!event.getString("button").equalsIgnoreCase("up")) {
            log.debug("only reacting on button UP. discarding.");
            return;
        }

        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_internal")) {
            // this happens when the bomb time is up for a specific agent
            agentFSMs.get(item).ProcessFSM(event.getString("message"));
        } else if (hasRole(sender, "mcom") && event.getString("button").equalsIgnoreCase("up")) {
            agentFSMs.get(item).ProcessFSM(event.getString("button"));
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
