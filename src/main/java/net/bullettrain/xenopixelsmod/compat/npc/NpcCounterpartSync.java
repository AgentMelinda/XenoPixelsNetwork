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

    /**
     * Any NPC this sync owns: a CustomNPCs/MyNPCs one, or one of our own.
     *
     * <p>{@link #apply} used to test {@link #isCustomNpc} directly, so for a <em>native</em> Xeno
     * NPC the whole method returned on its first line - no DragonMineZ stats blob, no flight, no
     * aggro range. The editor's strength and ki power went into our own NBT and stopped there,
     * which is why an NPC's battle power never moved when its stats changed.
     *
     * <p>The CustomNPCs-specific work further down is already guarded on its own reflection
     * returning null, so a native NPC passes straight through it rather than needing a second
     * branch.
     */
    public static boolean isManagedNpc(Entity entity) {
        return isCustomNpc(entity)
                || entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
    }

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
     *
     * <p>Native melee/resistance rewrite is separate: a profile {@link #force write} may pin
     * them, but a join/chunk-load/death {@link #forceLifecycle reload} must not, or MyNPCs'
     * own cloned stats snap back to 1 damage / 1.0 resistance every time something else
     * reloads the entity.
     */
    public static void apply(Entity entity, NpcCombatProfile profile, boolean force) {
        apply(entity, profile, force, true);
    }

    static void apply(Entity entity, NpcCombatProfile profile, boolean force, boolean rewriteNative) {
        if (entity == null || entity.level().isClientSide() || profile == null
                || !NpcCombatProfile.hasProfile(entity) || !isManagedNpc(entity)) {
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

        // The editor's numbers become a real DragonMineZ StatsData here, behind the same
        // authoritative gate and the same fingerprint as everything else this method pushes. DMZ's
        // own formulas can then answer for this NPC -- see NpcDmzStats for what an NPC blob can and
        // cannot be asked, given it has no player behind it.
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            NpcDmzStats.syncFromProfile(living, profile);
        }
        // Health reads StatsData.getHealthBonus(), so the profile-derived DMZ blob and its
        // character/form state must be applied first.
        NpcVitalitySync.apply(entity, profile);
        try {
            Object stats = stats(entity);
            if (stats != null && shouldRewriteNative(rewriteNative)) {
                Object melee = stats.getClass().getMethod("getMelee").invoke(stats);
                // profile.pinNativeCombat is the per-NPC escape from the overwrite below. Without
                // it there was no way to keep a hand-set damage or resistance: this runs again on
                // every profile save (write -> force skips the fingerprint gate entirely) and on any
                // DragonMineZ stat edit, since authorityFingerprint hashes all of them. An admin set
                // a value, touched a stat, and watched it revert with nothing to say why.
                //
                // Turning it off takes the restore path, which puts the backed-up natives back and
                // drops the backup -- so switching it on again captures what the admin has set now,
                // rather than what the NPC looked like the first time authority ever applied.
                boolean wroteNative;
                if (shouldPinNative(XenoServerConfig.npcDmzStatsAuthoritative, profile.pinNativeCombat)) {
                    backupNative(persistent, stats, melee);
                    pinNativeCombat(stats, melee);
                    wroteNative = true;
                } else {
                    wroteNative = restoreNative(persistent, stats, melee);
                }
                if (shouldMarkClientForNative(wroteNative)) {
                    markClientUpdate(entity);
                }
            }
            persistent.putInt(TAG_LAST_FINGERPRINT, fingerprint);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            warnOnce(failure);
        }
    }

    /**
     * Native melee/ranged/resistance overwrite. Independent of flight, aggro, vitality and the
     * DMZ blob, which still apply on a lifecycle reload even when this is false.
     */
    static boolean shouldPinNative(boolean dmzAuthoritative, boolean pinNativeCombat) {
        return dmzAuthoritative && pinNativeCombat;
    }

    /**
     * Join, chunk load, clone paste and death reset are reloads. They must not write MyNPCs
     * melee/resistance; only an explicit profile write (editor, command, script) may.
     */
    static boolean shouldRewriteNative(boolean profileWrite) {
        return profileWrite;
    }

    /**
     * Death/join used to set {@code updateClient} even when pin was off and restore had nothing
     * to write, which is the Stats GUI flash after respawn with native fields left alone.
     */
    static boolean shouldMarkClientForNative(boolean wroteNativeFields) {
        return wroteNativeFields;
    }

    public static void force(Entity entity, NpcCombatProfile profile) {
        force(entity, profile, true);
    }

    /**
     * Entity join, chunk load, wand/clone reload and death reset. Flight/aggro caches are stale
     * after MyNPCs rebuilds AI, and vitality/DMZ still need a refresh — native damage and
     * resistances stay whatever MyNPCs just loaded.
     */
    public static void forceLifecycle(Entity entity, NpcCombatProfile profile) {
        force(entity, profile, false);
    }

    private static void force(Entity entity, NpcCombatProfile profile, boolean rewriteNative) {
        // A respawn rebuilds CustomNPCs' AI from its own saved data, so the cached
        // last-applied navigator value is stale by definition on this path.
        NpcFlightBridge.force(entity, profile);
        NpcAggroBridge.forget(entity.getUUID());
        apply(entity, profile, true, rewriteNative);
    }

    private static void restoreAfterAuthority(Entity entity) {
        CompoundTag persistent = entity.getPersistentData();
        try {
            Object stats = stats(entity);
            if (stats != null) {
                boolean restored = restoreNative(persistent, stats,
                        stats.getClass().getMethod("getMelee").invoke(stats));
                if (shouldMarkClientForNative(restored)) {
                    markClientUpdate(entity);
                }
            }
            NpcVitalitySync.restoreNative(entity);
            // Giving authority back to CustomNPCs means giving up the DMZ blob too: leaving it
            // attached would keep StatsProvider.get resolving stats for an NPC that is no longer
            // supposed to have any, and ki damage would still read them.
            NpcDmzStats.clear(entity);
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

    private static void pinNativeCombat(Object stats, Object melee) throws ReflectiveOperationException {
        if (melee != null) {
            melee.getClass().getMethod("setStrength", int.class).invoke(melee, 1);
            melee.getClass().getMethod("setKnockback", int.class).invoke(melee, 0);
        }
        stats.getClass().getMethod("setHealthRegen", int.class).invoke(stats, 0);
        stats.getClass().getMethod("setCombatRegen", int.class).invoke(stats, 0);
        // Resistance-tab values remain native MyNPC state. Pinning DMZ combat must not rewrite
        // them when an unrelated DMZ profile field is saved.
        Object ranged = stats.getClass().getMethod("getRanged").invoke(stats);
        if (ranged != null) {
            ranged.getClass().getMethod("setStrength", int.class).invoke(ranged, 0);
            ranged.getClass().getMethod("setKnockback", int.class).invoke(ranged, 0);
            ranged.getClass().getMethod("setExplodeSize", int.class).invoke(ranged, 0);
        }
    }

    private static boolean restoreNative(CompoundTag persistent, Object stats, Object melee)
            throws ReflectiveOperationException {
        if (!persistent.contains(TAG_NATIVE_BACKUP, Tag.TAG_COMPOUND)) return false;
        CompoundTag backup = persistent.getCompound(TAG_NATIVE_BACKUP);
        stats.getClass().getMethod("setHealthRegen", int.class).invoke(stats, backup.getInt("HealthRegen"));
        stats.getClass().getMethod("setCombatRegen", int.class).invoke(stats, backup.getInt("CombatRegen"));
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
        return true;
    }

    private static void warnOnce(Exception failure) {
        if (warnedReflectionFailure) return;
        warnedReflectionFailure = true;
        XenoPixelsMod.LOGGER.warn("Could not synchronize authoritative NPC counterpart stats: {}",
                failure.toString());
    }
}
