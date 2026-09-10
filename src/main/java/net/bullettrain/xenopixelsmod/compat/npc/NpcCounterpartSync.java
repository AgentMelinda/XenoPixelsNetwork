package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Applies authoritative XenoPixels values to the verified CustomNPC/MyNPC counterpart fields. */
public final class NpcCounterpartSync {
    private static final String[] NPC_CLASSES = {
            "espi.mynpcs.entity.EntityNPCInterface",
            "noppes.npcs.entity.EntityNPCInterface"
    };
    private static final String TAG_LAST_FINGERPRINT = "xenopixels:npc_last_stat_fingerprint";
    private static final String TAG_NATIVE_BACKUP = "xenopixels:npc_native_stat_backup";
    private static boolean warnedReflectionFailure;

    private NpcCounterpartSync() {}

    /**
     * Resolved once. {@link NpcNegativeEffectPersistence#beforeSave} runs this for every living
     * entity the game serializes, so a per-call {@code Class.forName} — which builds a
     * {@link ClassNotFoundException} on every miss when neither NPC mod is installed — would sit
     * on the chunk-save path. Both mods load long before any world save, so a missing class at
     * first call stays missing.
     */
    private static volatile Class<?>[] npcClasses;

    public static boolean isCustomNpc(Entity entity) {
        if (entity == null) return false;
        for (Class<?> type : resolveNpcClasses()) {
            if (type.isInstance(entity)) return true;
        }
        return false;
    }

    private static Class<?>[] resolveNpcClasses() {
        Class<?>[] cached = npcClasses;
        if (cached != null) return cached;
        java.util.List<Class<?>> resolved = new java.util.ArrayList<>(NPC_CLASSES.length);
        for (String name : NPC_CLASSES) {
            try {
                resolved.add(Class.forName(name));
            } catch (ClassNotFoundException ignored) {
            }
        }
        cached = resolved.toArray(new Class<?>[0]);
        npcClasses = cached;
        return cached;
    }

    /**
     * Reapplies only when the authoritative snapshot changed, unless a lifecycle refresh requests
     * {@code force}. Health retains its own baseline/last-applied reconciliation.
     */
    public static void apply(Entity entity, NpcCombatProfile profile, boolean force) {
        if (entity == null || entity.level().isClientSide() || profile == null
                || !NpcCombatProfile.hasProfile(entity) || !isCustomNpc(entity)) {
            return;
        }
        if (!profile.authoritative) {
            restoreAfterAuthority(entity);
            return;
        }

        // Flight sits outside the stat fingerprint deliberately: it is not a stat, and the
        // bridge keeps its own last-applied cache, so pushing it before the fingerprint gate
        // costs a map lookup and means a fly toggle takes effect without a stat edit too.
        NpcFlightBridge.apply(entity, profile);
        // Aggro is likewise not a stat: the bridge caches its own last-applied value, and
        // CustomNPCs re-pins FOLLOW_RANGE to NpcNavRange on every entity load, so this has to
        // be re-pushed rather than set once.
        NpcAggroBridge.apply(entity, profile);

        CompoundTag persistent = entity.getPersistentData();
        int fingerprint = 31 * profile.authorityFingerprint()
                + Boolean.hashCode(XenoServerConfig.npcDmzStatsAuthoritative);
        if (!force && persistent.contains(TAG_LAST_FINGERPRINT, Tag.TAG_INT)
                && persistent.getInt(TAG_LAST_FINGERPRINT) == fingerprint) {
            return;
        }

        NpcVitalitySync.apply(entity, profile);
        try {
            Object stats = stats(entity);
            if (stats != null) {
                Object melee = stats.getClass().getMethod("getMelee").invoke(stats);
                if (XenoServerConfig.npcDmzStatsAuthoritative) {
                    backupNative(persistent, stats, melee);
                    if (melee != null) {
                        melee.getClass().getMethod("setStrength", int.class).invoke(melee, 1);
                        melee.getClass().getMethod("setKnockback", int.class).invoke(melee, 0);
                    }
                    stats.getClass().getMethod("setHealthRegen", int.class).invoke(stats, 0);
                    stats.getClass().getMethod("setCombatRegen", int.class).invoke(stats, 0);
                    Method setResistance = stats.getClass().getMethod("setResistance", int.class, float.class);
                    for (int type = 0; type < 4; type++) {
                        setResistance.invoke(stats, type, 1.0f);
                    }
                    Object ranged = stats.getClass().getMethod("getRanged").invoke(stats);
                    if (ranged != null) {
                        ranged.getClass().getMethod("setStrength", int.class).invoke(ranged, 0);
                        ranged.getClass().getMethod("setKnockback", int.class).invoke(ranged, 0);
                        ranged.getClass().getMethod("setExplodeSize", int.class).invoke(ranged, 0);
                    }
                } else {
                    restoreNative(persistent, stats, melee);
                }
                markClientUpdate(entity);
            }
            persistent.putInt(TAG_LAST_FINGERPRINT, fingerprint);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            warnOnce(failure);
        }
    }

    public static void force(Entity entity, NpcCombatProfile profile) {
        // A respawn rebuilds CustomNPCs' AI from its own saved data, so the cached
        // last-applied navigator value is stale by definition on this path.
        NpcFlightBridge.force(entity, profile);
        NpcAggroBridge.forget(entity.getUUID());
        apply(entity, profile, true);
    }

    private static void restoreAfterAuthority(Entity entity) {
        CompoundTag persistent = entity.getPersistentData();
        try {
            Object stats = stats(entity);
            if (stats != null) {
                restoreNative(persistent, stats, stats.getClass().getMethod("getMelee").invoke(stats));
                markClientUpdate(entity);
            }
            NpcVitalitySync.restoreNative(entity);
            persistent.remove(TAG_LAST_FINGERPRINT);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            warnOnce(failure);
        }
    }

    private static Object stats(Entity entity) throws ReflectiveOperationException {
        Class<?> npcClass = npcClass(entity);
        if (npcClass == null) return null;
        Field field = npcClass.getField("stats");
        return field.get(entity);
    }

    private static Class<?> npcClass(Entity entity) {
        for (String name : NPC_CLASSES) {
            try {
                Class<?> type = Class.forName(name);
                if (type.isInstance(entity)) return type;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static void markClientUpdate(Entity entity) {
        try {
            Field field = npcClass(entity).getField("updateClient");
            field.setBoolean(entity, true);
        } catch (ReflectiveOperationException | NullPointerException ignored) {
            // DataStats setters already mark current MyNPC builds for update.
        }
    }

    private static void backupNative(CompoundTag persistent, Object stats, Object melee)
            throws ReflectiveOperationException {
        if (persistent.contains(TAG_NATIVE_BACKUP, Tag.TAG_COMPOUND)) return;
        CompoundTag backup = new CompoundTag();
        backup.putInt("HealthRegen", (Integer) stats.getClass().getMethod("getHealthRegen").invoke(stats));
        backup.putInt("CombatRegen", (Integer) stats.getClass().getMethod("getCombatRegen").invoke(stats));
        Method getResistance = stats.getClass().getMethod("getResistance", int.class);
        for (int type = 0; type < 4; type++) {
            backup.putFloat("Resistance" + type, (Float) getResistance.invoke(stats, type));
        }
        if (melee != null) {
            backup.putInt("MeleeStrength", (Integer) melee.getClass().getMethod("getStrength").invoke(melee));
            backup.putInt("MeleeKnockback", (Integer) melee.getClass().getMethod("getKnockback").invoke(melee));
        }
        Object ranged = stats.getClass().getMethod("getRanged").invoke(stats);
        if (ranged != null) {
            backup.putInt("RangedStrength", (Integer) ranged.getClass().getMethod("getStrength").invoke(ranged));
            backup.putInt("RangedKnockback", (Integer) ranged.getClass().getMethod("getKnockback").invoke(ranged));
            backup.putInt("RangedExplosion", (Integer) ranged.getClass().getMethod("getExplodeSize").invoke(ranged));
        }
        persistent.put(TAG_NATIVE_BACKUP, backup);
    }

    private static void restoreNative(CompoundTag persistent, Object stats, Object melee)
            throws ReflectiveOperationException {
        if (!persistent.contains(TAG_NATIVE_BACKUP, Tag.TAG_COMPOUND)) return;
        CompoundTag backup = persistent.getCompound(TAG_NATIVE_BACKUP);
        stats.getClass().getMethod("setHealthRegen", int.class).invoke(stats, backup.getInt("HealthRegen"));
        stats.getClass().getMethod("setCombatRegen", int.class).invoke(stats, backup.getInt("CombatRegen"));
        Method setResistance = stats.getClass().getMethod("setResistance", int.class, float.class);
        for (int type = 0; type < 4; type++) {
            setResistance.invoke(stats, type, backup.getFloat("Resistance" + type));
        }
        if (melee != null) {
            melee.getClass().getMethod("setStrength", int.class).invoke(melee, backup.getInt("MeleeStrength"));
            melee.getClass().getMethod("setKnockback", int.class).invoke(melee, backup.getInt("MeleeKnockback"));
        }
        Object ranged = stats.getClass().getMethod("getRanged").invoke(stats);
        if (ranged != null) {
            ranged.getClass().getMethod("setStrength", int.class).invoke(ranged, backup.getInt("RangedStrength"));
            ranged.getClass().getMethod("setKnockback", int.class).invoke(ranged, backup.getInt("RangedKnockback"));
            ranged.getClass().getMethod("setExplodeSize", int.class).invoke(ranged, backup.getInt("RangedExplosion"));
        }
        persistent.remove(TAG_NATIVE_BACKUP);
    }

    private static void warnOnce(Exception failure) {
        if (warnedReflectionFailure) return;
        warnedReflectionFailure = true;
        XenoPixelsMod.LOGGER.warn("Could not synchronize authoritative NPC counterpart stats: {}",
                failure.toString());
    }
}
