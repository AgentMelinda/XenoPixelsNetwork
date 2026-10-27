package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** One server-tick movement owner for V9 native ground pursuit. */
public final class NpcNativePursuit {
    /** Native's {@code DashActive} window: how long a dash keeps steering the NPC. */
    private static final int DASH_ACTIVE_TICKS = 15;
    /** Native's flight-dash clamp: {@code min(3.8, max(0.55, base + cruise * 2.5))}. */
    private static final double FLIGHT_MAX_IMPULSE = 3.8;
    private static final double FLIGHT_MIN_IMPULSE = 0.55;
    /** DMZ's own stored flight speed, written on its player path. Absent on an NPC, which reads 0. */
    private static final String DMZ_SERVER_SPEED = "dmz_server_speed";
    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private static final class State {
        final NpcPursuitCurve curve = new NpcPursuitCurve();
        int issuedTick = Integer.MIN_VALUE, dashUntil, dashReady;
        Object level;
        java.util.UUID target;
    }
    private NpcNativePursuit() {}

    public static boolean owns(LivingEntity npc, NpcCombatProfile profile) {
        return npc instanceof XenoNpcEntity && profile != null && profile.combatBrain
                && profile.brainVersion == NpcCombatBrainVersion.V9;
    }

    public static void reset(LivingEntity npc) {
        if (STATES.remove(npc) == null) return;
        if (npc instanceof XenoNpcEntity nativeNpc) {
            nativeNpc.getNavigation().stop();
            NpcSprintSkill.apply(nativeNpc, null, false);
        }
        NpcMovementOwner.release(npc, NpcMovementOwner.Claim.COMBAT);
    }

    /**
     * Yield the velocity for a moment without giving the NPC away.
     *
     * <p>Each state that reaches here is a reason the pursuit must not steer <em>this</em> tick: a
     * knockback still playing out, a post-attack freeze, a charge in flight, a transformation hold, a
     * saga combo mid-animation, a ki projectile that must not have its owner shoved. They all used to
     * leave through the same {@code reset} as walking out of combat, and {@code reset} releases the
     * COMBAT claim. {@code XenoNpcBehaviour.tickLeash} has no combat check of its own - the claim is
     * the only thing telling it a fight is in progress - so for the ten ticks of a knockback the leash
     * could claim the NPC and start walking it home, and recovery then claimed it back and undid the
     * pull. That is the same shudder this coordinator was written to remove, arriving on a new path.
     *
     * <p>So the claim stays held: an NPC that cannot answer for itself should not be handed to the
     * lowest-priority mover for the duration.
     */
    private static void hold(XenoNpcEntity npc) {
        npc.getNavigation().stop();
        NpcSprintSkill.apply(npc, null, false);
        net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour
                .claimMovement(npc, NpcMovementOwner.Claim.COMBAT);
    }

    /** Navigation follows a curved waypoint; collision/path finding remain Minecraft's. */
    public static void tick(LivingEntity npc, NpcCombatProfile profile, LivingEntity target) {
        if (!owns(npc, profile) || npc.level().isClientSide()) return;
        XenoNpcEntity mob = (XenoNpcEntity) npc;
        if (target == null || target == npc || !target.isAlive() || !npc.isAlive()
                || target.level() != npc.level()) {
            reset(npc);
            return;
        }
        if (NpcKnockbackGrace.active(npc)
                || net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.isControlledVictim(npc.getUUID())
                || net.bullettrain.xenopixelsmod.combat.v3.technique.V3PostAttackFreeze.isFrozen(npc)
                || NpcKiAttackDispatcher.ownsLiveProjectile(npc)
                || NpcKiAttackDispatcher.isOwnerClashing(npc)
                || NpcCombatBrain.isActing(npc)
                || NpcChargeMoves.isCharging(npc)
                || NpcTransformSystem.isHolding(npc.getUUID())
                || net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain.hasActiveCombo(npc)) {
            hold(mob);
            return;
        }
        // Through XenoNpcBehaviour, not NpcMovementOwner directly: the arbitration switch is what
        // lets an operator turn this whole coordinator off, and a path that claimed around it kept
        // arbitrating - and refusing movement - with npcMovementArbitration set to false.
        if (!net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour
                .claimMovement(npc, NpcMovementOwner.Claim.COMBAT)) {
            STATES.remove(npc);
            NpcSprintSkill.apply(mob, null, false);
            return;
        }
        State state = STATES.computeIfAbsent(npc, ignored -> new State());
        if (state.level != npc.level() || !target.getUUID().equals(state.target)) {
            state = new State();
            state.level = npc.level();
            state.target = target.getUUID();
            STATES.put(npc, state);
        }
        if (state.issuedTick == npc.tickCount) return;
        state.issuedTick = npc.tickCount;
        double distance = npc.distanceTo(target);
        double reach = NpcCombatRanges.meleeReach(npc, target);
        boolean inReach = NpcCombatRanges.withinMelee(npc, target);
        double side = state.curve.update(target.getUUID(), npc.tickCount, distance, reach, inReach,
                npc.getRandom().nextBoolean() ? 1 : -1);
        boolean chasing = state.curve.chasing();
        NpcSprintSkill.apply(mob, profile, chasing);
        if (!chasing) {
            mob.getNavigation().stop();
            state.dashUntil = 0;
            return;
        }
        if (npc.tickCount < state.dashUntil) return;
        Vec3 delta = target.position().subtract(npc.position());
        Vec3 flat = delta.multiply(1, 0, 1).normalize();
        double strength = Math.min(1.5, Math.max(0, distance - reach - 0.5) * 0.22);
        if (strength > 0 && (npc.horizontalCollision || !NpcMovementOwner.progressing(npc))) {
            // Something between here and the target is winning: either the NPC was shoved off it
            // this tick, or it has spent three re-issues moving less than half a block. Turn the arc
            // the other way and measure the next attempt from here, rather than re-issuing the same
            // waypoint into the same geometry.
            side = state.curve.switchSide(npc.tickCount);
            NpcMovementOwner.clearProgress(npc);
        }
        double bend = strength * side;
        Vec3 waypoint = target.position().add(-flat.z * bend, 0, flat.x * bend);
        if (!mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.2)) {
            // Preserve ledge descent and obstructed-terrain fallback, without recursively entering this owner.
            NpcLedgeApproach.applyDirect(npc, target, 1.2);
        }
    }

    /**
     * DMZ 2.2's normal dash, replicated for a native NPC: an impulse, independent of the Xeno chase
     * teleport.
     *
     * <p>Every number here is read out of the pinned jar's {@code DashHandler} instead of estimated,
     * because this is the one move players judge their own NPC against:
     * <ul>
     *   <li>impulse {@code 4 * (MOVEMENT_SPEED / 0.1) * 0.3} along a flat vector;
     *   <li>lift <em>added</em>, not set - {@code 0.35} on the ground, {@code 0.2} in the air, so a
     *       dash taken off a jump works. The old {@code onGround} requirement was not in the native
     *       path at all; it only chose the lift value;
     *   <li>a flying NPC ({@code profile.flySkillOn}, the flag that already drives DMZ's fly pose and
     *       CustomNPCs' flying navigator) takes native's <em>flight</em> dash instead: impulse
     *       {@code min(3.8, max(0.55, base + cruise * 2.5))} where {@code base} is the same movement
     *       term and {@code cruise} is the larger of the current velocity and DMZ's stored flight
     *       speed; the vertical component is <em>zeroed</em> rather than lifted, and fall distance is
     *       cleared, so a flight dash holds altitude instead of hopping;
     *   <li>the cost is {@code ceil(baselineFormDrain * 0.12)} - 10 at the shipped default drain of 80
     *       - paid as <em>stamina</em> on the ground and as <em>ki</em> in the air, because native
     *       routes the two branches to different resources even though
     *       {@code DashHandler.getFlyDashKiCost()} computes the identical number. Charging ground
     *       dashes to ki made an NPC's dash fail on a fighter that had plenty of stamina left and
     *       succeed on one with no ki;
     *   <li>the cooldown is {@code dashCooldownSeconds * 20} - 80 ticks by default, not the 30 this
     *       used to hand out - and the dash owns the NPC's movement for the native 15-tick
     *       {@code DashActive} window, so the impulse is not steered away on the very next tick.
     * </ul>
     *
     * <p>Divergences are deliberate, and all of them are because this entity is not a player: the
     * ground impulse is clamped to the distance short of melee reach, so an NPC does not run through
     * the fighter it dashed at (the flight branch keeps native's unclamped cap, since 3.8 is already
     * the limit); the direction comes from the target rather than from movement input and look yaw,
     * because an NPC has no input; and {@code hasImpulse} is set, because {@code ServerEntity} only
     * syncs velocity for a non-player when that flag is on, where a {@code ServerPlayer} syncs through
     * its own path. A dash into a wall is refused rather than paid for, since native's
     * {@code tryStepUpDuringDash} is not replicated and the NPC would lose the dash and the stamina.
     * Native also refuses a flying dash outright when {@code Status.getFlightMode()} is 1; that mode
     * is a per-player flight control an NPC never sets, so there is no equivalent to gate here.
     */
    public static boolean dash(LivingEntity npc, NpcCombatProfile profile, LivingEntity target) {
        if (!owns(npc, profile) || target == null || !target.isAlive() || target.level() != npc.level()
                || npc.level().isClientSide() || NpcKnockbackGrace.active(npc)
                || NpcCombatRanges.withinMelee(npc, target)) return false;
        State state = STATES.get(npc);
        if (state == null || npc.tickCount < state.dashReady
                || !net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour
                        .claimMovement(npc, NpcMovementOwner.Claim.COMBAT)) return false;
        Vec3 direction = target.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < 1e-6 || npc.horizontalCollision) return false;
        double base = 12 * npc.getAttributeValue(Attributes.MOVEMENT_SPEED);
        boolean flying = profile.flySkillOn;
        double impulse;
        if (flying) {
            double cruise = Math.max(npc.getDeltaMovement().length(),
                    npc.getPersistentData().getDouble(DMZ_SERVER_SPEED));
            impulse = Math.min(FLIGHT_MAX_IMPULSE, Math.max(FLIGHT_MIN_IMPULSE, base + cruise * 2.5));
        } else {
            impulse = Math.min(base, Math.max(0, npc.distanceTo(target) - NpcCombatRanges.meleeReach(npc, target)));
        }
        if (!Double.isFinite(impulse) || impulse <= 0) return false;
        if (!(flying ? NpcResources.spendEnergy(npc, profile, dashCost())
                : NpcResources.spendStamina(npc, profile, dashCost()))) return false;
        ((XenoNpcEntity) npc).getNavigation().stop();
        if (flying) {
            Vec3 previous = npc.getDeltaMovement();
            npc.setDeltaMovement(previous.x + direction.x * impulse, 0, previous.z + direction.z * impulse);
            npc.fallDistance = 0;
        } else {
            npc.setDeltaMovement(npc.getDeltaMovement().add(direction.scale(impulse))
                    .add(0, npc.onGround() ? 0.35 : 0.2, 0));
        }
        npc.hurtMarked = true;
        npc.hasImpulse = true;
        if (npc.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    npc.getX(), npc.getY() + 0.5, npc.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, npc, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f + npc.getRandom().nextFloat() * 0.3f);
        }
        state.dashUntil = npc.tickCount + DASH_ACTIVE_TICKS;
        state.dashReady = npc.tickCount + cooldownTicks();
        return true;
    }

    /** {@code ceil(baselineFormDrain * 0.12)} from DMZ's own combat config, 10 at the shipped default. */
    private static double dashCost() {
        double drain = 80;
        try { drain = com.dragonminez.common.config.ConfigManager.getCombatConfig().getBaselineFormDrain(); }
        catch (Throwable ignored) {}
        return Math.max(1, Math.ceil(drain * 0.12));
    }

    /** {@code dashCooldownSeconds * 20}, 80 ticks when DMZ's config is not available. */
    private static int cooldownTicks() {
        double seconds = 4;
        try { seconds = com.dragonminez.common.config.ConfigManager.getCombatConfig().getDashCooldownSeconds(); }
        catch (Throwable ignored) {}
        return (int) Math.max(1, Math.round(seconds * 20));
    }
}
