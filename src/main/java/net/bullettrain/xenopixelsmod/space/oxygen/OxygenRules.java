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
        return !breathable || playerY > effectiveExitAltitude;
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
        SUFFOCATING
    }

    public record State(int breath, int reserve, Phase phase) {
        public State {
            if (breath < 0 || reserve < 0 || phase == null) throw new IllegalArgumentException("invalid oxygen state");
        }

        public static State full(int maxBreath) { return new State(maxBreath, 0, Phase.SAFE); }
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

    /** Adds Breathing Capsule supply, capped. Returns the new reserve. */
    public static int addReserve(int reserve, int addTicks, int capTicks) {
        long sum = (long) reserve + Math.max(0, addTicks);
        return (int) Math.min(capTicks, sum);
    }
}
