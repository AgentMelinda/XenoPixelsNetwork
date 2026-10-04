package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives CustomNPCs' own navigator from the profile's DMZ fly skill.
 *
 * <p>CustomNPCs, not this mod, owns NPC movement: {@code EntityNPCInterface.createNavigation}
 * picks between the ground and flying navigators from {@code DataAI}'s movement type, and
 * {@code canFly()} is literally {@code navigation instanceof FlyingPathNavigation}. So enabling
 * the DMZ fly skill on an NPC has to reach {@code ais.setNavigationType} and then ask CNPC to
 * rebuild, which it does when {@code updateAI} is set — the same field
 * {@link NpcDisplayApply#setSize} already flips after a display change.
 *
 * <p>Reflective for the same reason every other CNPC touchpoint here is: this class must compile
 * and load with no CustomNPCs jar present.
 */
public final class NpcFlightBridge {
    private static final String[] NPC_CLASSES = {
            "espi.mynpcs.entity.EntityNPCInterface",
            "noppes.npcs.entity.EntityNPCInterface"
    };
    /** CustomNPCs {@code DataAI.movementType}: 0 walks, 1 flies. */
    private static final int NAV_GROUND = 0;
    private static final int NAV_FLYING = 1;

    /**
     * Last value actually pushed, so the common case costs a map lookup rather than four
     * reflective calls. Only a hint — a miss re-pushes, which is harmless and idempotent.
     */
    private static final Map<UUID, Boolean> APPLIED = new ConcurrentHashMap<>();

    private NpcFlightBridge() {}

    /** Pushes {@code profile.flySkillOn} onto the native NPC. No-op off-server or without CNPC. */
    public static boolean apply(Entity entity, NpcCombatProfile profile) {
        if (entity == null || profile == null || entity.level().isClientSide()) {
            return false;
        }
        if (NpcCombatBrain.directsFlight(entity.getUUID())
                || net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain
                .directsFlight(entity.getUUID())) {
            return true;
        }
        // Combat owns the navigator while the NPC is engaged. Without this, every apply()
        // re-pushed flySkillOn=true right after a brain landed the NPC on a grounded target,
        // and the NPC bobbed between NAV_FLYING/NAV_GROUND (navigator rebuilds, updateAI
        // thrash) instead of staying down. This is the "player pressed F" emulation: while
        // the retaliation target walks on the ground, the NPC walks on the ground too.
        LivingEntity engagedWith = entity instanceof Mob mob ? mob.getTarget() : null;
        boolean want = wantsFlightFor(profile, engagedWith);
        return set(entity, settled(entity.getUUID(), want));
    }

    /**
     * Calls in a row a changed decision must survive before it is pushed. apply() runs every
     * four ticks, so this is roughly 12 ticks: a target that hops lands for a tick or two, and
     * without this each hop swapped the navigator and rebuilt CustomNPCs' AI.
     */
    static final int SETTLE_CALLS = 3;
    private static final Map<UUID, int[]> PENDING = new ConcurrentHashMap<>();

    private static boolean settled(UUID id, boolean want) {
        Boolean last = APPLIED.get(id);
        int[] pending = PENDING.computeIfAbsent(id, ignored -> new int[] {want ? 1 : 0, 0});
        boolean result = debounce(last, want, pending, SETTLE_CALLS);
        return result;
    }

    /**
     * Pure hysteresis step. {@code pending[0]} is the candidate (1 = fly), {@code pending[1]} how
     * many consecutive calls have asked for it. The first decision ever made applies at once.
     */
    static boolean debounce(Boolean last, boolean want, int[] pending, int need) {
        if (last == null || last == want) {
            pending[0] = want ? 1 : 0;
            pending[1] = 0;
            return want;
        }
        int candidate = want ? 1 : 0;
        if (pending[0] != candidate) {
            pending[0] = candidate;
            pending[1] = 1;
        } else {
            pending[1]++;
        }
        return pending[1] >= need ? want : last;
    }

    /**
     * Whether the periodic sync may push the flying navigator for this profile and target.
     *
     * <p>Pure so the rule is testable without a CNPC entity: the skill permits flight, and the
     * flying navigator is taken only while engaged with a live target that is off the ground.
     * Idle, or against a grounded target, the NPC keeps the ground navigator.
     */
    static boolean wantsFlightFor(NpcCombatProfile profile, LivingEntity engagedWith) {
        boolean engaged = engagedWith != null && engagedWith.isAlive();
        // "Can Use Flight" off means the NPC never takes the flying navigator, idle or engaged;
        // the combat brain already honoured it (NpcFlightPolicy.canCombatFly), this path did not.
        return wantsFlight(profile.flySkillOn && profile.canUseFlight, engaged,
                engaged && NpcFlightPolicy.targetGrounded(engagedWith));
    }

    /** Pure decision table behind {@link #wantsFlightFor}, so the rule is unit-testable. */
    static boolean wantsFlight(boolean flySkillOn, boolean engaged, boolean targetGrounded) {
        // The Fly skill is permission, not a mode. Like a player pressing F, the NPC takes off
        // only while it is engaged with a target that is itself in the air, and walks otherwise;
        // an idle NPC with Fly enabled used to hover forever in the flying pose.
        return flySkillOn && engaged && !targetGrounded;
    }

    /** Pushes a navigator type without changing {@code flySkillOn}. Used by the combat brain. */
    public static boolean setFlying(Entity entity, boolean flying) {
        if (entity == null || entity.level().isClientSide()) {
            return false;
        }
        return set(entity, flying);
    }

    /** Same, ignoring the cached last-applied value; used after a respawn rebuilds the AI. */
    public static boolean force(Entity entity, NpcCombatProfile profile) {
        if (entity == null) {
            return false;
        }
        APPLIED.remove(entity.getUUID());
        return apply(entity, profile);
    }

    public static void forget(UUID id) {
        if (id != null) {
            APPLIED.remove(id);
            PENDING.remove(id);
        }
    }

    private static boolean set(Entity entity, boolean flying) {
        Boolean last = APPLIED.get(entity.getUUID());
        if (last != null && last == flying) {
            return true;
        }
        Object ais = ais(entity);
        if (ais == null) {
            return false;
        }
        try {
            int want = flying ? NAV_FLYING : NAV_GROUND;
            Object current = ais.getClass().getMethod("getNavigationType").invoke(ais);
            if (current instanceof Integer value && value == want) {
                APPLIED.put(entity.getUUID(), flying);
                return true;
            }
            ais.getClass().getMethod("setNavigationType", int.class).invoke(ais, want);
            // setNavigationType only writes the field; CNPC rebuilds the navigator and move
            // control on its next AI refresh, which this flag is what requests.
            markUpdateAi(entity);
            APPLIED.put(entity.getUUID(), flying);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when this NPC is currently on CustomNPCs' flying navigator. */
    public static boolean isFlyingNavigation(LivingEntity entity) {
        Object ais = ais(entity);
        if (ais == null) {
            return false;
        }
        try {
            Object current = ais.getClass().getMethod("getNavigationType").invoke(ais);
            return current instanceof Integer value && value == NAV_FLYING;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object ais(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (String name : NPC_CLASSES) {
            try {
                Class<?> npcClass = Class.forName(name);
                if (!npcClass.isInstance(entity)) {
                    continue;
                }
                return npcClass.getField("ais").get(entity);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void markUpdateAi(Entity entity) {
        for (String name : NPC_CLASSES) {
            try {
                Class<?> npcClass = Class.forName(name);
                if (!npcClass.isInstance(entity)) {
                    continue;
                }
                npcClass.getField("updateAI").setBoolean(entity, true);
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}
