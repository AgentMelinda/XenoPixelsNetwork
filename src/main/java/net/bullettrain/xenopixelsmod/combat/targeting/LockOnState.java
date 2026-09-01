package net.bullettrain.xenopixelsmod.combat.targeting;

/**
 * One locker's runtime lock, owned and mutated exclusively by {@link TargetLockManager} on the
 * server thread. Holds only what the manager needs between ticks; everything derived (quality,
 * lead) is computed fresh rather than cached here.
 */
final class LockOnState {
    final int targetEntityId;
    double progress;
    /** Ticks of continued grace remaining while conditions are momentarily invalid. Resets to the configured grace on every valid tick. */
    int graceTicksLeft;

    /** Last values pushed to the client, so the per-tick loop only sends on a real change. */
    int lastSyncedTargetId = Integer.MIN_VALUE;
    int lastSyncedProgressPercent = -1;
    LockOnQuality lastSyncedQuality;
    /** Whether the last packet carried a lead velocity, and what it was. */
    boolean lastSyncedHadVelocity;
    net.minecraft.world.phys.Vec3 lastSyncedVelocity = net.minecraft.world.phys.Vec3.ZERO;
    /**
     * Ticks since the last packet. Starts high so a freshly created lock always syncs on its
     * very first tick rather than waiting for the keepalive to come round.
     */
    int ticksSinceSync = Integer.MAX_VALUE / 2;

    LockOnState(int targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    LockOnQuality quality() {
        return LockOnQuality.fromProgress(progress);
    }
}
