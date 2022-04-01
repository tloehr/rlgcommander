package de.flashheart.rlg.commander.misc;

import java.util.EventListener;

public interface GameStateListener extends EventListener {
    void onStateChange(GameStateEvent event);
}
