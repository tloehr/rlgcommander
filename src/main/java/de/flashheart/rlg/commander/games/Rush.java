package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.action.FSMAction;
import com.github.ankzz.dynamicfsm.fsm.FSM;
import de.flashheart.rlg.commander.controller.MQTT;
import de.flashheart.rlg.commander.controller.MQTTOutbound;
import de.flashheart.rlg.commander.jobs.MComJob;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
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
    private int remaining_tickets_for_this_zone;

    public Rush(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) throws ParserConfigurationException, IOException, SAXException {
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

        //dynmamically determine the number of zones by the game_paramters
        this.MAX_NUMBER_OF_SECTORS = game_parameters.getJSONArray("sectors").length();
        /*
            we read the sectors information from the game_parameters and prepare the FSMs.
            rush uses an own structure under the key "sectors". In addidtion to the generic
            information listed under "agents", which are parsed in the GAME superclass.
         */
        for (int sector_number = 0; sector_number < MAX_NUMBER_OF_SECTORS; sector_number++) {
            JSONObject sector = game_parameters.getJSONArray("sectors").getJSONObject(sector_number);
            agentFSMs.put(sector.getString("mcom1"), createMCOM(sector.getString("mcom1"), sector_number));
            agentFSMs.put(sector.getString("mcom2"), createMCOM(sector.getString("mcom2"), sector_number));
            sectors.add(createSector(sector_number, sector.getString("mcom1"), sector.getString("mcom2")));
            // create functional group "mcom"
            roles.put("mcom", sector.getString("mcom1"));
            roles.put("mcom", sector.getString("mcom2"));
            roles.put("sirens", sector.getString("siren"));
        }
    }

    @Override
    protected void at_state(String state) {

    }

    @Override
    protected void on_transition(String old_state, String message, String new_state) {

    }

    /**
     * FSMs for every M-Com in the game
     *
     * @param agent
     * @return
     */
    private FSM createMCOM(final String agent, int sector_number) {
        // timer job keys for both mcoms in a sector
        final JobKey mcomJob = new JobKey(String.format("mcomJob-%s", agent), uuid.toString());
        final JobDataMap jdm = new JobDataMap();
        jdm.put("agent", agent);
        jdm.put("sector", sector_number);

        try {
            FSM mcom = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/rush/mcom.xml"), null);

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


    public void on_reset() {

        active_sector = -1;
        agentFSMs.values().forEach(fsm -> fsm.ProcessFSM("reset"));
        sectors.forEach(fsm -> fsm.ProcessFSM("reset"));
    }

    /**
     * @param sector_number
     * @param mcom1         contains the agent for mcom1
     * @param mcom2         contains the agent for mcom2
     * @return
     */
    private FSM createSector(final int sector_number, final String mcom1, final String mcom2) {
        try {
            FSM sectorFSM = new FSM(this.getClass().getClassLoader().getResourceAsStream("games/rush/sector.xml"), null);

            sectorFSM.setStatesAfterTransition("PROLOG", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
                remaining_tickets_for_this_zone = respawn_tickets;
            });
            sectorFSM.setAction("PROLOG", "start", new FSMAction() {
                @Override
                public boolean action(String curState, String message, String nextState, Object args) {
                    log.info("{}:{} =====> {}", sector_number, curState, nextState);
                    active_sector = sector_number;
                    return true;
                }
            });
            sectorFSM.setStatesAfterTransition("BOTH_DEFUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("ONE_MCOM_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("BOTH_MCOMS_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_FUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_DEFUSED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("BOTH_MCOMS_OVERTIME", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("LAST_MCOM_OVERTIME", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
            });
            sectorFSM.setStatesAfterTransition("SECTOR_DEFENDED", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
                process_message(_msg_GAME_OVER);
            });
            sectorFSM.setStatesAfterTransition("SECTOR_TAKEN", (state, o) -> {
                log.info("=====> Sector{}:{}", sector_number, state);
                active_sector = sector_number + 1;
                if (active_sector > MAX_NUMBER_OF_SECTORS) process_message(_msg_GAME_OVER);
                else sectors.get(active_sector).ProcessFSM("start");
            });

            return sectorFSM;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.error(ex);
            return null;
        }
    }

    @Override
    public void process_message(String sender, String item, JSONObject message) {

        // internal message OR message I am interested in
        if (sender.equalsIgnoreCase("_bombtimer_")) {
            // for BOMB_TIME_UP messages - we notify the agent (m-com) here
            // item contains the agent name
            agentFSMs.get(item).ProcessFSM(message.getString("message"));
        } else if (hasRole(sender, "mcom") && message.getString("button").equalsIgnoreCase("up")) {
            agentFSMs.get(sender).ProcessFSM(message.getString("button"));
        } else if (hasRole(sender, "attacker_spawn") && message.getString("button").equalsIgnoreCase("up")) {
            // red respawn button was pressed
            mqttOutbound.send("signals", MQTT.toJSON("buzzer", "single_buzz", "led_wht", "single_buzz"), sender);
            remaining_tickets_for_this_zone--;
            broadcast_score();
        } else {
            log.debug("message is not for me. ignoring.");
        }
    }

    private void broadcast_score() {
//        mqttOutbound.send("vars",
//                MQTT.toJSON("tickets", Integer.toString(remaining_tickets_for_this_zone.intValue()),
//                        "blue_tickets", Integer.toString(remaining_blue_tickets.intValue()),
//                        "red_flags", Integer.toString(cps_held_by_red.size()),
//                        "blue_flags", Integer.toString(cps_held_by_blue.size())),
//                roles.get("spawns"));
//
//        log.debug("Cp: R{} B{}", cps_held_by_red.size(), cps_held_by_blue.size());
//        log.debug("Tk: R{} B{}", remaining_red_tickets.intValue(), remaining_blue_tickets.intValue());
//
//        log.debug(" Red: {}", cps_held_by_red);
//        log.debug("Blue: {}", cps_held_by_blue);
    }

    @Override
    public void on_cleanup() {
        super.on_cleanup();
        agentFSMs.clear();
    }


    @Override
    public JSONObject getState() {
        final JSONObject statusObject = super.getState()
                .put("mode", "rush")
                .put("bomb_time", bomb_timer)
                .put("respawn_tickets", respawn_tickets);


        return statusObject;
    }

    @Override
    protected JSONObject getPages() {
        return MQTT.page("page0", game_description);
    }
}
