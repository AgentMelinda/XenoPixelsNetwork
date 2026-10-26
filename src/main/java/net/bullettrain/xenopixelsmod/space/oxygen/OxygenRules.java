package net.bullettrain.xenopixelsmod.space.oxygen;

/**
 * Pure oxygen state machine for design-doc §6, in ticks, with no Minecraft types so it is unit-testable.
 * <p>In vacuum a player draws first on their Breathing Capsule supply ({@code reserve}), then holds their breath
 * ({@code breath} counts down), then suffocates; suffocation costs ki and, once ki is gone, health (applied by
 * {@link OxygenSystem}, not here). Out of vacuum, breath refills at a configurable rate and the reserve is kept.
 */
public final class OxygenRules {
    private OxygenRules() {}

    /**
     * Whether a charted body's atmosphere is absent at the player's height. The caller supplies the effective exit
     * altitude (including chart/config overrides), so oxygen and the altitude handoff can share the same boundary.
     */
    public static boolean inVacuum(boolean breathable, double playerY, double effectiveExitAltitude) {
        return inVacuum(breathable, playerY, effectiveExitAltitude, true);
    }

    /** Per-dimension sidecar switch controls whether crossing the chart exit changes breathable air to vacuum. */
    public static boolean inVacuum(boolean breathable, double playerY, double effectiveExitAltitude,
                                   boolean oxygenRequiredAboveExit) {
        return !breathable || oxygenRequiredAboveExit && playerY > effectiveExitAltitude;
    }

    /** Shared-dimension cells may not have a persisted travel trip; use the dimension's real ceiling then. */
    public static double sharedSurfaceExitAltitude(int maxBuildHeight, double tripExitAltitude) {
        return Double.isFinite(tripExitAltitude) ? tripExitAltitude : maxBuildHeight - 16.0;
    }

    /** Sidecar rates are relative to the server's configured base drain: slow=half, normal=base, fast=double. */
    public static int drainTicks(int baseTicks, String rate) {
        if (baseTicks <= 0) return 0;
        if (rate == null || rate.equals("normal")) return baseTicks;
        if (rate.equals("slow")) return Math.max(1, (baseTicks + 1) / 2);
        if (rate.equals("fast")) return baseTicks > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : baseTicks * 2;
        throw new IllegalArgumentException("unsupported oxygen drain rate: " + rate);
    }

    public enum Phase {
        /** Breathable air, lungs full. */
        SAFE,
        /** Breathable air, lungs refilling after a vacuum stint. */
        RECOVERING,
        /** Vacuum, breathing from Breathing Capsule supply. */
        SUPPLIED,
        /** Vacuum, no supply, holding breath (§6 phase 1). */
        HOLDING,
        /** Vacuum, out of breath: ki, then health, pay for it (§6 phase 2). */
        SUFFOCATING,
        /** Vacuum, where a paid DMZ aura is currently substituting for the capsule. */
        AURA
    }

    public record State(int breath, int reserve, Phase phase) {
        public State {
            if (breath < 0 || reserve < 0 || phase == null) throw new IllegalArgumentException("invalid oxygen state");
        }

        public static State full(int maxBreath) { return new State(maxBreath, 0, Phase.SAFE); }
    }

    /** Ki due for an aura substitution over one active sample interval. Zero rate is explicitly free. */
    public static double auraKiCost(double kiPerSecond, int elapsedTicks) {
        if (!Double.isFinite(kiPerSecond) || kiPerSecond < 0.0 || elapsedTicks < 0)
            throw new IllegalArgumentException("invalid aura oxygen charge");
        return kiPerSecond * elapsedTicks / 20.0;
    }

    /** Zero configured cost is explicitly free; a positive cost requires a successful Ki payment. */
    public static boolean auraPaymentAllowsSubstitute(double cost, boolean paymentSucceeded) {
        if (!Double.isFinite(cost) || cost < 0.0) throw new IllegalArgumentException("invalid aura oxygen cost");
        return cost == 0.0 || paymentSucceeded;
    }

    /** Positive prices must remain positive and finite when converted to DMZ's float resource API. */
    public static boolean auraCostRepresentable(double cost) {
        if (!Double.isFinite(cost) || cost < 0.0) throw new IllegalArgumentException("invalid aura oxygen cost");
        if (cost == 0.0) return true;
        float resourceCost = (float) cost;
        return resourceCost > 0.0f && Float.isFinite(resourceCost);
    }

    /**
     * Advances the state by {@code ticks}.
     *
     * @param maxBreath      lungs capacity in ticks (oxygenBreathSeconds * 20)
     * @param refillPerTick  breath ticks regained per tick in air (oxygenRefillPerSecond, as seconds of breath per second)
     */
    public static State step(State state, boolean vacuum, int ticks, int maxBreath, double refillPerTick) {
        if (ticks <= 0) return state;
        int breath = Math.min(state.breath(), maxBreath), reserve = state.reserve();
        if (!vacuum) {
            breath = (int) Math.min(maxBreath, breath + Math.ceil(refillPerTick * ticks));
            return new State(breath, reserve, breath >= maxBreath ? Phase.SAFE : Phase.RECOVERING);
        }
        int fromReserve = Math.min(reserve, ticks);
        reserve -= fromReserve;
        int remaining = ticks - fromReserve;
        if (remaining == 0) return new State(breath, reserve, Phase.SUPPLIED);
        breath = Math.max(0, breath - remaining);
        return new State(breath, reserve, breath > 0 ? Phase.HOLDING : Phase.SUFFOCATING);
    }

    /** Keeps the existing capsule and lung stores intact while an active aura provides breathing. */
    public static State auraSubstitute(State state, int maxBreath) {
        if (state == null || maxBreath <= 0) throw new IllegalArgumentException("invalid aura oxygen state");
        return new State(Math.min(state.breath(), maxBreath), state.reserve(), Phase.AURA);
    }

    /** Adds Breathing Capsule supply, capped. Returns the new reserve. */
    public static int addReserve(int reserve, int addTicks, int capTicks) {
        long sum = (long) reserve + Math.max(0, addTicks);
        return (int) Math.min(capTicks, sum);
    }
}
