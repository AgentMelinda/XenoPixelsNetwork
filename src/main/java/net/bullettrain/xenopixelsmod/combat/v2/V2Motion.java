package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.v2.motion.MotionRules;
import net.bullettrain.xenopixelsmod.network.ChaseRouting;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The one mover behind every v2 travel: chase, dragon homing, Z-Burst and Dragon Dash.
 *
 * <p>Velocity only. A travelling fighter is flown toward a point a little at a time, so everyone
 * watching sees a body cross the gap and the fighter's own camera is never touched; nothing here
 * teleports. The arithmetic is {@link MotionRules} and the detour decisions are
 * {@link ChaseRouting}, the rules v1's chase already uses; this class owns what has to touch a
 * player: gravity, the per-tick velocity write, collision, the look of the fighter while they
 * travel ({@link V2TravelPose}), and putting everything back when it ends.
 *
 * <p>The client is told a travel is running with the existing {@link ChaseFlightStatePacket}, so
 * the DragonMineZ flight suppression already written for v1 chases applies to v2 as well.
 */
final class V2Motion {

    /** Long enough to reach the ground from a realistic travel height without dying to it. */
    private static final int LANDING_GRACE_TICKS = 200;
    private static final double LANDING_SNAP = 0.75;
    private static final double OBSTACLE_CLEARANCE = 2.0;
    private static final double MAX_OBSTACLE_CLIMB = 16.0;
    /** A travel that cannot move at all for this long is boxed in and ends. */
    private static final int MAX_BLOCKED_TICKS = 10;

    private V2Motion() {}

    // ---- travel ----

    static void startTravel(ServerPlayer player, V2Fighter f, LivingEntity target,
                            V2Fighter.TravelKind kind, double speed, double arrive, float charge) {
        int now = V2Support.tick(player);
        V2Config.Values cfg = V2Config.get();
        if (!f.traveling()) {
            f.travelNoGravityBefore = player.isNoGravity();
            player.setNoGravity(true);
            // A burst is a lunge of a few ticks: it gets the aura, not a change of flight mode.
            f.travelPose = V2TravelPose.begin(player, cfg.travelAura,
                    kind == V2Fighter.TravelKind.CHASE || kind == V2Fighter.TravelKind.ULTIMATE_FINISHER
                            || (cfg.travelFlightPose && kind != V2Fighter.TravelKind.Z_BURST));
            ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(true));
            CombatFx.cue(player.serverLevel(), player.position(), CombatFxKind.DASH_LAUNCH, 1.0f);
        }
        f.travelKind = kind;
        f.travelTargetId = target.getId();
        f.travelStartTick = now;
        f.travelSpeed = speed;
        f.travelArrive = arrive;
        f.travelCharge = charge;
        f.travelTimeoutTicks = MotionRules.timeoutTicks(player.distanceTo(target), speed, 40);
        f.travelRoute = V2Fighter.TravelRoute.DIRECT;
        f.travelHasWaypoint = false;
        f.travelBlockedTicks = 0;
        if (f.state == V2State.NEUTRAL) f.state = V2State.TRAVEL;
    }

    /**
     * Advances a travel one tick.
     *
     * @return the kind that arrived this tick, or null while still flying or after a failure
     */
    static V2Fighter.TravelKind tickTravel(ServerPlayer player, V2Fighter f, int now) {
        if (!f.traveling()) return null;
        LivingEntity target = V2Support.living(player, f.travelTargetId);
        if (target == null) {
            stopTravel(player, f);
            V2Support.hint(player, "Lost the target");
            return null;
        }
        if (now - f.travelStartTick > f.travelTimeoutTicks || V2TravelPose.flightLost(player, f.travelPose)) {
            stopTravel(player, f);
            return null;
        }

        Vec3 landing = Bt3Landing.chaseLanding(player, target);
        Vec3 pos = player.position();
        if (MotionRules.arrived(player.distanceTo(target), f.travelArrive)
                || MotionRules.arrived(pos.distanceTo(landing), LANDING_SNAP)) {
            V2Fighter.TravelKind kind = f.travelKind;
            stopTravel(player, f);
            return kind;
        }

        Vec3 destination = route(player, f, target, landing, pos);
        Vec3 to = destination.subtract(pos);
        double[] v = MotionRules.stepToward(to.x, to.y, to.z, f.travelSpeed);
        Vec3 movement = unblocked(player, new Vec3(v[0], v[1], v[2]), f.travelSpeed);
        V2Support.faceBody(player, target.getX(), target.getZ());
        if (movement == null) {
            // Nothing moves this tick. Re-plan from scratch next tick; give up if it keeps happening.
            f.travelRoute = V2Fighter.TravelRoute.DIRECT;
            f.travelHasWaypoint = false;
            push(player, Vec3.ZERO);
            if (++f.travelBlockedTicks > MAX_BLOCKED_TICKS) {
                stopTravel(player, f);
                V2Support.hint(player, "The way is blocked");
            }
            return null;
        }
        f.travelBlockedTicks = 0;
        push(player, movement);
        return null;
    }

    /**
     * Where to fly this tick: straight at the landing point, or up to a waypoint above whatever
     * is in the way and then down onto the target.
     */
    private static Vec3 route(ServerPlayer player, V2Fighter f, LivingEntity target, Vec3 landing, Vec3 pos) {
        if (f.travelRoute == V2Fighter.TravelRoute.DIRECT && pathBlocked(player, target, landing)) {
            Vec3 waypoint = elevatedWaypoint(player, target, landing);
            if (waypoint != null) {
                f.travelRoute = V2Fighter.TravelRoute.ASCEND;
                f.travelHasWaypoint = true;
                f.travelWaypointX = waypoint.x;
                f.travelWaypointY = waypoint.y;
                f.travelWaypointZ = waypoint.z;
            }
        }
        if (f.travelRoute == V2Fighter.TravelRoute.ASCEND && f.travelHasWaypoint) {
            Vec3 waypoint = new Vec3(f.travelWaypointX, f.travelWaypointY, f.travelWaypointZ);
            if (pos.distanceTo(waypoint) > LANDING_SNAP) return waypoint;
            f.travelRoute = V2Fighter.TravelRoute.DIVE;
        }
        if (f.travelRoute == V2Fighter.TravelRoute.DIVE && pathBlocked(player, target, landing)) {
            // Still something in the way from up here: plan again next tick.
            f.travelRoute = V2Fighter.TravelRoute.DIRECT;
            f.travelHasWaypoint = false;
        }
        return landing;
    }

    /**
     * Aim the path test at the target's body rather than the landing point at its feet, so the
     * surface the target stands on is not mistaken for a wall.
     */
    private static Vec3 pathAim(LivingEntity target, Vec3 landing) {
        return new Vec3(landing.x, landing.y + target.getBbHeight() * 0.5, landing.z);
    }

    private static boolean pathBlocked(ServerPlayer player, LivingEntity target, Vec3 landing) {
        Vec3 start = player.getBoundingBox().getCenter();
        Vec3 aim = pathAim(target, landing);
        if (!ChaseRouting.detourWorthwhile(Math.hypot(aim.x - start.x, aim.z - start.z))) return false;
        BlockHitResult hit = player.level().clip(new ClipContext(
                start, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.MISS
                && ChaseRouting.obstructs(hit.getLocation().distanceTo(start), start.distanceTo(aim));
    }

    private static Vec3 elevatedWaypoint(ServerPlayer player, LivingEntity target, Vec3 landing) {
        Vec3 start = player.position();
        Vec3 center = player.getBoundingBox().getCenter();
        BlockHitResult hit = player.level().clip(new ClipContext(
                center, pathAim(target, landing), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return null;
        double requiredY = ChaseRouting.clearanceY(hit.getLocation().y, player.getBbHeight(), OBSTACLE_CLEARANCE);
        if (!ChaseRouting.detourReachable(requiredY, start.y, MAX_OBSTACLE_CLIMB)) return null;
        Vec3 waypoint = new Vec3(start.x, requiredY, start.z);
        return player.level().noCollision(player, player.getBoundingBox().move(waypoint.subtract(start)))
                ? waypoint : null;
    }

    /**
     * The movement to make this tick, given that the wanted one may clip a block: the wanted one,
     * else its horizontal part, else its vertical part, else a hop straight up over a lip. Null
     * when none of them fits.
     */
    private static Vec3 unblocked(ServerPlayer player, Vec3 wanted, double speed) {
        if (fits(player, wanted)) return wanted;
        Vec3 flat = new Vec3(wanted.x, 0.0, wanted.z);
        if (wanted.y != 0.0 && flat.lengthSqr() > 1.0e-6 && fits(player, flat)) return flat;
        Vec3 vertical = new Vec3(0.0, wanted.y, 0.0);
        if (Math.abs(wanted.y) > 1.0e-3 && fits(player, vertical)) return vertical;
        Vec3 hop = new Vec3(0.0, Math.min(1.0, speed), 0.0);
        return fits(player, hop) ? hop : null;
    }

    private static boolean fits(ServerPlayer player, Vec3 movement) {
        return player.level().noCollision(player, player.getBoundingBox().move(movement));
    }

    /** Ends a travel and gives the fighter their own movement back. Safe to call when idle. */
    static void stopTravel(ServerPlayer player, V2Fighter f) {
        if (!f.traveling()) return;
        if (f.travelKind == V2Fighter.TravelKind.DRAGON_DASH) f.clearDashFollow();
        f.travelKind = null;
        f.travelTargetId = -1;
        f.travelRoute = V2Fighter.TravelRoute.DIRECT;
        f.travelHasWaypoint = false;
        f.travelBlockedTicks = 0;
        if (f.state == V2State.TRAVEL) f.state = V2State.NEUTRAL;
        player.setNoGravity(f.travelNoGravityBefore);
        push(player, Vec3.ZERO);
        // A travel ends where it ends, often far above the ground. Without this the fighter is
        // handed gravity back at altitude and dies to the landing rather than to the fight.
        player.resetFallDistance();
        Bt3CombatEvents.grantFallGrace(player, LANDING_GRACE_TICKS);
        V2TravelPose.Snapshot pose = f.travelPose;
        f.travelPose = null;
        V2TravelPose.end(player, pose);
        ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(false));
    }

    private static void push(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
