package net.bullettrain.xenopixelsmod.combat.v2;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whether a v2 swing lands on the fighter's locked target.
 *
 * <p>There is exactly one thing a swing can hit: the target the fighter is locked on. Nothing
 * here looks for anyone else to hit, so a punch thrown beside a crowd touches nobody in it.
 *
 * <p>The geometry is {@link ReachRules}; this class feeds it real positions and adds the wall
 * test, which needs a world.
 *
 * <p>The wall test is a block clip done here rather than {@code hasLineOfSight}. That method is
 * what the lock-on "through blocks" setting rewrites for the local player, and on a single-player
 * world the server's player shares that entity id, so asking it here would let punches through
 * walls whenever the lock-on setting is on. Locking through a wall and punching through one are
 * different things.
 */
final class V2Targeting {

    private V2Targeting() {}

    /** Distance from the attacker's eyes to the nearest point of the target's hitbox. */
    static double reach(ServerPlayer player, LivingEntity target) {
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        return ReachRules.distanceToBox(eye.x, eye.y, eye.z,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /** How squarely the attacker faces the target: 1 dead ahead, -1 directly behind. */
    static double facing(ServerPlayer player, LivingEntity target) {
        Vec3 look = player.getLookAngle();
        return ReachRules.facingDot(look.x, look.z,
                target.getX() - player.getX(), target.getZ() - player.getZ());
    }

    /** True when no solid block stands between the attacker's eyes and some part of the target. */
    static boolean sight(ServerPlayer player, LivingEntity target) {
        Vec3 eye = player.getEyePosition();
        double x = target.getX();
        double z = target.getZ();
        double h = target.getBbHeight();
        return clear(player, eye, new Vec3(x, target.getY() + h * 0.5, z))
                || clear(player, eye, new Vec3(x, target.getY() + h * 0.9, z))
                || clear(player, eye, new Vec3(x, target.getY() + 0.1, z));
    }

    private static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** Whether a swing from where the attacker stands, facing as they face, lands on the target. */
    static boolean canStrike(ServerPlayer player, LivingEntity target, double range, double minFacingDot) {
        if (target == null || !target.isAlive()) return false;
        if (!ReachRules.reachable(reach(player, target), range, facing(player, target), minFacingDot)) {
            return false;
        }
        return !V2Config.get().strikeNeedsLineOfSight || sight(player, target);
    }

    /**
     * The locked target when a swing would land on it, otherwise nobody.
     *
     * @param locked the fighter's lock; null lands on nobody
     */
    static LivingEntity inReach(ServerPlayer player, LivingEntity locked, double range, double minFacingDot) {
        return locked != null && V2Support.refusal(player, locked) == null
                && canStrike(player, locked, range, minFacingDot) ? locked : null;
    }
}
