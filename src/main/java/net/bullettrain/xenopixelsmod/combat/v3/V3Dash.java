package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dragon Dash (owner continuation update 2026-10-12).
 *
 * <ul>
 *   <li>First N: fly at the locked target with DragonMineZ's flight state and server velocity
 *       ({@link V3Travel}), then land behind the target at {@code v3.dragonDashFollowDistance}.
 *       Arrival opens a recoverable victim hold.</li>
 *   <li>Second, third, ... N: teleport to the far side from you (past the target). LMB/RMB stay
 *       free between presses. Successful follow presses attempt one native light hit.</li>
 *   <li>No cinematic camera for the dash chain; that belongs to the Strike attacks.</li>
 * </ul>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid=net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class V3Dash {
    private static final ThreadLocal<DashContact> CONTACT=new ThreadLocal<>();
    private static final class DashContact {
        final ServerPlayer player;final LivingEntity target;float damage;
        DashContact(ServerPlayer player,LivingEntity target){this.player=player;this.target=target;}
    }
    /** Admission alone cannot authorize launch: only finite positive damage to the exact victim. */
    static double acceptedLaunchDistance(boolean admitted,float acceptedDamage,double configured){
        return admitted && Float.isFinite(acceptedDamage) && acceptedDamage>0 ? Math.clamp(configured,4,64):0;
    }
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDashDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
        DashContact c=CONTACT.get();
        if(c!=null && event.getEntity()==c.target && event.getSource().getEntity()==c.player
                && Float.isFinite(event.getNewDamage()) && event.getNewDamage()>0)c.damage=Math.max(c.damage,event.getNewDamage());
    }
    private static void hitAndLaunch(ServerPlayer player,LivingEntity target,int now,UUID session,V3Config.Values config,V3Direction direction){
        DashContact outer=CONTACT.get(),c=new DashContact(player,target);CONTACT.set(c);
        boolean admitted;
        try{admitted=V3Melee.tap(player,target,now);}
        finally{if(outer==null)CONTACT.remove();else CONTACT.set(outer);}
        double distance=acceptedLaunchDistance(admitted,c.damage,config.dragonDashLaunchDistance());
        if(distance<=0 || !target.isAlive() || target.isRemoved() || target.level()!=player.level()
                || !session.equals(V3CombatServer.session(player)) || V3Targeting.resolve(player)!=target
                || !net.bullettrain.xenopixelsmod.combat.CombatKnockback.canKnockBack(target))return;
        Vec3 bearing=target.position().subtract(player.position()).multiply(1,0,1);
        if(bearing.lengthSqr()<1e-6)bearing=player.getLookAngle().multiply(1,0,1);
        if(bearing.lengthSqr()<1e-6)bearing=new Vec3(0,0,1);
        Vec3 launchDir=bearing.normalize();
        if(direction==V3Direction.UP) launchDir=launchDir.add(0,1.2,0).normalize();
        else if(direction==V3Direction.DOWN) launchDir=launchDir.subtract(0,0.6,0).normalize();
        V2ChargedArc.start(target,launchDir,now,distance,3);
    }

    /** Default continuation window; the live value is {@code v3.dragonDashFollowWindowTicks}. */
    static final int CONTINUATION_TICKS = V3Config.Values.DEFAULT_DASH_FOLLOW_WINDOW;
    /** Plan balancing values, not BT3 constants. */
    static final double ARRIVE = 2.4;
    static final int COOLDOWN_TICKS = 40;

    private V3Dash() {}

    static boolean inDashRange(double distanceSquared, double range) {
        return V3TargetingRules.inRange(distanceSquared, range);
    }

    /** W/S lift the rush above/below the lock target while the camera stays out of it. */
    static Vec3 travelOffset(V3Direction direction) {
        if (direction == V3Direction.UP) return new Vec3(0, 6, 0);
        if (direction == V3Direction.DOWN) return new Vec3(0, -3, 0);
        return Vec3.ZERO;
    }

    /** Same target only; every accepted chain step renews the continuation window. */
    static boolean crossAllowed(boolean traveling, int windowTicksLeft, boolean crossed, UUID dashed, UUID current) {
        return dashed != null && dashed.equals(current) && (traveling || windowTicksLeft > 0);
    }

    public static boolean start(ServerPlayer player, LivingEntity target, int now) {
        return start(player, target, V3Direction.NONE, now);
    }

    static boolean preferFollow(V3State state, V3Window window, int ticksLeft, boolean traveling) {
        return window == V3Window.DASH_CROSS && (state == V3State.TRAVEL && traveling
                || state == V3State.IDLE && ticksLeft > 0);
    }

    /** A delayed client snapshot cannot turn a second N into a refused new approach. */
    public static boolean press(ServerPlayer player, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return false;
        return preferFollow(fighter.state, fighter.window, fighter.windowTicksLeft, fighter.travelTarget != null)
                ? cross(player, direction, now) : start(player, V3Targeting.resolve(player), direction, now);
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
        if (!V3Travel.start(player, target, travelOffset(direction), config.dragonDashSpeed(), now)) {
            V3Motion.release(player);
            return false;
        }
        freezeTarget(player, target, now);
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

    /**
     * First N arrived: reposition behind the target. The chain lands no hit; the window stays
     * open so the next press (or the player's own clicks) can continue.
     */
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
            snapBehind(player, target);
            // The reposition chain is available after the arrival whether or not the snap found
            // room: the fighter already stands within reach of the target it flew to.
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
     * Follow press (second N onward): teleport to the far side from the attacker — past the
     * target. A successful move attempts one native light hit; normal cooldown and guards apply.
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
        boolean moved = snapBehind(player, target);
        target = V3Targeting.resolve(player);
        if (target == null || V3FighterStore.peek(player) != fighter || !session.equals(fighter.session())) {
            V3CombatServer.syncState(player);
            return true;
        }
        if (moved) {
            end(player, fighter);
            fighter.state = V3State.IDLE;
            fighter.dashCrossed = true;
            freezeTarget(player, target, now);
            hitAndLaunch(player,target,now,session,config,direction);
            V3DashCamera.stop(player);
        }
        // Still renew the window so the chain can continue even when the snap found no room.
        if (V3Targeting.resolve(player) == target) openFollowWindow(fighter, target);
        fighter.dashFollowReadyTick = (long) now + config.dragonDashFollowCooldownTicks();
        V3CombatServer.syncState(player);
        return true;
    }

    /**
     * Teleports to the far side from the attacker at the target's height, facing the target.
     * A blocked spot leaves the fighter where it is and returns false.
     */
    private static boolean snapBehind(ServerPlayer player, LivingEntity target) {
        Vec3 landing = followLanding(player, target, V3Config.get().dragonDashFollowDistance(),
                V3Config.get().dragonDashRange());
        if (landing == null || !V3ChunkWindow.update(player, landing)) return false;
        float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - landing.z, target.getX() - landing.x)) - 90);
        player.connection.teleport(landing.x, landing.y, landing.z, yaw, 0);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        teleportCue(player);
        return true;
    }



    private static void freezeTarget(ServerPlayer player, LivingEntity target, int now) {
        if (target instanceof ServerPlayer victim && CombatRecovery.dashImmune(victim, now)) return;
        // Existing holds are left intact: repeated N cannot reset the victim's recovery progress.
        int ticks = Math.min(V3Config.get().strikeTargetFreezeTicks(), V3Config.get().dragonDashFollowWindowTicks());
        net.bullettrain.xenopixelsmod.combat.v3.technique.V3PostAttackFreeze.start(
                player, target, "dragon_dash", now, ticks, RecoverySnapshot.Kind.DASH);
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

    /** Disconnect or shutdown: never leave a player saved without gravity. */
    public static void abort(ServerPlayer player) {
        V3DashCamera.stop(player);
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter != null) end(player, fighter);
    }
}
