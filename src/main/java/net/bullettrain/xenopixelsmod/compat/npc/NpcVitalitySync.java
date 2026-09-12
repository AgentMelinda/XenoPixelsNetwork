package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Applies XenoPixels VIT to CustomNPCs' real max-health setting. */
public final class NpcVitalitySync {
    private static final String TAG_BASE_MAX_HEALTH = "xenopixels:npc_base_max_health";
    private static final String TAG_LAST_MAX_HEALTH = "xenopixels:npc_last_applied_max_health";
    private static boolean warnedReflectionFailure;

    private NpcVitalitySync() {}

    /**
     * Uses only profile VIT while DMZ stats are authoritative. Hybrid mode retains CustomNPCs'
     * configured health as a baseline. The metadata lives outside the replaceable profile tag.
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
            if (!XenoServerConfig.npcDmzStatsAuthoritative
                    && hasLast && configuredMax != persistent.getInt(TAG_LAST_MAX_HEALTH)) {
                baseMax = configuredMax;
            }

            double multiplier = vitalityMultiplier(profile);
            double vitScaling = vitalityScaling(profile);
            int targetMax = XenoServerConfig.npcDmzStatsAuthoritative
                    ? NpcVitalityMath.authoritativeMaxHealth(profile.vitality, multiplier, vitScaling)
                    : NpcVitalityMath.hybridMaxHealth(baseMax, profile.vitality, multiplier, vitScaling);
            float oldHealth = living.getHealth();
            float oldMax = living.getMaxHealth();

            setMaxHealth.invoke(stats, targetMax);
            pushLivingMaxHealth(living, targetMax);
            persistent.putInt(TAG_BASE_MAX_HEALTH, baseMax);
            persistent.putInt(TAG_LAST_MAX_HEALTH, targetMax);

            float appliedMax = living.getMaxHealth();
            living.setHealth(NpcVitalityMath.preserveHealthPercent(oldHealth, oldMax, appliedMax));
        } catch (ReflectiveOperationException | ClassCastException e) {
            warnOnce(e);
        }
    }

    private static Object customNpcStats(Entity entity) throws ReflectiveOperationException {
        for (String className : new String[] {
                "espi.mynpcs.entity.EntityNPCInterface",
                "noppes.npcs.entity.EntityNPCInterface"}) {
            try {
                Class<?> npcClass = Class.forName(className);
                if (npcClass.isInstance(entity)) {
                    Field statsField = npcClass.getField("stats");
                    return statsField.get(entity);
                }
            } catch (ClassNotFoundException ignored) {
                // The other supported CustomNPC namespace may be installed instead.
            }
        }
        return null;
    }

    /** Restores the native max-health baseline when a profile stops being authoritative. */
    static void restoreNative(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide()) return;
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(TAG_BASE_MAX_HEALTH, Tag.TAG_INT)) return;
        try {
            Object stats = customNpcStats(entity);
            if (stats == null) return;
            int baseMax = Math.max(1, persistent.getInt(TAG_BASE_MAX_HEALTH));
            float oldHealth = living.getHealth();
            float oldMax = living.getMaxHealth();
            stats.getClass().getMethod("setMaxHealth", int.class).invoke(stats, baseMax);
            pushLivingMaxHealth(living, baseMax);
            persistent.remove(TAG_BASE_MAX_HEALTH);
            persistent.remove(TAG_LAST_MAX_HEALTH);
            living.setHealth(NpcVitalityMath.preserveHealthPercent(
                    oldHealth, oldMax, living.getMaxHealth()));
        } catch (ReflectiveOperationException | ClassCastException e) {
            warnOnce(e);
        }
    }

    /**
     * The HP a wand / Stats tab should display for this profile: vanilla 20 + VIT ×
     * form VIT × race {@code VIT_scaling}, matching {@code StatsData.getHealthBonus}.
     */
    public static int displayedMaxHealth(NpcCombatProfile profile) {
        if (profile == null) return NpcVitalityMath.VANILLA_BASE;
        return NpcVitalityMath.authoritativeMaxHealth(
                profile.vitality, vitalityMultiplier(profile), vitalityScaling(profile));
    }

    private static void pushLivingMaxHealth(LivingEntity living, int targetMax) {
        AttributeInstance attribute = living.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && Math.abs(attribute.getBaseValue() - targetMax) > 0.01) {
            attribute.setBaseValue(targetMax);
        }
    }

    private static double vitalityMultiplier(NpcCombatProfile profile) {
        try {
            return NpcFormLookup.multiplier(profile, "VIT");
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    /**
     * Race/class {@code VIT_scaling} from DragonMineZ's live {@code RaceStatsConfig}.
     * Warrior is 1.2 in the 2.1.3 race stats files; the Java default is 1.0 if the config is absent.
     */
    static double vitalityScaling(NpcCombatProfile profile) {
        if (profile == null) return 1.0;
        try {
            String race = profile.raceId == null || profile.raceId.isBlank() ? "human" : profile.raceId;
            String characterClass = "warrior";
            if (profile.appearance != null && profile.appearance.characterClass != null
                    && !profile.appearance.characterClass.isBlank()) {
                characterClass = profile.appearance.characterClass;
            }
            var raceConfig = com.dragonminez.common.config.ConfigManager.getRaceStats(race);
            if (raceConfig == null) return 1.0;
            var scaling = raceConfig.getClassStats(characterClass).getStatScaling();
            if (scaling == null || scaling.getVitalityScaling() == null) return 1.0;
            double value = scaling.getVitalityScaling();
            return Double.isFinite(value) && value > 0.0 ? value : 1.0;
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
