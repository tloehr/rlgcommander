package de.flashheart.rlg.commander.games;

import de.flashheart.rlg.commander.controller.MQTTOutbound;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.quartz.Scheduler;

/**
 * see https://battlefield.fandom.com/wiki/Tickets#Rush
 * <li>attackers have a limited amount of respawn tickets</li>
 * <li>defenders have an un-limited amount of respawn tickets</li>
 * <li>when BOTH M-COMs or the last remaining one have been armed, attackers can still spawn even when their ticket level has reached ZERO</li>
 */
@Log4j2
public class Rush extends Scheduled {
    public Rush(JSONObject game_parameters, Scheduler scheduler, MQTTOutbound mqttOutbound) {
        super(game_parameters, scheduler, mqttOutbound);
        log.debug("    ____             __\n" +
                "   / __ \\__  _______/ /_\n" +
                "  / /_/ / / / / ___/ __ \\\n" +
                " / _, _/ /_/ (__  ) / / /\n" +
                "/_/ |_|\\__,_/____/_/ /_/\n");
    }

}
