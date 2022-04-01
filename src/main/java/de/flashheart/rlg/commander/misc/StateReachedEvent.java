package de.flashheart.rlg.commander.misc;

import lombok.ToString;

@ToString
public class StateReachedEvent {
    protected final String state;

    public StateReachedEvent(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }
}

