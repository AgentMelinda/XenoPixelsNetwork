package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-owned flight toward the approved target, at any distance the lock allows.
 *
 * <p>The target is looked up by its approved UUID every tick. Travel moves only through loaded
 * world: a missing chunk pauses it, and after forty ticks cancels it.
 */
public final class V3Travel {
    static final int MAX_TIMEOUT_TICKS = 12000;
    static final int MAX_WAIT_TICKS = 40;
    static final int MAX_BLOCKED_TICKS = 10;

    private V3Travel() {}

    public enum Result { MOVING, ARRIVED, REFUSED }

    /** {@code ceil(distance / speed) + 20}, at most 12000; zero means "do not start". */
    static int timeoutTicks(double distance, double speed) {
        if (!Double.isFinite(distance) || !Double.isFinite(speed) || distance < 0 || speed <= 0) return 0;
        double ticks = Math.ceil(distance / speed) + 20;
        return ticks >= MAX_TIMEOUT_TICKS ? MAX_TIMEOUT_TICKS : (int) ticks;
    }

    /** One tick of movement toward an offset, never past it and never non-finite. */
    static double[] step(double dx, double dy, double dz, double speed) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || !Double.isFinite(speed) || length < 1.0e-9 || speed <= 0) {
            return new double[] {0, 0, 0};
        }
        if (length <= speed) return new double[] {dx, dy, dz};
        double scale = speed / length;
        return new double[] {dx * scale, dy * scale, dz * scale};
    }

    static boolean waitExpired(int waitedTicks) { return waitedTicks > MAX_WAIT_TICKS; }

    /** Requires the motion lease. {@code offset} is added to the target's position each tick. */
    public static boolean start(ServerPlayer player, LivingEntity target, Vec3 offset, double speed, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null || !V3Motion.owns(player) || !V3TargetingRules.finite(offset)) return false;
        int timeout = timeoutTicks(player.distanceTo(target), speed);
        if (timeout <= 0) return false;
        fighter.travelTarget = target.getUUID();
        fighter.travelOffset = offset;
        fighter.travelSpeed = speed;
        fighter.travelStartTick = now;
        fighter.travelTimeout = timeout;
        fighter.travelWait = 0;
        fighter.travelBlocked = 0;
        fighter.travelPose = net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.begin(player, true, true);
        return true;
    }

    public static Result tick(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.travelTarget == null) return Result.REFUSED;
        if (net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.flightLost(player, fighter.travelPose)) return Result.REFUSED;
        LivingEntity target = V3Targeting.resolve(player);
        if (target == null || !target.getUUID().equals(fighter.travelTarget)
                || now - fighter.travelStartTick > fighter.travelTimeout) return Result.REFUSED;

        Vec3 destination = target.position().add(fighter.travelOffset);
        Vec3 to = destination.subtract(player.position());
        if (to.lengthSqr() <= fighter.travelArrive * fighter.travelArrive) return Result.ARRIVED;

        double[] v = step(to.x, to.y, to.z, Math.min(fighter.travelSpeed, Math.max(0.05, to.length() - fighter.travelArrive * 0.5)));
        Vec3 movement = new Vec3(v[0], v[1], v[2]);
        if (!V3ChunkWindow.update(player, player.position().add(movement))) {
            V3Motion.push(player, Vec3.ZERO);
            return waitExpired(++fighter.travelWait) ? Result.REFUSED : Result.MOVING;
        }
        fighter.travelWait = 0;
        if (!fits(player, movement)) {
            // Try to rise over whatever is in the way before giving up on the tick.
            Vec3 climb = new Vec3(movement.x * 0.25, Math.min(fighter.travelSpeed, 1.5), movement.z * 0.25);
            if (V3ChunkWindow.update(player, player.position().add(climb)) && fits(player, climb)) {
                movement = climb;
            } else {
                V3Motion.push(player, Vec3.ZERO);
                return ++fighter.travelBlocked > MAX_BLOCKED_TICKS ? Result.REFUSED : Result.MOVING;
            }
        }
        fighter.travelBlocked = 0;
        Vec3 facing = destination.subtract(player.position());
        float yaw = (float) (Math.toDegrees(Math.atan2(facing.z, facing.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(facing.y, Math.hypot(facing.x, facing.z)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        V3Motion.push(player, movement);
        return Result.MOVING;
    }

    private static boolean fits(ServerPlayer player, Vec3 movement) {
        return player.level().noCollision(player, player.getBoundingBox().move(movement));
    }

    /** Forgets the route. The motion lease is the caller's to release. */
    public static void stop(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        var pose = fighter.travelPose;
        fighter.travelPose = null;
        net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.end(player, pose);
        fighter.travelTarget = null;
        fighter.travelWait = fighter.travelBlocked = 0;
        V3ChunkWindow.release(player);
    }
}
