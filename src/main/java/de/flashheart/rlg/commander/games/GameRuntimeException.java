package de.flashheart.rlg.commander.games;

public class GameRuntimeException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = -5116177948118950844L;

    /**
     * Constructs an {@code ArrayIndexOutOfBoundsException} with no detail
     * message.
     */
    public GameRuntimeException(String s) {
        super(s);
    }
}
