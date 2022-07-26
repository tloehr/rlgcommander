package de.flashheart.rlg.commander.games.events;

import java.util.EventListener;

public interface RespawnListener extends EventListener {
    void onRespawn(RespawnEvent event);
}
