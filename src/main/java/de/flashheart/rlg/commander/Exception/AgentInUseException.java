package de.flashheart.rlg.commander.Exception;

import java.io.Serial;

public class AgentInUseException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 0L;

    public AgentInUseException(String message) {
        super(message);
    }
}
