package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import org.json.JSONObject;
import org.quartz.Scheduler;

/**
 * see https://battlefield.fandom.com/wiki/Tickets#Rush
 * <li>attackers have a limited amount of respawn tickets</li>
 * <li>defenders have an un-limited amount of respawn tickets</li>
 * <li>when BOTH M-COMs or the last remaining one have been armed, attackers can still spawn even when their ticket level has reached ZERO</li>
 */
public class Rush extends ScheduledGame {
    public Rush(String id, JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(id, game_parameters, scheduler, mqttOutbound);
    }

}
