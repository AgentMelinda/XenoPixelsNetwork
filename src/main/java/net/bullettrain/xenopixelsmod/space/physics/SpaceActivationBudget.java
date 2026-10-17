package net.bullettrain.xenopixelsmod.space.physics;

import java.util.EnumSet;

/** Pure tick-based gate shared by generated Sable-body activators. */
public final class SpaceActivationBudget {
    public static final int MIN_INTERVAL_TICKS = 10;
    private static final long STALE_REQUEST_TICKS = MIN_INTERVAL_TICKS * 3L;
    public enum Owner { CHART_PLANET, ASTEROID, SMALL_PLANET, SUPER_DRAGON_BALL }
    private static final Owner[] OWNERS = Owner.values();

    private long lastGrantedTick;
    private boolean hasGranted;
    private Owner nextOwner = Owner.CHART_PLANET;
    private final EnumSet<Owner> deferredOwners = EnumSet.noneOf(Owner.class);
    private final long[] deferredSinceTick = new long[Owner.values().length];
    private final long[] lastRequestedTick = new long[Owner.values().length];
    private final long[] deniedRequests = new long[Owner.values().length];
    private final long[] rateLimitDenials = new long[Owner.values().length];
    private final long[] arbitrationDenials = new long[Owner.values().length];
    private final long[] lastDeniedTick = new long[Owner.values().length];

    public SpaceActivationBudget() {
        java.util.Arrays.fill(deferredSinceTick, Long.MIN_VALUE);
        java.util.Arrays.fill(lastRequestedTick, Long.MIN_VALUE);
        java.util.Arrays.fill(lastDeniedTick, Long.MIN_VALUE);
    }

    /** Earliest scheduled retry after a gate denial; callers must not sleep through a whole scan interval. */
    public static long denialRetryTick(long gameTime) {
        return tickAfter(gameTime, MIN_INTERVAL_TICKS);
    }

    /** Saturating tick addition for retry/cadence scheduling near the representable game-time limit. */
    public static long tickAfter(long gameTime, int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        return gameTime > Long.MAX_VALUE - interval ? Long.MAX_VALUE : gameTime + interval;
    }

    /** A denied activation retries at the gate cadence; ordinary attempts retain their configured scan cadence. */
    public static long nextAttemptTick(long gameTime, int configuredIntervalTicks, boolean budgetDenied) {
        return budgetDenied ? denialRetryTick(gameTime) : tickAfter(gameTime, configuredIntervalTicks);
    }

    /**
     * Grants at most one activation per minimum interval. Contending owners take rotating turns regardless
     * of pass order. If the preferred owner is idle, current waiters can use its slot on the following tick;
     * stale requests expire so an owner that stopped asking cannot hold the gate closed.
     */
    public boolean tryAcquire(Owner owner, long gameTime) {
        if (owner == null) throw new IllegalArgumentException("activation owner is required");
        // No owner has taken a turn yet, so there is no contention to arbitrate. Let the first
        // eligible manager start immediately, then rotate from the owner that actually won.
        if (!hasGranted) return grant(owner, gameTime);
        // No representable future tick can be a full interval after this point.
        if (lastGrantedTick > Long.MAX_VALUE - MIN_INTERVAL_TICKS) return deny(owner, gameTime, true);
        if (gameTime < lastGrantedTick + MIN_INTERVAL_TICKS) return deny(owner, gameTime, true);
        expireStaleRequests(gameTime);
        // No later game tick exists to give a deferred owner its normal one-tick grace.
        // At the terminal representable tick, grant the eligible request instead of stranding it.
        if (gameTime == Long.MAX_VALUE) return grant(owner, gameTime);
        int ownerIndex = owner.ordinal();
        lastRequestedTick[ownerIndex] = gameTime;
        if (owner == nextOwner) return grant(owner, gameTime);

        if (deferredOwners.add(owner)) deferredSinceTick[ownerIndex] = gameTime;
        if (gameTime <= deferredSinceTick[ownerIndex]) return deny(owner, gameTime, false);
        if (fallbackOwner(gameTime) != owner) return deny(owner, gameTime, false);
        return grant(owner, gameTime);
    }

    private boolean deny(Owner owner, long gameTime, boolean rateLimited) {
        int index = owner.ordinal();
        if (deniedRequests[index] < Long.MAX_VALUE) deniedRequests[index]++;
        long[] reasonCounter = rateLimited ? rateLimitDenials : arbitrationDenials;
        if (reasonCounter[index] < Long.MAX_VALUE) reasonCounter[index]++;
        lastDeniedTick[index] = gameTime;
        return false;
    }

    public long deniedRequests(Owner owner) {
        if (owner == null) throw new IllegalArgumentException("activation owner is required");
        return deniedRequests[owner.ordinal()];
    }

    /** Requests refused only because the global minimum interval has not elapsed. */
    public long rateLimitDenials(Owner owner) {
        if (owner == null) throw new IllegalArgumentException("activation owner is required");
        return rateLimitDenials[owner.ordinal()];
    }

    /** Requests refused because another eligible owner has priority for this activation turn. */
    public long arbitrationDenials(Owner owner) {
        if (owner == null) throw new IllegalArgumentException("activation owner is required");
        return arbitrationDenials[owner.ordinal()];
    }

    public long lastDeniedTick(Owner owner) {
        if (owner == null) throw new IllegalArgumentException("activation owner is required");
        return lastDeniedTick[owner.ordinal()];
    }

    private boolean grant(Owner owner, long gameTime) {
        lastGrantedTick = gameTime;
        hasGranted = true;
        nextOwner = OWNERS[(owner.ordinal() + 1) % OWNERS.length];
        deferredOwners.clear();
        java.util.Arrays.fill(deferredSinceTick, Long.MIN_VALUE);
        java.util.Arrays.fill(lastRequestedTick, Long.MIN_VALUE);
        return true;
    }

    private Owner fallbackOwner(long gameTime) {
        for (int step = 0; step < OWNERS.length; step++) {
            Owner candidate = OWNERS[(nextOwner.ordinal() + step) % OWNERS.length];
            int index = candidate.ordinal();
            if (candidate != nextOwner && deferredOwners.contains(candidate)
                    && lastRequestedTick[index] != Long.MIN_VALUE
                    && gameTime >= lastRequestedTick[index]
                    // Callers run in a fixed pass order and may not have reached this method yet this
                    // tick. Keep a waiter eligible for one token interval, but do not let the 30-tick
                    // bookkeeping expiry turn a stopped caller into a blocker.
                    && gameTime - lastRequestedTick[index] <= MIN_INTERVAL_TICKS
                    && gameTime > deferredSinceTick[index]) return candidate;
        }
        return null;
    }

    private void expireStaleRequests(long gameTime) {
        for (Owner owner : OWNERS) {
            int index = owner.ordinal();
            long lastRequested = lastRequestedTick[index];
            if (lastRequested != Long.MIN_VALUE && gameTime >= lastRequested
                    && gameTime - lastRequested > STALE_REQUEST_TICKS) {
                deferredOwners.remove(owner);
                deferredSinceTick[index] = Long.MIN_VALUE;
                lastRequestedTick[index] = Long.MIN_VALUE;
            }
        }
    }

    public long nextAllowedTick() {
        if (!hasGranted) return Long.MIN_VALUE;
        return lastGrantedTick > Long.MAX_VALUE - MIN_INTERVAL_TICKS
                ? Long.MAX_VALUE : lastGrantedTick + MIN_INTERVAL_TICKS;
    }
}
