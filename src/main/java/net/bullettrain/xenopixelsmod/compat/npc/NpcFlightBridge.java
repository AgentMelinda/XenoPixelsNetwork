package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        return set(entity, profile.flySkillOn);
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
