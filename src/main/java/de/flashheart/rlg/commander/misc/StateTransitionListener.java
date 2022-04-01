package de.flashheart.rlg.commander.misc;

import java.io.IOException;
import java.util.EventListener;

public interface StateTransitionListener extends EventListener {
    void onStateTransition(StateTransitionEvent event);
}