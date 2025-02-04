package de.flashheart.rlg.commander.misc;

import lombok.Getter;

@Getter
public class AgentStateChangeMessage extends ClientNotificationMessage {
    private final String agent_id;
    private final String agent_state;

    public AgentStateChangeMessage(String agent_id, String agent_state) {
        super("agent_state_change");
        this.agent_id = agent_id;
        this.agent_state = agent_state;
    }
}
