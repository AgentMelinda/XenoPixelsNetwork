package net.bullettrain.xenopixelsmod.combat.v3.technique;

import net.minecraft.world.phys.Vec3;

/** Body-centered rush spacing and release aim, independent of cinematic view direction. */
public final class V3RushGeometry {
    private V3RushGeometry() {}

    public static Vec3 contactPosition(Vec3 attacker, double attackerHeight, Vec3 victim,
                                       double victimHeight, Vec3 fallbackForward) {
        Vec3 forward = victim.subtract(attacker).multiply(1, 0, 1);
        if (forward.lengthSqr() < 1e-6) forward = fallbackForward.multiply(1, 0, 1);
        if (forward.lengthSqr() < 1e-6) forward = new Vec3(0, 0, 1);
        return victim.subtract(forward.normalize().scale(2))
                .add(0, (victimHeight - attackerHeight) * .5, 0);
    }

    /** Ultimate Rush opens an exact ten-block body gap along the target's current bearing. */
    public static Vec3 ultimateOpening(Vec3 attacker,Vec3 victim,Vec3 fallback){
        return attacker.add(aim(attacker,victim,fallback).scale(10));
    }

    public static Vec3 aim(Vec3 origin, Vec3 targetCenter, Vec3 fallback) {
        Vec3 direction = targetCenter.subtract(origin);
        if (direction.lengthSqr() < 1e-6) direction = fallback;
        if (direction.lengthSqr() < 1e-6) direction = new Vec3(0, 0, 1);
        return direction.normalize();
    }
}
