package net.bullettrain.xenopixelsmod.space.event;

import java.util.Objects;

/** Pure progression policy for the scripted chart-star stages in §23.1. */
public final class StellarStageTimeline {
    public record State(StellarStage stage, long startedAtTick) {
        public State {
            Objects.requireNonNull(stage, "stage");
            if (startedAtTick < 0L) throw new IllegalArgumentException("stage start tick must be non-negative");
        }
    }

    private StellarStageTimeline() {}

    /**
     * Advances scripted stages using game ticks. STABLE stars remain stable until explicitly scripted.
     * A late server tick can catch up across the telegraph stages while preserving their original timing,
     * but stops at SUPERNOVA for at least one scheduled pass so the event stage is observable and syncable.
     */
    public static State advance(StellarStage stage, long startedAtTick, long nowTick, long rampTicks) {
        return advance(stage, startedAtTick, nowTick, rampTicks, rampTicks);
    }

    /** Keeps the nova stage through the longer of its visual stage ramp and expanding-front lifetime. */
    public static State advance(StellarStage stage, long startedAtTick, long nowTick,
            long rampTicks, long waveDurationTicks) {
        State state = new State(stage, startedAtTick);
        if (stage == StellarStage.STABLE || stage == StellarStage.REMNANT || nowTick < startedAtTick) return state;
        long duration = stage == StellarStage.SUPERNOVA
                ? Math.max(Math.max(1L, rampTicks), Math.max(1L, waveDurationTicks))
                : Math.max(1L, rampTicks);
        long elapsed = nowTick - startedAtTick;
        if (elapsed < duration) return state;

        long nextStart = saturatedAdd(startedAtTick, duration);
        StellarStage next = switch (stage) {
            case SWELLING -> StellarStage.RED_GIANT;
            case RED_GIANT -> StellarStage.DYING;
            case DYING -> StellarStage.SUPERNOVA;
            case SUPERNOVA -> StellarStage.REMNANT;
            case STABLE, REMNANT -> stage;
        };
        State advanced = new State(next, nextStart);
        if (next == StellarStage.SUPERNOVA || nowTick < nextStart) return advanced;
        if (nowTick - nextStart < duration) return advanced;
        StellarStage following = switch (next) {
            case RED_GIANT -> StellarStage.DYING;
            case DYING -> StellarStage.SUPERNOVA;
            case REMNANT -> StellarStage.REMNANT;
            case STABLE, SWELLING, SUPERNOVA -> next;
        };
        if (following == StellarStage.SUPERNOVA || following == StellarStage.REMNANT)
            return new State(following, saturatedAdd(nextStart, duration));
        return new State(StellarStage.DYING, saturatedAdd(nextStart, duration));
    }

    /** Adds elapsed disabled game ticks to a stage that existed before a saved pause began. */
    public static long resumeStartTick(long startedAtTick, long pausedAtTick, long resumedAtTick) {
        if (startedAtTick < 0L || pausedAtTick < 0L || resumedAtTick < 0L)
            throw new IllegalArgumentException("lifecycle ticks must be non-negative");
        if (resumedAtTick <= pausedAtTick) return startedAtTick;
        if (startedAtTick < pausedAtTick) return saturatedAdd(startedAtTick, resumedAtTick - pausedAtTick);
        // A stage created at/after pause time begins accruing only when the lifecycle resumes.
        return resumedAtTick;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
