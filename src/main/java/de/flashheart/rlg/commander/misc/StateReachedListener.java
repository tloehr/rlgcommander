package de.flashheart.rlg.commander.misc;

import java.util.EventListener;

public interface StateReachedListener extends EventListener {
    void onStateReached(StateReachedEvent event);
}