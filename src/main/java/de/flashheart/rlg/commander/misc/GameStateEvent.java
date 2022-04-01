package de.flashheart.rlg.commander.misc;

public class GameStateEvent {
    protected final String state;
    protected final int gameid;

    public GameStateEvent(String state, int gameid) {
        this.state = state;
        this.gameid = gameid;
    }

    public int getGameid() {
        return gameid;
    }

    public String getState() {
        return state;
    }
}
