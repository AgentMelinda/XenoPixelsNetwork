package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Pure admission and snapshot rules, shared by server validation and the client mirror. */
public final class V3TargetingRules {
    private V3TargetingRules() {}
    /**
     * How far a V3 lock reaches, to acquire and to keep. It is the whole V3 range and deliberately
     * not the Dragon Dash setting: a short dash must not cost the fighter their target.
     */
    public static final double LOCK_RANGE = 999.0;
    public static boolean inRange(double distanceSquared, double range) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0
                && Double.isFinite(range) && range >= 2 && range <= 999
                && distanceSquared <= range * range;
    }
    public static boolean matches(UUID approved, UUID candidate) {
        return approved != null && approved.equals(candidate);
    }
    public static boolean newer(long revision, long watermark) { return revision >= 0 && revision > watermark; }
    public static double rayDistance(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) ? 0 : box.clip(from, to).map(from::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
    }
    static boolean cycleAllowed(UUID approved, UUID requested, boolean currentValid, boolean candidateValid) {
        return approved != null && requested != null && currentValid && candidateValid;
    }
    public static boolean motionDue(V3TargetSnapshot previous, Vec3 position, Vec3 velocity, long now, long lastSent) {
        return previous != null && now - lastSent >= 2
                && (previous.position().distanceToSqr(position) >= 0.0025
                || previous.velocity().distanceToSqr(velocity) >= 0.0001);
    }
    public static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
