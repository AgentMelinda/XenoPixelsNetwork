package net.bullettrain.xenopixelsmod.combat.clone;

import net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import net.bullettrain.xenopixelsmod.compat.npc.NpcStrikeDispatcher;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The multi-form copies' own combat brain: {@code /xenomultiform ai multiform}.
 *
 * <p>A fork of {@link net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrain} rather than a reuse of
 * it, because four things that are correct for a CustomNPC are wrong for a copy, and none of them can
 * be fixed in the shared brain without changing how real NPCs fight:
 *
 * <ul>
 *   <li><b>Damage.</b> {@code NpcMeleeDamage} scales a hit from the attacker's stored profile, and
 *       {@code NpcCombatProfile.hasProfile} deliberately excludes copies so the profile lifecycle
 *       does not tick them twice. A copy driven through the shared brain therefore lands the raw
 *       {@code 1.0f} fallback instead of its owner's melee damage. This applies the owner's damage
 *       directly, the way {@link CloneCombatBridge} always has.</li>
 *   <li><b>Movement.</b> {@link XenoCloneEntity} zeroes its own velocity every server tick, so the
 *       shared brain's {@code applyVelocity} is erased before the entity moves. Every step here goes
 *       through {@link CloneCombatBridge#move}, which sets position and already steers around
 *       obstacles.</li>
 *   <li><b>Leash.</b> A copy belongs to a formation around its owner. It chases, but the owner's
 *       detect range is a hard boundary, and crossing it sends the copy home rather than letting it
 *       wander off after a target.</li>
 *   <li><b>State.</b> The shared brain keeps its per-NPC state in static maps keyed by UUID and
 *       copies are created and destroyed constantly, so that state leaks a little with every split.
 *       This brain is an instance, owned by the copy, and dies with it.</li>
 * </ul>
 *
 * <p>Everything that is not clone-specific is reused rather than copied: {@link CloneCombatPolicy}
 * decides the band, {@link NpcBrainKiRotation} supplies the approach and flight vectors,
 * {@link NpcCombatMoves}, {@link NpcKiAttackDispatcher} and {@link NpcStrikeDispatcher} perform the
 * actions, and {@link CloneCombatBridge} owns the profile, the owner's resource pools and movement.
 */
public final class MultiFormCombatBrain {

    /** Ticks between decisions. Matches the legacy clone AI's swing cadence. */
    private static final int DECISION_INTERVAL = 20;
    /** Ticks a copy leaves a target alone after giving up on hurting it. */
    private static final int ABANDON_TICKS = 60;
    /** How far a copy steps per tick while closing on foot. */
    private static final double CHASE_SPEED = 0.45;
    /** Faster while flying, matching the shared brain's air chase. */
    private static final double FLY_SPEED = NpcBrainKiRotation.FLY_SPEED * 2.0;
    /** Ki blast lifetime, in ticks; the same value the legacy clone AI fires with. */
    private static final int KI_LIFETIME = 40;

    private int cooldown;
    private int whiffStreak;
    private long abandonedUntil = Long.MIN_VALUE;
    private java.util.UUID engaged;

    /**
     * One tick of combat.
     *
     * @return true when combat owns the copy's movement this tick, so the caller must not also slide
     *         it back toward its formation slot
     */
    public boolean tick(XenoCloneEntity clone, ServerPlayer owner, LivingEntity target,
                        CloneCombatBridge bridge) {
        if (cooldown > 0) cooldown--;
        if (!CloneCombatPolicy.hasLivingLock(target != null, target != null && target.isAlive())) {
            stand(clone, bridge);
            return false;
        }

        boolean valid = CloneCombatBridge.validTarget(owner, target)
                && XenoCloneSystem.inDetectRange(owner, target)
                && !abandoned(clone, target);
        double ownerDistance = clone.distanceTo(owner);
        double targetDistance = clone.distanceTo(target);
        var action = CloneCombatPolicy.decide(CloneCombatBridge.active(clone), valid, ownerDistance,
                valid ? targetDistance : Double.POSITIVE_INFINITY,
                valid && clone.hasLineOfSight(target), cooldown,
                XenoServerConfig.clampedMultiFormDetectRange());
        if (action == CloneCombatPolicy.Action.FORMATION) {
            stand(clone, bridge);
            return false;
        }

        if (!target.getUUID().equals(engaged)) {
            bridge.cancel(clone);
            engaged = target.getUUID();
            whiffStreak = 0;
            abandonedUntil = Long.MIN_VALUE;
        }

        NpcCombatProfile profile = bridge.profile(clone);
        NpcKiAim.applyPose(clone, target);

        switch (action) {
            case MELEE -> melee(clone, owner, target, profile, bridge);
            case RANGED -> ranged(clone, target, profile);
            default -> { }
        }

        if (CloneCombatPolicy.shouldAbandon(whiffStreak)) {
            abandonedUntil = clone.level().getGameTime() + ABANDON_TICKS;
            whiffStreak = 0;
            stand(clone, bridge);
            return false;
        }
        // Everything that is not a punch keeps closing, so a copy does not stand still between
        // swings or while recovering. Exactly one move per tick: stepping here as well as in the
        // switch above would have moved an approaching copy at double speed.
        if (action != CloneCombatPolicy.Action.MELEE) {
            close(clone, target, targetDistance, profile);
        }
        return true;
    }

    /**
     * A punch, paid for out of the owner's stamina and dealing the owner's damage.
     *
     * <p>Deliberately not routed through {@code NpcMeleeDamage.hit}: that scales from a stored
     * profile a copy does not have, so it would land 1.0 damage and animate nothing.
     */
    private void melee(XenoCloneEntity clone, ServerPlayer owner, LivingEntity target,
                       NpcCombatProfile profile, CloneCombatBridge bridge) {
        for (String id : profile.techniques) {
            var owned = CloneCombatBridge.strike(clone, id);
            if (owned != null && NpcStrikeDispatcher.fire(id, clone, profile, target)) {
                cooldown = Math.max(DECISION_INTERVAL, owned.getActualCastTime() + DECISION_INTERVAL);
                NpcDmzAnim.play(clone,
                        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_RIGHT);
                // fire() only queued the impact; until it lands this is a miss, so a copy that can
                // never hurt its target still gives up instead of swinging forever.
                whiffStreak = CloneCombatPolicy.noteSwing(whiffStreak, false);
                return;
            }
        }

        cooldown = DECISION_INTERVAL;
        if (!CloneCombatBridge.spend(clone, 0, CloneCombatBridge.meleeStaminaCost(clone))) {
            // Out of stamina still counts against the abandon budget. Not counting it was how the
            // legacy path could sit in melee forever without ever swinging.
            whiffStreak = CloneCombatPolicy.noteSwing(whiffStreak, false);
            return;
        }
        clone.swing(InteractionHand.MAIN_HAND, true);
        NpcDmzAnim.play(clone,
                net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_RIGHT);
        whiffStreak = CloneCombatPolicy.noteSwing(whiffStreak,
                target.hurt(clone.damageSources().mobAttack(clone), profile.meleeDamage()));
    }

    /** A ki blast, paid for out of the owner's energy. */
    private void ranged(XenoCloneEntity clone, LivingEntity target, NpcCombatProfile profile) {
        cooldown = 30;
        float damage = profile.kiDamage();
        if (damage <= 0) return;
        if (!CloneCombatBridge.spend(clone, Math.max(1.0, damage * 0.1), 0)) return;
        NpcKiAttackDispatcher.fireKiBlast(clone, profile, KI_LIFETIME, target, 0);
    }

    /**
     * Close the distance, flying when the target is clearly above.
     *
     * <p>The vector comes from the shared {@link NpcBrainKiRotation} helpers so a copy moves the way
     * a brain NPC does, but it is applied as a destination rather than a velocity — a copy's velocity
     * is wiped at the top of every tick.
     */
    private void close(XenoCloneEntity clone, LivingEntity target, double distance,
                       NpcCombatProfile profile) {
        double dx = target.getX() - clone.getX();
        double dy = target.getY() - clone.getY();
        double dz = target.getZ() - clone.getZ();
        boolean climbing = NpcBrainKiRotation.needsClimb(dy);
        double[] step = climbing
                ? NpcBrainKiRotation.airChaseVelocity(dx, dy, dz, FLY_SPEED)
                : NpcBrainKiRotation.approachVelocity(dx, dy, dz, CHASE_SPEED);
        double speed = climbing ? FLY_SPEED : CHASE_SPEED;
        CloneCombatBridge.move(clone, clone.position().add(step[0], step[1], step[2]), speed);

        if (XenoServerConfig.multiFormVanish && distance > CloneCombatPolicy.MELEE_RANGE
                && profile.allowBrainAction("vanish", clone.getRandom())) {
            NpcCombatMoves.vanish(clone, target, 0);
        }
    }

    /** Stand combat down without touching the caller's formation move. */
    private void stand(XenoCloneEntity clone, CloneCombatBridge bridge) {
        bridge.cancel(clone);
        engaged = null;
    }

    /** True while this copy is sitting out the window after giving up on {@code target}. */
    private boolean abandoned(XenoCloneEntity clone, LivingEntity target) {
        if (abandonedUntil == Long.MIN_VALUE) return false;
        long now = clone.level().getGameTime();
        if (now >= abandonedUntil || now < abandonedUntil - ABANDON_TICKS * 4L) {
            abandonedUntil = Long.MIN_VALUE;
            return false;
        }
        return target != null && target.getUUID().equals(engaged);
    }

    /** A landed hit clears the streak, so a real fight never abandons itself. */
    public void noteHit() {
        whiffStreak = 0;
    }

    /** Forget everything about the current fight; called when the copy is removed. */
    public void forget() {
        cooldown = 0;
        whiffStreak = 0;
        abandonedUntil = Long.MIN_VALUE;
        engaged = null;
    }
}
