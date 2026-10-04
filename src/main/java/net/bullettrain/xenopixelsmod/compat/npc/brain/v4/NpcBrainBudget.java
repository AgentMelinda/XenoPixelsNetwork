package net.bullettrain.xenopixelsmod.compat.npc.brain.v4;

public final class NpcBrainBudget {
    private final int maximum;
    private long tick = Long.MIN_VALUE;
    private int used;

    public NpcBrainBudget(int maximum) {
        this.maximum = Math.max(1, maximum);
    }

    public synchronized boolean tryAcquire(long serverTick) {
        if (tick != serverTick) {
            tick = serverTick;
            used = 0;
        }
        if (used >= maximum) return false;
        used++;
        return true;
    }
}
