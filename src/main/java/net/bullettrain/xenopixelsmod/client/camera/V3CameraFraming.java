package net.bullettrain.xenopixelsmod.client.camera;

import net.minecraft.world.phys.Vec3;

/** Pure wide-shot framing: contain both complete fighters for the current FOV and aspect. */
public final class V3CameraFraming {
    public record Frame(Vec3 position, Vec3 lookAt) {}
    private V3CameraFraming() {}

    public static boolean shouldFitBoth(double authoredFocus, double distance, boolean firing) {
        return firing || authoredFocus >= .45 || distance >= 8;
    }

    public static Frame fit(Vec3 attacker, double attackerHeight, Vec3 victim, double victimHeight,
                            Vec3 proposedPosition, Vec3 proposedLookAt, double verticalFov, double aspect) {
        Vec3 center = attacker.lerp(victim, .5);
        Vec3 offset = proposedPosition.subtract(proposedLookAt);
        if (offset.lengthSqr() < 1e-6) offset = new Vec3(-1, .5, -1);
        double verticalHalf = Math.toRadians(Math.clamp(verticalFov, 20, 120) * .5);
        double horizontalHalf = Math.atan(Math.tan(verticalHalf) * Math.max(.1, aspect));
        double radius = attacker.distanceTo(victim) * .5
                + Math.max(attackerHeight, victimHeight) * .65 + .75;
        double distance = Math.max(offset.length(), radius / Math.sin(Math.min(verticalHalf, horizontalHalf)) + .5);
        return new Frame(center.add(offset.normalize().scale(distance)), center);
    }
}
