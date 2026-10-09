package net.bullettrain.xenopixelsmod.combat.v3.technique;

/**
 * One typed step of a V3 technique timeline. Data can only choose among these kinds; there are no
 * script strings and nothing is looked up by reflection.
 *
 * @param tick     server ticks after the cast started
 * @param duration how long the step lasts, for the kinds that last
 * @param payload  a clip name for POSE, a direction for SHOVE, a radius for RADIAL; otherwise empty
 * @param value    a damage scale for STRIKE and RADIAL; otherwise 0
 */
public record V3Beat(Kind kind, int tick, int duration, String payload, float value) {
    public enum Kind { POSE, APPROACH, STRIKE, SHOVE, RADIAL, HOLD_TARGET, KI_CHARGE, KI_RELEASE, END, KI_HOLD }

    public static final int MAX_TICK = 1200;

    public V3Beat {
        if (kind == null || tick < 0 || tick > MAX_TICK || duration < 0 || duration > MAX_TICK
                || payload == null || payload.length() > 64 || !Float.isFinite(value) || value < 0f || value > 10f) {
            throw new IllegalArgumentException("Invalid V3 beat");
        }
    }
}
