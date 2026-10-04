package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.server.events.players.StatsEvents;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Applies DMZ's player health baseline and VIT bonus to NPC max health. */
public final class NpcVitalitySync {
    private static final String TAG_BASE_MAX_HEALTH = "xenopixels:npc_base_max_health";
    private static final String TAG_BASE_MAX_HEALTH_D = "xenopixels:npc_base_max_health_d";
    private static final String TAG_LAST_MAX_HEALTH = "xenopixels:npc_last_applied_max_health";
    private static final String TAG_LAST_MAX_HEALTH_D = "xenopixels:npc_last_applied_max_health_d";
    private static final double PLAYER_BASE_HEALTH = 20.0;
    private static final ResourceLocation DMZ_HEALTH_MODIFIER =
            AttributeMods.id(StatsEvents.DMZ_HEALTH_MODIFIER_UUID);
    private static boolean warnedReflectionFailure;

    private NpcVitalitySync() {}

    /**
     * Applies the same 20-base plus {@code StatsData.getHealthBonus()} modifier used for players.
     * Hybrid mode keeps the counterpart's configured base health. The metadata lives outside the
     * replaceable profile tag, so its original health can be restored when DMZ authority is removed.
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
            Method getMaxHealth = stats == null ? null : stats.getClass().getMethod("getMaxHealth");
            Method setMaxHealth = stats == null ? null : stats.getClass().getMethod("setMaxHealth", int.class);
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            CompoundTag persistent = entity.getPersistentData();
            boolean hasBase = persistent.contains(TAG_BASE_MAX_HEALTH_D, Tag.TAG_DOUBLE)
                    || persistent.contains(TAG_BASE_MAX_HEALTH, Tag.TAG_INT);
            double configuredBase = getMaxHealth != null ? (Integer) getMaxHealth.invoke(stats)
                    : maxHealth != null ? maxHealth.getBaseValue() : PLAYER_BASE_HEALTH;
            double baseMax = hasBase
                    ? persistent.contains(TAG_BASE_MAX_HEALTH_D, Tag.TAG_DOUBLE)
                            ? persistent.getDouble(TAG_BASE_MAX_HEALTH_D)
                            : persistent.getInt(TAG_BASE_MAX_HEALTH)
                    : Math.max(1.0, configuredBase);

            int lastNpc = lastNpcStatsMax(persistent);
            if (!XenoServerConfig.npcDmzStatsAuthoritative && getMaxHealth != null
                    && lastNpc > 0 && (Integer) getMaxHealth.invoke(stats) != lastNpc) {
                // A native CustomNPC health edit changes its requested hybrid baseline.
                baseMax = Math.max(1, (Integer) getMaxHealth.invoke(stats));
            }

            float healthBonus = NpcDmzStats.healthBonus(living, profile);
            double attributeBase = profile.maxHealthOverride > 0
                    ? profile.maxHealthOverride
                    : XenoServerConfig.npcDmzStatsAuthoritative ? PLAYER_BASE_HEALTH : baseMax;
            float modifierAmount = profile.maxHealthOverride > 0 ? 0.0f : healthBonus;
            float oldHealth = living.getHealth();
            float oldMax = living.getMaxHealth();

            if (maxHealth != null) {
                setBaseHealth(maxHealth, attributeBase);
                setDmzHealthBonus(maxHealth, modifierAmount);
            }
            double targetMax = maxHealth == null ? attributeBase + modifierAmount : maxHealth.getValue();
            if (setMaxHealth != null) {
                setMaxHealth.invoke(stats, NpcVitalityMath.npcStatsMaxHealth(targetMax));
            }

            persistent.putDouble(TAG_BASE_MAX_HEALTH_D, baseMax);
            persistent.putInt(TAG_LAST_MAX_HEALTH,
                    NpcVitalityMath.npcStatsMaxHealth(targetMax));
            persistent.putDouble(TAG_LAST_MAX_HEALTH_D, targetMax);

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
        if (!persistent.contains(TAG_BASE_MAX_HEALTH_D, Tag.TAG_DOUBLE)
                && !persistent.contains(TAG_BASE_MAX_HEALTH, Tag.TAG_INT)) return;
        try {
            Object stats = customNpcStats(entity);
            double baseMax = persistent.contains(TAG_BASE_MAX_HEALTH_D, Tag.TAG_DOUBLE)
                    ? Math.max(1.0, persistent.getDouble(TAG_BASE_MAX_HEALTH_D))
                    : Math.max(1, persistent.getInt(TAG_BASE_MAX_HEALTH));
            float oldHealth = living.getHealth();
            float oldMax = living.getMaxHealth();
            if (stats != null) {
                stats.getClass().getMethod("setMaxHealth", int.class).invoke(stats,
                        NpcVitalityMath.npcStatsMaxHealth(baseMax));
            }
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                setDmzHealthBonus(maxHealth, 0.0f);
                setBaseHealth(maxHealth, baseMax);
            }
            persistent.remove(TAG_BASE_MAX_HEALTH);
            persistent.remove(TAG_BASE_MAX_HEALTH_D);
            persistent.remove(TAG_LAST_MAX_HEALTH);
            persistent.remove(TAG_LAST_MAX_HEALTH_D);
            living.setHealth(NpcVitalityMath.preserveHealthPercent(
                    oldHealth, oldMax, living.getMaxHealth()));
        } catch (ReflectiveOperationException | ClassCastException e) {
            warnOnce(e);
        }
    }

    /**
     * Whole-HP label for the wand / Stats tab: vanilla 20 + VIT × form VIT × race
     * {@code VIT_scaling}, matching {@code StatsData.getHealthBonus()} float rounding.
     */
    public static String displayedMaxHealthText(NpcCombatProfile profile) {
        return NpcVitalityMath.displayedMaxHealth(livingMaxHealth(profile));
    }

    /**
     * Saturated CustomNPC int for native {@code setMaxHealth}. Combat HP uses
     * {@link #livingMaxHealth(NpcCombatProfile)}.
     */
    public static int npcStatsMaxHealth(NpcCombatProfile profile) {
        return NpcVitalityMath.npcStatsMaxHealth(livingMaxHealth(profile));
    }

    public static double livingMaxHealth(NpcCombatProfile profile) {
        if (profile == null) return NpcVitalityMath.VANILLA_BASE;
        return NpcVitalityMath.authoritativeMaxHealth(
                profile.vitality, vitalityMultiplier(profile), vitalityScaling(profile));
    }

    private static int lastNpcStatsMax(CompoundTag persistent) {
        if (persistent.contains(TAG_LAST_MAX_HEALTH_D, Tag.TAG_DOUBLE)) {
            return NpcVitalityMath.npcStatsMaxHealth(persistent.getDouble(TAG_LAST_MAX_HEALTH_D));
        }
        if (persistent.contains(TAG_LAST_MAX_HEALTH, Tag.TAG_INT)) {
            return persistent.getInt(TAG_LAST_MAX_HEALTH);
        }
        return 0;
    }

    private static void setBaseHealth(AttributeInstance attribute, double value) {
        if (Math.abs(attribute.getBaseValue() - value) > 0.01) {
            attribute.setBaseValue(value);
        }
    }

    private static void setDmzHealthBonus(AttributeInstance attribute, float amount) {
        AttributeModifier existing = attribute.getModifier(DMZ_HEALTH_MODIFIER);
        boolean matches = existing != null && Math.abs(existing.amount() - amount)
                <= Math.max(1.0, Math.abs(amount) * 1.0e-5);
        if (matches) return;
        attribute.removeModifier(DMZ_HEALTH_MODIFIER);
        if (Float.isFinite(amount) && amount > 0.0f) {
            attribute.addPermanentModifier(new AttributeModifier(DMZ_HEALTH_MODIFIER, amount,
                    Operation.ADD_VALUE));
        }
    }

    static double vitalityMultiplier(NpcCombatProfile profile) {
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
        return NpcStatScaling.of(profile, "VIT");
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
