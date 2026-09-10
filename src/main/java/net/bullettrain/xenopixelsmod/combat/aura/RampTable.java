package net.bullettrain.xenopixelsmod.combat.aura;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-owner power-up ramps, advanced on wall-clock time.
 *
 * <p>Split out from the aura scaling and kept free of Minecraft and DragonMineZ types so the part
 * that actually went wrong can be tested. The first version of this keyed its ramps off a
 * "who is being drawn" marker that only one of DragonMineZ's four aura render paths set, so a ramp
 * applied on some frames and not others — the aura visibly jumped — and could be attributed to
 * whichever entity had been drawn last. Which key a ramp belongs to is therefore not a detail.
 *
 * <p>Time comes in from the caller rather than being read here, so a test can drive it without
 * sleeping.
 *
 * @param <K> whatever identifies an owner; equality is however that type defines it
 */
public final class RampTable<K> {

    /** Beyond this many entries the table is swept for owners nothing has asked about lately. */
    private static final int PRUNE_ABOVE = 64;
    private static final long STALE_NANOS = 5_000_000_000L;
    /** A tick is 50ms; ramp maths is in ticks so it matches the rest of the mod. */
    private static final float NANOS_PER_TICK = 5.0e7f;
    /**
     * The largest step a single advance may take.
     *
     * <p>A loading screen or an alt-tab can leave seconds between two frames, and without this the
     * ramp would teleport to its end and the aura would snap rather than climb.
     */
    private static final float MAX_STEP_TICKS = 10.0f;

    private final Map<K, Ramp> ramps = new ConcurrentHashMap<>();

    /**
     * Advances one owner's ramp and returns its new value.
     *
     * @param key       the owner
     * @param rising    whether that owner is powering up right now
     * @param nowNanos  the current time, from the caller
     * @param rampTicks how many ticks a full rise takes
     * @return the ramp, 0 to 1
     */
    public float advance(K key, boolean rising, long nowNanos, float rampTicks) {
        if (key == null) {
            return 0.0f;
        }
        Ramp state = ramps.computeIfAbsent(key, ignored -> new Ramp());
        // A ramp seen for the first time has no previous timestamp, so it advances by nothing and
        // starts moving on the next frame instead of jumping by however long the game had been up.
        float ticks = state.lastNanos == 0L
                ? 0.0f
                : Math.min((nowNanos - state.lastNanos) / NANOS_PER_TICK, MAX_STEP_TICKS);
        state.lastNanos = nowNanos;
        state.value = AuraScaleCurve.advanceRamp(state.value, rising, ticks, rampTicks);
        prune(nowNanos);
        return state.value;
    }

    /** This owner's ramp without advancing it, or 0 when there is none. */
    public float peek(K key) {
        Ramp state = key == null ? null : ramps.get(key);
        return state == null ? 0.0f : state.value;
    }

    /** Forgets every ramp, so leaving a world cannot carry one into the next. */
    public void clear() {
        ramps.clear();
    }

    /** How many owners are being tracked. */
    public int size() {
        return ramps.size();
    }

    private void prune(long nowNanos) {
        if (ramps.size() <= PRUNE_ABOVE) {
            return;
        }
        ramps.entrySet().removeIf(entry -> nowNanos - entry.getValue().lastNanos > STALE_NANOS);
    }

    private static final class Ramp {
        private float value;
        private long lastNanos;
    }
}
