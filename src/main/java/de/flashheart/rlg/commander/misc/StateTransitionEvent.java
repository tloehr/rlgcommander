package de.flashheart.rlg.commander.misc;

import lombok.ToString;

@ToString
public class StateTransitionEvent {
    protected final String oldState;
    protected final String message;
    protected final String newState;

    public StateTransitionEvent(String oldState, String message, String newState) {
        this.oldState = oldState;
        this.message = message;
        this.newState = newState;
    }

    public String getOldState() {
        return oldState;
    }

    public String getMessage() {
        return message;
    }

    public String getNewState() {
        return newState;
    }
}

