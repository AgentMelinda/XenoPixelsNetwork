package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Timing, size and placement of the missile effects. */
public final class MissileEffectRules {
    private MissileEffectRules() {}

    /** The thruster effect is a short pulse re-sent every 8 ticks while the motor burns. */
    public static boolean thrusterPulseDue(long tick) {
        return Math.floorMod(tick, 8) == 0;
    }

    /** Explosion effect size from the blast power (vanilla TNT is 4). */
    /**
     * Size of the bound missile plume: 0.4 x the missile's length, which puts the plume's nozzle
     * (1.25 effect units behind the entity centre, see tools/effekseer/efkgen/effects/thruster.py)
     * exactly at the tail.
     */
    public static float boundThrusterScale(float visualLength) {
        return 0.4f * Math.max(0.1f, visualLength);
    }

    public static float explosionScale(float power) {
        return Mth.clamp(0.5f + power / 4.0f, 0.5f, 6.0f);
    }

    /** Where the exhaust comes out: behind the centre along -velocity, or below when not moving. */
    public static Vec3 nozzle(Vec3 centre, Vec3 velocity, double length) {
        if (velocity == null || velocity.lengthSqr() < 1.0e-6) return centre.add(0, -length, 0);
        return centre.subtract(velocity.normalize().scale(length));
    }
}
