package net.bullettrain.xenopixelsmod.combat.v2;

/** Timings and the velocity-driven, fifteen-block throw path of UltimateFinisher. */
public final class UltimateFinisherRules {
    public enum Phase { APPROACH, COMBO, GRAB, THROW, CHARGE, BEAM, STOP }
    public static final int PUNCHES = 6;
    public static final int HIT_INTERVAL = 6;
    public static final int GRAB_START = 36;
    public static final int THROW_START = 44;
    public static final int CHARGE_START = 64;
    public static final int BEAM_START = 104;
    /** When the ki wave presentation ends; victim freeze continues past this. */
    public static final int BEAM_WAVE_END = 144;
    /** Owner: keep the target frozen at least 4 more seconds after the wave ends. */
    public static final int POST_BEAM_FREEZE_TICKS = 80;
    public static final int END = BEAM_WAVE_END + POST_BEAM_FREEZE_TICKS;
    public static final double THROW_DISTANCE = 15.0;
    public static final double ARC_HEIGHT = 4.0;

    private UltimateFinisherRules() {}

    public static Phase phase(int elapsed) {
        if (elapsed < 0) return Phase.APPROACH;
        if (elapsed < GRAB_START) return Phase.COMBO;
        if (elapsed < THROW_START) return Phase.GRAB;
        if (elapsed < CHARGE_START) return Phase.THROW;
        if (elapsed < BEAM_START) return Phase.CHARGE;
        // BEAM covers both the wave and the post-wave freeze so the victim stays locked.
        if (elapsed < END) return Phase.BEAM;
        return Phase.STOP;
    }

    /** True while the kamehameha wave should still be driving (before post-freeze). */
    public static boolean beamWaveActive(int elapsed) {
        return elapsed >= BEAM_START && elapsed < BEAM_WAVE_END;
    }

    public static int punchesDue(int elapsed) {
        return elapsed < 0 ? 0 : Math.min(PUNCHES, elapsed / HIT_INTERVAL + 1);
    }

    /** Relative position; the mover writes the difference as velocity, never a teleport. */
    public static double[] throwOffset(int tick) {
        double t = Math.clamp(tick / (double) (CHARGE_START - THROW_START), 0.0, 1.0);
        return new double[] {THROW_DISTANCE * t, 4.0 * ARC_HEIGHT * t * (1.0 - t)};
    }

}
