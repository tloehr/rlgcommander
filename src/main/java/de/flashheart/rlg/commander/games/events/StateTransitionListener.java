package de.flashheart.rlg.commander.games.events;

import java.util.EventListener;

public interface StateTransitionListener extends EventListener {
    void onStateTransition(StateTransitionEvent event);
}
