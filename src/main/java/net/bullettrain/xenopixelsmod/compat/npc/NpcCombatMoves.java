package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.VanishShadeFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The BT3/DMZ combat moves, made available to NPCs.
 *
 * <p>The player versions live on {@code Bt3CombatPacket} as {@code private static} handlers typed
 * to {@code ServerPlayer} and DMZ's {@code Resources} ({@code handleVanish}, {@code handleChase},
 * {@code handleBackstep}, …), so none of them can be called for an NPC — this is the NPC-side
 * equivalent, not a wrapper. What it does reuse rather than re-derive:
 *
 * <ul>
 *   <li>{@link Bt3CombatPacket#vanishBehind(net.minecraft.world.entity.Entity, LivingEntity, int)}
 *       and {@link Bt3CombatPacket#chaseLanding} — both already take a plain {@code Entity}, so
 *       an NPC gets exactly the destination geometry a player gets;</li>
 *   <li>{@link VanishShadeFx} and {@link CombatFx} for the visuals;</li>
 *   <li>{@link NpcResources} for cost, standing in for the player path's {@code Resources}.</li>
 * </ul>
 *
 * <p>Every move is directly script-callable and is also what {@link NpcCombatBrain} drives, so
 * scripted and autonomous NPCs share one implementation and one set of gates.
 */
public final class NpcCombatMoves {
    /** Cooldown keys. Namespaced so they cannot collide with a DMZ technique id. */
    private static final String CD_VANISH = "xeno:vanish";
    private static final String CD_CHASE = "xeno:chase";
    private static final String CD_BACKSTEP = "xeno:backstep";
    private static final String CD_ZBURST = "xeno:zburst";

    /** Energy costs, mirroring the player-side config knobs for the same moves. */
    private static final double VANISH_COST = 8.0;
    private static final double CHASE_COST = 6.0;
    private static final double BACKSTEP_COST = 4.0;
    private static final double ZBURST_COST = 12.0;

    private static final int VANISH_CD_TICKS = 40;
    private static final int CHASE_CD_TICKS = 30;
    private static final int BACKSTEP_CD_TICKS = 25;
    private static final int ZBURST_CD_TICKS = 100;

    /**
     * How far a backstep carries. The player move is a client-driven hop with no single
     * configured distance ({@code backstepMaxRange} bounds how far the <em>target</em> may be,
     * not how far you travel), so the NPC version picks its own modest value.
     */
    private static final double BACKSTEP_DISTANCE = 4.0;

    /** Guard state, keyed by NPC. Purely this mod's own concept; CustomNPCs has no equivalent. */
    private static final Map<UUID, Boolean> GUARDING = new ConcurrentHashMap<>();
    /** Simple per-move cooldown clock, in server ticks. */
    private static final Map<String, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    private NpcCombatMoves() {}

    /**
     * Whether this NPC's brain may reposition itself.
     *
     * <p>Vanish, chase, backstep and teleportAbove are this mod's own - they land through
     * {@code Bt3Landing.chaseLanding}, not through anything DragonMineZ does - so they are
     * Xeno specials, and the V7 brain is the ported DMZ tree with none of them.
     *
     * <p>Only the repositioning set asks. {@code guard}, {@code kiai} and {@code zBurst} live in
     * this class too but are ports of DMZ behaviour, so they stay available in every version.
     */
    private static boolean specialsAllowed(LivingEntity npc) {
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        return profile == null || profile.allowXenoSpecial();
    }

    /** Teleports the NPC behind its target, leaving a shade behind. */
    public static boolean vanish(LivingEntity npc, LivingEntity target, int side) {
        if (!canAct(npc, target) || !ready(npc, CD_VANISH) || !specialsAllowed(npc)) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        double range = XenoServerConfig.vanishMaxRange * (profile == null ? 1.0f : profile.brainModifier("vanish"));
        if (npc.distanceTo(target) > range) {
            return false;
        }
        if (!NpcResources.spendEnergy(npc, profile, VANISH_COST)) {
            return false;
        }
        Vec3 from = npc.position();
        Vec3 dest = net.bullettrain.xenopixelsmod.combat.Bt3Landing.vanishLanding(npc, target, side);
        teleportFacing(npc, dest, target);
        VanishShadeFx.spawn(npc, from);
        if (npc.level() instanceof ServerLevel level) {
            CombatFx.cue(level, from, CombatFxKind.VANISH_CLAP, 1.0f);
        }
        mark(npc, CD_VANISH, VANISH_CD_TICKS);
        return true;
    }

    /** Closes to melee range on the approach line, landing in front of the target. */
    public static boolean chase(LivingEntity npc, LivingEntity target) {
        if (!canAct(npc, target) || !ready(npc, CD_CHASE) || !specialsAllowed(npc)) {
            return false;
        }
        double distance = npc.distanceTo(target);
        // chaseMaxRange of 0 means unlimited (see XenoServerConfig.chaseRangeUnlimited).
        if (distance < 2.5
                || (!XenoServerConfig.chaseRangeUnlimited() && distance > XenoServerConfig.chaseMaxRange)) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        if (!NpcResources.spendEnergy(npc, profile, CHASE_COST)) {
            return false;
        }
        teleportFacing(npc, net.bullettrain.xenopixelsmod.combat.Bt3Landing.chaseLanding(npc, target), target);
        mark(npc, CD_CHASE, CHASE_CD_TICKS);
        return true;
    }

    /** Hops backwards away from the target, keeping it in view. */
    public static boolean backstep(LivingEntity npc, LivingEntity target) {
        if (!canAct(npc, target) || !ready(npc, CD_BACKSTEP) || !specialsAllowed(npc)) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        if (!NpcResources.spendEnergy(npc, profile, BACKSTEP_COST)) {
            return false;
        }
        Vec3 away = npc.position().subtract(target.position());
        if (away.lengthSqr() < 1.0e-4) {
            away = npc.getLookAngle().scale(-1.0);
        }
        away = new Vec3(away.x, 0.0, away.z).normalize().scale(BACKSTEP_DISTANCE);
        teleportFacing(npc, npc.position().add(away), target);
        mark(npc, CD_BACKSTEP, BACKSTEP_CD_TICKS);
        return true;
    }

    /** Ki burst that shoves nearby attackers off. */
    public static boolean zBurst(LivingEntity npc, LivingEntity target) {
        return zBurst(npc, target, 0.8);
    }

    /** Enhanced ZBurst used by the brain Kiai special. Knockback only — never teleports the victim. */
    public static boolean kiai(LivingEntity npc, LivingEntity target) {
        return zBurst(npc, target, 1.65);
    }

    public static boolean zBurst(LivingEntity npc, LivingEntity target, double knockback) {
        if (!canAct(npc, target) || !ready(npc, CD_ZBURST)) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        if (!NpcResources.spendEnergy(npc, profile, ZBURST_COST)) {
            return false;
        }
        if (npc.level() instanceof ServerLevel level) {
            CombatFx.cue(level, npc.position().add(0.0, npc.getBbHeight() * 0.5, 0.0),
                    CombatFxKind.VANISH_CLAP, 1.0f);
        }
        Vec3 push = target.position().subtract(npc.position());
        if (push.lengthSqr() > 1.0e-4) {
            CombatKnockback.add(target, push.normalize().scale(knockback));
        }
        mark(npc, CD_ZBURST, ZBURST_CD_TICKS);
        return true;
    }

    /**
     * Moves the NPC above the target. The victim stays where they are; slam them with
     * {@link CombatKnockback} afterwards.
     */
    public static boolean teleportAbove(LivingEntity npc, LivingEntity target, double extraY) {
        if (!canAct(npc, target) || !specialsAllowed(npc)) {
            return false;
        }
        Vec3 dest = new Vec3(target.getX(), target.getY() + target.getBbHeight() + extraY, target.getZ());
        teleportFacing(npc, dest, target);
        return true;
    }

    /** Downward impulse applied to a Bone Crusher victim. Never a {@code setPos}. */
    public static Vec3 boneCrusherImpulse() {
        return new Vec3(0.0, -1.35, 0.0);
    }

    /** Toggles the NPC's guard. Read by {@code NpcMeleeDamage} to reduce incoming damage. */
    public static boolean guard(LivingEntity npc, boolean on) {
        if (npc == null) {
            return false;
        }
        if (on) {
            GUARDING.put(npc.getUUID(), Boolean.TRUE);
        } else {
            GUARDING.remove(npc.getUUID());
        }
        return true;
    }

    public static boolean isGuarding(LivingEntity npc) {
        return npc != null && GUARDING.containsKey(npc.getUUID());
    }

    public static void forget(UUID npcId) {
        if (npcId == null) {
            return;
        }
        GUARDING.remove(npcId);
        COOLDOWNS.keySet().removeIf(key -> key.startsWith(npcId.toString()));
    }

    private static boolean canAct(LivingEntity npc, LivingEntity target) {
        return npc != null && npc.isAlive() && target != null && target.isAlive() && npc != target
                && !npc.level().isClientSide()
                && !NpcTransformSystem.isHolding(npc.getUUID());
    }

    /**
     * Teleport plus a look at the target. A bare {@code teleportTo} leaves the NPC facing its
     * old heading, which reads as it blinking in backwards.
     */
    /**
     * Teleport plus a look at the target.
     *
     * <p>The single place every combat reposition lands - chase, vanish, backstep, teleportAbove -
     * which is why the movement claim is taken here rather than at each of them. A fight outranks
     * the leash, so this stops the leash walking an NPC home out of the middle of one; it yields to
     * an authored scene, which is the only thing above it.
     */
    private static void teleportFacing(LivingEntity npc, Vec3 dest, LivingEntity target) {
        net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.claimMovement(npc,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.COMBAT);
        npc.teleportTo(dest.x, dest.y, dest.z);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hurtMarked = true;
        NpcKiAim.applyPose(npc, target);
        // The navigator still holds a path to where the NPC used to be standing.
        if (npc instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        // It has just moved a long way on purpose. Any record of it failing to get somewhere is
        // about a destination that no longer applies.
        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.clearProgress(npc);
    }

    private static boolean ready(LivingEntity npc, String move) {
        Integer until = COOLDOWNS.get(key(npc, move));
        return until == null || serverTick(npc) >= until;
    }

    private static void mark(LivingEntity npc, String move, int ticks) {
        COOLDOWNS.put(key(npc, move), serverTick(npc) + ticks);
    }

    private static String key(LivingEntity npc, String move) {
        return npc.getUUID() + "|" + move;
    }

    private static int serverTick(LivingEntity npc) {
        return npc.level() instanceof ServerLevel level
                ? level.getServer().getTickCount() : npc.tickCount;
    }
}
