package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Applies XenoPixels VIT to CustomNPCs' real max-health setting. */
public final class NpcVitalitySync {
    private static final String TAG_BASE_MAX_HEALTH = "xenopixels:npc_base_max_health";
    private static final String TAG_LAST_MAX_HEALTH = "xenopixels:npc_last_applied_max_health";
    private static boolean warnedReflectionFailure;

    private NpcVitalitySync() {}

    /**
     * Keeps CustomNPCs' configured health as the baseline, then adds the profile's VIT bonus.
     * The metadata lives outside the replaceable profile tag so wand saves cannot erase it.
     */
    public static void apply(Entity entity, NpcCombatProfile profile) {
        if (!(entity instanceof LivingEntity living)
                || entity.level().isClientSide()
                || profile == null
                || !NpcCombatProfile.hasProfile(entity)) {
            return;
        }

        try {
            Object stats = customNpcStats(entity);
            if (stats == null) {
                return;
            }
            Method getMaxHealth = stats.getClass().getMethod("getMaxHealth");
            Method setMaxHealth = stats.getClass().getMethod("setMaxHealth", int.class);
            int configuredMax = Math.max(1, (Integer) getMaxHealth.invoke(stats));

            CompoundTag persistent = entity.getPersistentData();
            boolean hasBase = persistent.contains(TAG_BASE_MAX_HEALTH, Tag.TAG_INT);
            boolean hasLast = persistent.contains(TAG_LAST_MAX_HEALTH, Tag.TAG_INT);
            int baseMax = hasBase
                    ? Math.max(1, persistent.getInt(TAG_BASE_MAX_HEALTH))
                    : configuredMax;

            // A native CustomNPC stats edit changes its configured max away from our last value.
            // Treat that new value as the requested baseline instead of overwriting the edit.
            if (hasLast && configuredMax != persistent.getInt(TAG_LAST_MAX_HEALTH)) {
                baseMax = configuredMax;
            }

            int targetMax = NpcVitalityMath.maxHealth(
                    baseMax, profile.vitality, vitalityMultiplier(profile));
            float oldHealth = living.getHealth();
            float oldMax = living.getMaxHealth();

            setMaxHealth.invoke(stats, targetMax);
            persistent.putInt(TAG_BASE_MAX_HEALTH, baseMax);
            persistent.putInt(TAG_LAST_MAX_HEALTH, targetMax);

            float appliedMax = living.getMaxHealth();
            living.setHealth(NpcVitalityMath.preserveHealthPercent(oldHealth, oldMax, appliedMax));
        } catch (ReflectiveOperationException | ClassCastException e) {
            warnOnce(e);
        }
    }

    private static Object customNpcStats(Entity entity) throws ReflectiveOperationException {
        Class<?> npcClass;
        try {
            npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
        if (!npcClass.isInstance(entity)) {
            return null;
        }
        Field statsField = npcClass.getField("stats");
        return statsField.get(entity);
    }

    private static double vitalityMultiplier(NpcCombatProfile profile) {
        try {
            return NpcFormLookup.multiplier(profile, "VIT");
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    private static void warnOnce(Exception failure) {
        if (warnedReflectionFailure) {
            return;
        }
        warnedReflectionFailure = true;
        XenoPixelsMod.LOGGER.warn(
                "Could not synchronize an NPC combat profile's vitality to CustomNPC health: {}",
                failure.toString());
    }
}
