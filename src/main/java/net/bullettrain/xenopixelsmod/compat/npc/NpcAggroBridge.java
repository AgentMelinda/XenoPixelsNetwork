package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Widens how far a profiled NPC will notice, chase, and keep chasing a target.
 *
 * <p>Three separate CustomNPCs limits make an NPC "lose the player", all verified in
 * {@code EntityAIAttackTarget}:
 *
 * <ol>
 *   <li>{@code canContinueToUse} drops the target unless
 *       {@code npc.isInRange(target, stats.aggroRange)} — aggro range is the <em>retention</em>
 *       range, not just the acquisition range.</li>
 *   <li>{@code isWithinRestriction} requires the target to stay within
 *       {@code max(aggroRange * 2, 64)} blocks of {@code startPos}, which is wherever the NPC
 *       was standing when the goal <em>started</em>. Lead an NPC far enough from that spot and it
 *       gives up and walks home, however close you are to it.</li>
 *   <li>{@code EntityNPCInterface} pins {@link Attributes#FOLLOW_RANGE} to
 *       {@code CustomNpcs.NpcNavRange} (config default 32) on every load, so pathing cannot
 *       reach past that even when aggro range is large.</li>
 * </ol>
 *
 * <p>Both derived values are recomputed from a remembered base rather than multiplied in place,
 * so re-applying is idempotent and CustomNPCs' own saved {@code aggroRange} keeps its meaning.
 *
 * <p>Reflective throughout, matching {@link NpcDisplayApply}: this class must load with no
 * CustomNPCs jar present.
 */
public final class NpcAggroBridge {
    private static final String[] NPC_CLASSES = {
            "espi.mynpcs.entity.EntityNPCInterface",
            "noppes.npcs.entity.EntityNPCInterface"
    };

    /** CustomNPCs' own default, used when an NPC somehow reports a nonsense base. */
    private static final int FALLBACK_BASE_AGGRO = 16;
    /**
     * Ceiling on the effective range. Follow range drives pathfinding cost, and an NPC that
     * tries to path to something hundreds of blocks away is a TPS problem, not a feature.
     */
    public static final int MAX_EFFECTIVE_RANGE = 128;

    /** Base aggro range as CustomNPCs stored it, before any multiplier was applied. */
    private static final Map<UUID, Integer> BASE_AGGRO = new ConcurrentHashMap<>();
    /** Last effective range pushed, so the common case costs one map lookup. */
    private static final Map<UUID, Integer> APPLIED = new ConcurrentHashMap<>();

    private NpcAggroBridge() {}

    /** Pushes the profile's aggro multiplier onto the native NPC. */
    public static boolean apply(Entity entity, NpcCombatProfile profile) {
        if (entity == null || profile == null || entity.level().isClientSide()) {
            return false;
        }
        int base = baseAggro(entity);
        if (base <= 0) {
            return false;
        }
        int effective = effectiveRange(base, profile.aggroMultiplier);
        Integer last = APPLIED.get(entity.getUUID());
        if (last != null && last == effective) {
            return true;
        }
        boolean ok = setAggroRange(entity, effective);
        // Follow range gates pathfinding distance and is re-pinned to NpcNavRange on every
        // load, so it has to be re-raised alongside the aggro range rather than once.
        applyFollowRange(entity, effective);
        if (ok) {
            APPLIED.put(entity.getUUID(), effective);
        }
        return ok;
    }

    /** Effective range for a base and multiplier, clamped to something pathfinding can afford. */
    public static int effectiveRange(int base, float multiplier) {
        float scaled = base * NpcCombatProfile.clampAggroMultiplier(multiplier);
        return Math.max(1, Math.min(MAX_EFFECTIVE_RANGE, Math.round(scaled)));
    }

    /** The effective aggro range currently pushed for this NPC, or 0 when none has been. */
    public static int effectiveRangeOf(Entity entity) {
        if (entity == null) {
            return 0;
        }
        Integer applied = APPLIED.get(entity.getUUID());
        return applied == null ? 0 : applied;
    }

    /**
     * True when the target is still inside this NPC's effective aggro range — the same test
     * CustomNPCs' own goal uses to decide whether to keep chasing.
     */
    public static boolean inEffectiveRange(LivingEntity npc, LivingEntity target) {
        if (npc == null || target == null) {
            return false;
        }
        int range = effectiveRangeOf(npc);
        if (range <= 0) {
            range = FALLBACK_BASE_AGGRO;
        }
        return npc.distanceToSqr(target) <= (double) range * range;
    }

    public static void forget(UUID id) {
        if (id != null) {
            BASE_AGGRO.remove(id);
            APPLIED.remove(id);
        }
    }

    /**
     * CustomNPCs' stored aggro range, remembered on first sight so the multiplier is always
     * applied to the original rather than compounding on an already-widened value.
     */
    private static int baseAggro(Entity entity) {
        Integer remembered = BASE_AGGRO.get(entity.getUUID());
        if (remembered != null) {
            return remembered;
        }
        Object stats = stats(entity);
        if (stats == null) {
            return 0;
        }
        try {
            Object value = stats.getClass().getMethod("getAggroRange").invoke(stats);
            int base = value instanceof Integer i && i > 0 ? i : FALLBACK_BASE_AGGRO;
            BASE_AGGRO.put(entity.getUUID(), base);
            return base;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean setAggroRange(Entity entity, int range) {
        Object stats = stats(entity);
        if (stats == null) {
            return false;
        }
        try {
            stats.getClass().getMethod("setAggroRange", int.class).invoke(stats, range);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyFollowRange(Entity entity, int range) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        AttributeInstance attribute = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (attribute == null) {
            return;
        }
        if (attribute.getBaseValue() < range) {
            attribute.setBaseValue(range);
        }
    }

    private static Object stats(Entity entity) {
        for (String name : NPC_CLASSES) {
            try {
                Class<?> npcClass = Class.forName(name);
                if (!npcClass.isInstance(entity)) {
                    continue;
                }
                return npcClass.getField("stats").get(entity);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
