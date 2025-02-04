package de.flashheart.rlg.commander.misc;

import lombok.Getter;
import org.json.JSONObject;

@Getter
public class AgentEventMessage extends ClientNotificationMessage {
    public final String agent_id;
    public final String event;
    public final String device;

    public AgentEventMessage(String agent_id, String device, String event) {
        super("agent_event");
        this.agent_id = agent_id;
        this.event = event;
        this.device = device;
    }
}
