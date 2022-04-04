package de.flashheart.rlg.commander.games;

import com.github.ankzz.dynamicfsm.fsm.FSM;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface to mark Gamemodes that tell players when to respawn.
 */
public interface HasRespawn {
    void respawn();
}
