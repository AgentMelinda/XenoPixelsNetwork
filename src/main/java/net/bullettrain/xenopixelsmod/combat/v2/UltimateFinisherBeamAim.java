package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.v3.technique.V3RushGeometry;
import net.minecraft.world.phys.Vec3;

/** Pure centered muzzle/aim geometry, with no camera ray or right-hand offset. */
public final class UltimateFinisherBeamAim {
    public record Aim(Vec3 origin, Vec3 direction, float yaw, float pitch) {}
    private UltimateFinisherBeamAim() {}
    public static Aim toward(Vec3 eye, Vec3 targetCenter, Vec3 fallback) {
        Vec3 direction = V3RushGeometry.aim(eye, targetCenter, fallback);
        Vec3 origin = eye.add(direction.scale(Math.min(1.5, eye.distanceTo(targetCenter) * .25)));
        return new Aim(origin, direction,
                (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90),
                (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
    }
}
