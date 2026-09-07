package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autonomous combat for profiled NPCs.
 *
 * <p>Opt-in per NPC through {@link NpcCombatProfile#combatBrain}, which defaults to off — every
 * existing scripted NPC therefore behaves exactly as its script says unless an author turns this
 * on. It never replaces CustomNPCs' own movement AI; it layers decisions on top, and every move
 * it makes is one a script could equally have made through {@link NpcCombatMoves} or
 * {@link NpcKiAttackDispatcher}.
 *
 * <p><b>Cost.</b> {@link NpcProfileLifecycle} calls this on a staggered slot (one NPC per id per
 * ten ticks), never every tick. Everything here early-outs before allocating when there is no
 * target, reads profiles through {@link NpcCombatProfile#readCached}, and issues no pathfinding
 * of its own — CustomNPCs' attack goal already paths, and the movement moves here are teleports.
 */
public final class NpcCombatBrain {
    /** Inside this, prefer strikes and melee pressure. */
    private static final double MELEE_BAND = 4.0;
    /** Between melee and this, prefer ki. Beyond it, close the distance first. */
    private static final double KI_BAND = 32.0;

    /** Below this fraction of max energy the NPC disengages rather than spending more. */
    private static final double LOW_ENERGY = 0.2;
    /** Below this fraction of max health the NPC will try to ascend. */
    private static final double ASCEND_HEALTH = 0.5;

    /** Ticks between two decisions for one NPC, on top of the caller's own stagger. */
    private static final int DECISION_INTERVAL = 20;

    private static final Map<UUID, Integer> NEXT_DECISION = new ConcurrentHashMap<>();

    private NpcCombatBrain() {}

    public static void forget(UUID npcId) {
        if (npcId != null) {
            NEXT_DECISION.remove(npcId);
        }
    }

    /**
     * One decision for one NPC.
     *
     * @param victim the target {@link NpcTargetKeeper} resolved this tick, or null
     */
    public static void tick(MinecraftServer server, LivingEntity npc, NpcCombatProfile profile,
                            LivingEntity victim, int serverTick) {
        if (npc == null || profile == null || !profile.combatBrain) {
            return;
        }
        // No target is the overwhelmingly common case; leave before touching anything else.
        if (victim == null || !victim.isAlive() || victim == npc) {
            return;
        }
        if (isBusy(npc)) {
            return;
        }
        Integer next = NEXT_DECISION.get(npc.getUUID());
        if (next != null && serverTick < next) {
            return;
        }
        NEXT_DECISION.put(npc.getUUID(), serverTick + DECISION_INTERVAL);

        NpcResources.Snapshot resources = NpcResources.get(npc, profile);
        double energyFraction = resources.maxEnergy() <= 0.0
                ? 0.0 : resources.energy() / resources.maxEnergy();
        double healthFraction = npc.getMaxHealth() <= 0.0f
                ? 1.0 : npc.getHealth() / (double) npc.getMaxHealth();
        double distance = npc.distanceTo(victim);

        // Face the target regardless of what is chosen; a shot or strike that starts from the
        // wrong heading looks broken even when it lands.
        NpcKiAim.applyPose(npc, victim);

        if (tryDisengage(npc, victim, energyFraction, healthFraction)) {
            return;
        }
        if (tryAscend(npc, profile, healthFraction, energyFraction)) {
            return;
        }
        if (distance <= MELEE_BAND) {
            if (!tryStrike(npc, profile, victim)) {
                // Nothing melee available: a burst buys space to charge instead of standing still.
                NpcCombatMoves.zBurst(npc, victim);
            }
            return;
        }
        if (distance <= KI_BAND) {
            if (!tryKi(npc, profile, victim)) {
                NpcCombatMoves.vanish(npc, victim, 0);
            }
            return;
        }
        // Out of both bands: close the gap. CustomNPCs is already walking; chase skips ahead.
        NpcCombatMoves.chase(npc, victim);
    }

    /** True while something else already owns this NPC's actions. */
    private static boolean isBusy(LivingEntity npc) {
        return NpcTransformSystem.isHolding(npc.getUUID())
                || NpcKiAim.lockedTarget(npc) != null;
    }

    /** Low on energy and hurt: back off and guard rather than trade badly. */
    private static boolean tryDisengage(LivingEntity npc, LivingEntity victim,
                                        double energyFraction, double healthFraction) {
        if (energyFraction > LOW_ENERGY || healthFraction > ASCEND_HEALTH) {
            NpcCombatMoves.guard(npc, false);
            return false;
        }
        NpcCombatMoves.guard(npc, true);
        return NpcCombatMoves.backstep(npc, victim);
    }

    /** Ascends into a selected form when hurt, if one is configured and not already active. */
    private static boolean tryAscend(LivingEntity npc, NpcCombatProfile profile,
                                     double healthFraction, double energyFraction) {
        if (healthFraction > ASCEND_HEALTH || energyFraction < LOW_ENERGY) {
            return false;
        }
        if (profile.selectedFormGroup.isBlank() || profile.selectedFormId.isBlank()) {
            return false;
        }
        // Already in the form it would ascend to.
        if (profile.selectedFormGroup.equalsIgnoreCase(profile.formGroup)
                && profile.selectedFormId.equalsIgnoreCase(profile.formId)) {
            return false;
        }
        return NpcTransformSystem.start(npc, profile.selectedFormGroup, profile.selectedFormId,
                NpcTransformSystem.DEFAULT_TICKS);
    }

    /** Fires the first ready strike technique this NPC knows. */
    private static boolean tryStrike(LivingEntity npc, NpcCombatProfile profile,
                                     LivingEntity victim) {
        for (String technique : profile.techniques) {
            if (PredefinedTechniqueLookup.findStrike(technique) == null) {
                continue;
            }
            if (NpcStrikeDispatcher.fire(technique, npc, profile, victim)) {
                return true;
            }
        }
        return false;
    }

    /** Fires the first ready ki technique this NPC knows, falling back to a plain blast. */
    private static boolean tryKi(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim) {
        List<String> techniques = profile.techniques;
        for (String technique : techniques) {
            if (PredefinedTechniqueLookup.find(technique) == null) {
                continue;
            }
            if (NpcKiAttackDispatcher.firePredefinedTechnique(technique, npc, profile,
                    NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0)) {
                return true;
            }
        }
        if (!NpcResources.spendEnergy(npc, profile, 2.0)) {
            return false;
        }
        NpcKiAttackDispatcher.fireKiBlast(npc, profile,
                NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, victim, 0);
        return true;
    }
}
