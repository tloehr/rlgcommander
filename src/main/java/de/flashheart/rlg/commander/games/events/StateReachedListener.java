package de.flashheart.rlg.commander.games.events;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.EventListener;

public interface StateReachedListener extends EventListener {
    void onStateReached(StateReachedEvent event);
}
