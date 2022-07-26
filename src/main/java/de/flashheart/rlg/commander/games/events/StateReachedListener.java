package de.flashheart.rlg.commander.games.events;

import java.util.EventListener;

public interface StateReachedListener extends EventListener {
    void onStateReached(StateReachedEvent event);
}
