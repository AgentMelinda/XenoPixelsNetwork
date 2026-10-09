package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dragon Dash (owner design 2026-10-08).
 *
 * <ul>
 *   <li>First N: fly at the locked target with DragonMineZ's flight state and server velocity
 *       ({@link V3Travel}), land one strong hit and launch the target
 *       {@code v3.dragonDashLaunchDistance} blocks (default 20, at least 15 unobstructed).</li>
 *   <li>Second, third, ... N: knockback strike (≥ {@code v3.dragonDashLaunchDistance}, W=up punch /
 *       S=down kick) then teleport to the far side from you (past the target) while they fly.
 *       LMB/RMB stay free between presses. The follow window renews each time.</li>
 *   <li>No cinematic camera for the dash chain; that belongs to the Strike attacks.</li>
 * </ul>
 */
public final class V3Dash {
    /** Default continuation window; the live value is {@code v3.dragonDashFollowWindowTicks}. */
    static final int CONTINUATION_TICKS = V3Config.Values.DEFAULT_DASH_FOLLOW_WINDOW;
    /** Default launch; the live value is {@code v3.dragonDashLaunchDistance}. */
    static final double LAUNCH_DISTANCE = V3Config.Values.DEFAULT_DASH_LAUNCH;
    /** Plan balancing values, not BT3 constants. */
    static final double ARRIVE = 2.4;
    static final int COOLDOWN_TICKS = 40;

    private V3Dash() {}

    static boolean inDashRange(double distanceSquared, double range) {
        return V3TargetingRules.inRange(distanceSquared, range);
    }

    /** Same target only; every accepted chain step renews the continuation window. */
    static boolean crossAllowed(boolean traveling, int windowTicksLeft, boolean crossed, UUID dashed, UUID current) {
        return dashed != null && dashed.equals(current) && (traveling || windowTicksLeft > 0);
    }

    public static boolean start(ServerPlayer player, LivingEntity target, int now) {
        return start(player, target, V3Direction.NONE, now);
    }

    public static boolean start(ServerPlayer player, LivingEntity target, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        V3Config.Values config = V3Config.get();
        if (fighter == null || target == null || fighter.state != V3State.IDLE || now < fighter.dashReadyTick
                || V3Heavy.stunned(player)
                || !inDashRange(player.distanceToSqr(target), config.dragonDashRange())) return false;
        V2ChargedArc.cancel(player);
        if (!V3Motion.acquire(player, V3Motion.Owner.APPROACH, fighter.session())) return false;
        fighter.travelArrive = ARRIVE;
        if (!V3Travel.start(player, target, Vec3.ZERO, config.dragonDashSpeed(), now)) {
            V3Motion.release(player);
            return false;
        }
        fighter.state = V3State.TRAVEL;
        fighter.dashTarget = target.getUUID();
        fighter.dashCrossed = false;
        fighter.dashDirection = direction;
        fighter.window = V3Window.DASH_CROSS;
        fighter.windowTarget = fighter.dashTarget;
        fighter.windowTicksLeft = fighter.windowTicksTotal = config.dragonDashFollowWindowTicks();
        fighter.dashReadyTick = (long) now + COOLDOWN_TICKS;
        CombatFx.cue(player.serverLevel(), player.position(), CombatFxKind.DASH_LAUNCH, 1.0f);
        V3CombatServer.syncState(player);
        V3DashCamera.stop(player);
        return true;
    }

    public static void tick(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        if (fighter.travelTarget != null) {
            if (fighter.state != V3State.TRAVEL) {
                // Lock loss or a session change already reset the state; give movement back.
                end(player, fighter);
                return;
            }
            switch (V3Travel.tick(player, now)) {
                case ARRIVED -> arrived(player, now);
                case REFUSED -> {
                    // The flight failed; the fighter keeps its lock, only the chain state is reset.
                    end(player, fighter);
                    fighter.state = V3State.IDLE;
                    fighter.dashTarget = null;
                    fighter.window = V3Window.NONE;
                    fighter.windowTarget = null;
                    fighter.windowTicksLeft = fighter.windowTicksTotal = 0;
                    V3DashCamera.stop(player);
                    V3CombatServer.syncState(player);
                }
                case MOVING -> { }
            }
        } else if (fighter.motion.heldBy(V3Motion.Owner.APPROACH, fighter.session()) || staleLease(fighter)) {
            V3Motion.release(player);
        } else if (fighter.windowTicksLeft > 0 && --fighter.windowTicksLeft == 0) {
            fighter.windowTicksTotal = 0;
            fighter.window = V3Window.NONE;
            fighter.windowTarget = null;
            fighter.dashTarget = null;
            V3DashCamera.stop(player);
            V3CombatServer.syncState(player);
        }
    }

    private static boolean staleLease(V3Fighter fighter) {
        return fighter.motion.owner() == V3Motion.Owner.APPROACH;
    }

    private static void end(ServerPlayer player, V3Fighter fighter) {
        V3Travel.stop(player);
        V3Motion.release(player);
    }

    /** First N arrived: the one strong hit of the chain. The camera returns to the player here. */
    public static void arrived(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        UUID session = fighter.session();
        end(player, fighter);
        fighter.state = V3State.IDLE;
        LivingEntity target = V3Targeting.resolve(player);
        fighter.window = V3Window.NONE;
        fighter.windowTarget = null;
        fighter.windowTicksLeft = fighter.windowTicksTotal = 0;
        V3DashCamera.stop(player);
        if (fighter.dashTarget != null && target != null && fighter.dashTarget.equals(target.getUUID())) {
            hit(player, target, fighter.dashDirection, now);
            // The reposition chain is available after the arrival whether or not the hit was
            // accepted (guarded, i-framed): the player can still get behind the target.
            if (V3FighterStore.peek(player) == fighter && session.equals(fighter.session())
                    && fighter.state == V3State.IDLE && fighter.motion.owner() == null
                    && V3Targeting.resolve(player) == target) openFollowWindow(fighter, target);
        }
        V3CombatServer.syncState(player);
    }

    public static boolean cross(ServerPlayer player, int now) {
        return cross(player, V3Direction.NONE, now);
    }

    /** A follow cannot tear down movement belonging to another action or session. */
    static boolean followMotionAllowed(V3State state, boolean hasTravel, V3Motion.Lease lease, UUID session) {
        return state == V3State.IDLE ? !hasTravel && lease.owner() == null
                : state == V3State.TRAVEL && hasTravel && lease.heldBy(V3Motion.Owner.APPROACH, session);
    }

    /**
     * Follow press (second N onward): knockback strike (≥ {@code v3.dragonDashLaunchDistance})
     * then teleport behind while the target is still airborne. W/S steer up/down launch;
     * LMB/RMB remain free between presses for strike combos.
     *
     * @return true when the fighter was repositioned; false leaves the chain available
     */
    public static boolean cross(ServerPlayer player, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || now < fighter.dashFollowReadyTick || V3Heavy.stunned(player)
                || !followMotionAllowed(fighter.state, fighter.travelTarget != null,
                        fighter.motion, fighter.session())) return false;
        boolean traveling = fighter.travelTarget != null && fighter.state == V3State.TRAVEL;
        LivingEntity target = V3Targeting.resolve(player);
        int crossWindow = fighter.window == V3Window.DASH_CROSS ? fighter.windowTicksLeft : 0;
        if (target == null || !crossAllowed(traveling, crossWindow, fighter.dashCrossed,
                fighter.dashTarget, target.getUUID())) return false;
        V3Config.Values config = V3Config.get();
        UUID session = fighter.session();
        // Hit + launch first so the victim is airborne, then snap behind their back.
        followStrike(player, target, direction, now);
        target = V3Targeting.resolve(player);
        if (target == null || V3FighterStore.peek(player) != fighter || !session.equals(fighter.session())) {
            V3CombatServer.syncState(player);
            return true;
        }
        // Landing uses the live (possibly flying) target position so W/S chains stay behind them.
        Vec3 landing = followLanding(player, target, config.dragonDashFollowDistance(), config.dragonDashRange());
        if (landing == null || !V3ChunkWindow.update(player, landing)) {
            // Strike already spent; still renew the window so the chain can continue.
            if (V3Targeting.resolve(player) == target) openFollowWindow(fighter, target);
            fighter.dashFollowReadyTick = (long) now + config.dragonDashFollowCooldownTicks();
            V3CombatServer.syncState(player);
            return true;
        }
        end(player, fighter);
        fighter.state = V3State.IDLE;
        fighter.dashCrossed = true;
        V3DashCamera.stop(player);
        float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - landing.z, target.getX() - landing.x)) - 90);
        player.connection.teleport(landing.x, landing.y, landing.z, yaw, 0);
        if (V3FighterStore.peek(player) != fighter || !session.equals(fighter.session())
                || fighter.state != V3State.IDLE || fighter.motion.owner() != null) return true;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        fighter.dashFollowReadyTick = (long) now + config.dragonDashFollowCooldownTicks();
        teleportCue(player);
        if (V3FighterStore.peek(player) == fighter && session.equals(fighter.session())
                && fighter.state == V3State.IDLE && fighter.motion.owner() == null
                && V3Targeting.resolve(player) == target) {
            openFollowWindow(fighter, target);
        }
        V3CombatServer.syncState(player);
        return true;
    }

    /** Follow-N knockback: same W=up punch / S=down kick launch as first-N arrival. */
    private static void followStrike(ServerPlayer player, LivingEntity target, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null || !target.isAlive()) return;
        // Snap into melee reach if needed so the follow press still connects while they fly.
        if (!V3Heavy.inReach(player, target)) {
            Vec3 near = followLanding(player, target, Math.min(2.2, V3Config.get().dragonDashFollowDistance()),
                    V3Config.get().dragonDashRange());
            if (near != null && V3ChunkWindow.update(player, near)) {
                float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - near.z, target.getX() - near.x)) - 90);
                player.connection.teleport(near.x, near.y, near.z, yaw, 0);
            }
        }
        if (!V3Heavy.inReach(player, target)) return;
        hit(player, target, direction, now);
    }

    private static void openFollowWindow(V3Fighter fighter, LivingEntity target) {
        fighter.dashTarget = target.getUUID();
        fighter.window = V3Window.DASH_CROSS;
        fighter.windowTarget = target.getUUID();
        fighter.windowTicksLeft = fighter.windowTicksTotal = V3Config.get().dragonDashFollowWindowTicks();
    }

    /** Body-relative behind (spine side). Kept for tests / callers that need facing-relative geometry. */
    static Vec3 behind(Vec3 targetPosition, float targetBodyYawDegrees, double gap) {
        double yaw = Math.toRadians(targetBodyYawDegrees);
        // Minecraft forward for a yaw is (-sin, 0, cos); behind is the opposite.
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        return targetPosition.add(back.scale(gap));
    }

    /**
     * Owner 2026-03-22: follow N lands on the far side from the attacker — past the target,
     * opposite where you started — so you swap through them instead of appearing on their face.
     */
    static Vec3 farSideFromAttacker(Vec3 attackerPosition, Vec3 targetPosition, double gap) {
        Vec3 away = targetPosition.subtract(attackerPosition).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-6) away = new Vec3(0, 0, 1);
        else away = away.normalize();
        return new Vec3(targetPosition.x, targetPosition.y, targetPosition.z).add(away.scale(gap));
    }

    /**
     * Follow landing: far side from the attacker at the target's height. A blocked spot refuses
     * the press without flipping to the near/front side.
     */
    static Vec3 followLanding(ServerPlayer player, LivingEntity target, double gap, double range) {
        if (player.level() != target.level() || !target.isAlive() || player.distanceTo(target) > range) return null;
        double spaced = Math.max(gap, (player.getBbWidth() + target.getBbWidth()) * 0.5 + 0.3);
        Vec3 landing = farSideFromAttacker(player.position(), target.position(), spaced);
        return V3ChunkWindow.update(player, landing) && open(player, landing) ? landing : null;
    }

    private static boolean open(ServerPlayer player, Vec3 landing) {
        var box = player.getBoundingBox().move(landing.subtract(player.position()));
        return player.level().getWorldBorder().isWithinBounds(box) && Bt3Landing.isSpotOpen(player, landing);
    }

    /** DragonMineZ's own short teleport sound ({@code DashHandler}), gated by {@code v3.attackSounds}. */
    private static void teleportCue(ServerPlayer player) {
        if (!V3Config.get().attackSounds()) return;
        try {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.dragonminez.common.init.MainSounds.TP_SHORT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        } catch (RuntimeException | LinkageError unavailable) {
            // Presentation only.
        }
    }

    static Vec3 launchDirection(V3Direction direction, Vec3 away) {
        if (direction == V3Direction.FORWARD || direction == V3Direction.UP) return new Vec3(0, 1, 0);
        if (direction == V3Direction.BACK) return new Vec3(0, -1, 0);
        Vec3 horizontal = away.multiply(1, 0, 1);
        return horizontal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : horizontal.normalize();
    }

    /** The strong hit belongs only to first-N arrival. */
    private static void hit(ServerPlayer player, LivingEntity target, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || !V3Heavy.inReach(player, target)) return;
        UUID session = fighter.session();
        fighter.dashFollowReadyTick = (long) now + V3Config.get().dragonDashFollowCooldownTicks();
        String pose = direction == V3Direction.BACK ? "combat.xeno_bt3_v3_dash_slam"
                : direction == V3Direction.FORWARD ? "combat.xeno_uppercut_right_v4"
                : "combat.xeno_heavy_finish_v4";
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(), pose, false, 1f), player);
        } catch (RuntimeException | LinkageError unavailable) {
            // Presentation failure does not bypass damage admission or cancel the action.
        }
        float accepted = V3Heavy.strike(player, target, 2.4f);
        if (accepted > 0f) V3AttackSounds.heavyHit(player, target);
        else V3AttackSounds.contact(player, target);
        // Damage listeners may cancel the lock, rotate the session or protect/remove the victim.
        if (!(accepted > 0) || !target.isAlive() || V3FighterStore.peek(player) != fighter
                || !session.equals(fighter.session()) || V3Targeting.resolve(player) != target
                || !V3CombatServer.canContinueTechnique(player, session, target.getUUID())
                || fighter.state != V3State.IDLE || fighter.motion.owner() != null
                || !CombatKnockback.canKnockBack(target)) return;
        Vec3 away = target.position().subtract(player.position());
        Vec3 launch = launchDirection(direction, away);
        V2ChargedArc.start(target, launch, now, V3Config.get().dragonDashLaunchDistance(),
                direction == V3Direction.FORWARD || direction == V3Direction.BACK ? 0 : 3);
        CombatFx.impact(player.serverLevel(), player, target, launch, CombatFx.Weight.HEAVY);
    }

    /** Disconnect or shutdown: never leave a player saved without gravity. */
    public static void abort(ServerPlayer player) {
        V3DashCamera.stop(player);
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter != null) end(player, fighter);
    }
}
