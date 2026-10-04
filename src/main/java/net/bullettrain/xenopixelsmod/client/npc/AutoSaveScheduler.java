package net.bullettrain.xenopixelsmod.client.npc;

/**
 * When an editor should save on its own, MyNPCs-style: a change saves after {@code quietTicks}
 * without further changes (so typing is one save, not one per key), never while a save is still
 * waiting for the server, and a change made during that wait saves afterwards.
 */
public final class AutoSaveScheduler {
    private final int quietTicks;
    private boolean dirty;
    private boolean inFlight;
    private long lastChange;

    public AutoSaveScheduler(int quietTicks) {
        this.quietTicks = Math.max(1, quietTicks);
    }

    public void markChanged(long tick) {
        dirty = true;
        lastChange = tick;
    }

    public boolean due(long tick) {
        return dirty && !inFlight && tick - lastChange >= quietTicks;
    }

    /** The save left; changes after this belong to the next save. */
    public void sent() {
        dirty = false;
        inFlight = true;
    }

    public void acknowledged() {
        inFlight = false;
    }

    /** The server refused; keep the changes and try again after the quiet period. */
    public void failed(long tick) {
        inFlight = false;
        dirty = true;
        lastChange = tick;
    }

    /** Unsaved or unacknowledged changes exist. */
    public boolean pending() {
        return dirty || inFlight;
    }

    public boolean inFlight() {
        return inFlight;
    }
}
