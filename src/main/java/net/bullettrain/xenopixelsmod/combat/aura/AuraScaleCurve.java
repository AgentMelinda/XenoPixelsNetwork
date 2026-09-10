package net.bullettrain.xenopixelsmod.combat.aura;

/**
 * How big an aura gets, from battle power and from powering up.
 *
 * <p>Two separate ideas, deliberately kept apart. Battle power sets the aura you carry around — a
 * character who has trained for a thousand hours should read as bigger standing still than one who
 * has not. Powering up is transient and much more dramatic: the aura climbs into a column, the way
 * Kefla, Ultra Instinct Goku and Jiren tower inside theirs, and settles again when you stop.
 *
 * <p>Pure arithmetic with no Minecraft or DragonMineZ types, so the curve can be tested. A stat
 * scale that misbehaves is not obvious in a screenshot — an aura that quietly swallows the screen at
 * high power, or never grows at all, both just look like "the aura is wrong".
 */
public final class AuraScaleCurve {

    private AuraScaleCurve() {
    }

    /**
     * The resting size multiplier for a given battle power.
     *
     * <p>Logarithmic on purpose. Battle power in DragonMineZ spans many orders of magnitude — the
     * character in testing sits near three billion — so anything proportional would make a late
     * character's aura hundreds of times the size of an early one. A log curve gives a clear,
     * readable difference between a thousand and a billion while still having somewhere to go above
     * that, and {@code max} stops it running away entirely.
     *
     * @param battlePower the player's battle power; zero and negatives are treated as no bonus
     * @param pivot       the power at which the curve has climbed by roughly {@code gain * 0.3}
     * @param gain        how steeply the aura grows per decade of power
     * @param max         hard ceiling on the multiplier
     */
    public static float fromBattlePower(double battlePower, double pivot, double gain, double max) {
        if (!(battlePower > 0.0) || !(pivot > 0.0) || !(gain > 0.0)) {
            return 1.0f;
        }
        double grown = 1.0 + gain * Math.log10(1.0 + battlePower / pivot);
        return (float) clamp(grown, 1.0, Math.max(1.0, max));
    }

    /**
     * Advances the power-up ramp.
     *
     * <p>Rises while charging and falls back when not, both over {@code rampTicks}, so the aura
     * grows into its column and sinks out of it instead of snapping between two sizes. The fall is
     * deliberately slower than the rise — the anime's auras flare instantly and subside.
     *
     * @param current   the ramp's present value, 0 to 1
     * @param charging  whether the player is powering up right now
     * @param ticks     how many ticks have passed since the last update
     * @param rampTicks ticks a full rise takes; the fall takes twice as long
     * @return the new ramp value, clamped to 0..1
     */
    public static float advanceRamp(float current, boolean charging, float ticks, float rampTicks) {
        if (!(rampTicks > 0.0f) || !(ticks > 0.0f)) {
            return clampRamp(current);
        }
        float step = ticks / rampTicks;
        float next = charging ? current + step : current - step * 0.5f;
        return clampRamp(next);
    }

    /**
     * The height multiplier at a given point in the ramp.
     *
     * <p>Height is where nearly all of the drama lives: the silhouette people recognise is a narrow
     * pillar of light, not a bigger blob, so this climbs far further than {@link #chargeWidth}.
     */
    public static float chargeHeight(float ramp, double extra) {
        return (float) (1.0 + Math.max(0.0, extra) * clampRamp(ramp));
    }

    /** The width multiplier at a given point in the ramp — a much gentler swell than the height. */
    public static float chargeWidth(float ramp, double extra) {
        return (float) (1.0 + Math.max(0.0, extra) * clampRamp(ramp));
    }

    private static float clampRamp(float value) {
        if (Float.isNaN(value)) return 0.0f;
        return (float) clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double low, double high) {
        return value < low ? low : Math.min(value, high);
    }
}
