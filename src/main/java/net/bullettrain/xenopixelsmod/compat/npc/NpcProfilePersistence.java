package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/**
 * Copies the combat profile into CustomNPCs / My NPCs' own NBT tree.
 *
 * <p>Vanilla {@code ForgeData} already stores {@link NpcCombatProfile#NBT_KEY}, but the wand's
 * {@code writeSpawnData} packet and clone files are a CNPC-owned tag that never includes
 * ForgeData. A world restart after a wand save therefore kept the CNPC MaxHealth snapshot and
 * dropped VIT/STR/form. Writing the same compound under {@link #CNPC_KEY} makes every CNPC
 * save path carry the profile, and reading it back restores ForgeData before vitality is applied.
 */
public final class NpcProfilePersistence {
    /** Top-level CNPC/MyNPCs NBT key. Unnamespaced so clone converters keep it. */
    public static final String CNPC_KEY = "XenoPixelsProfile";

    private NpcProfilePersistence() {}

    public static void writeToNpcTag(Entity entity, CompoundTag npcTag) {
        if (entity == null || npcTag == null) return;
        CompoundTag persistent = entity.getPersistentData();
        CompoundTag profile = persistent.contains(NpcCombatProfile.NBT_KEY, Tag.TAG_COMPOUND)
                ? persistent.getCompound(NpcCombatProfile.NBT_KEY)
                : persistent.getCompound(CNPC_KEY);
        if (profile.isEmpty()) return;
        npcTag.put(CNPC_KEY, profile.copy());
    }

    public static void readFromNpcTag(Entity entity, CompoundTag npcTag) {
        if (entity == null || npcTag == null
                || !npcTag.contains(CNPC_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag stored = npcTag.getCompound(CNPC_KEY);
        if (stored.isEmpty()) return;
        entity.getPersistentData().put(NpcCombatProfile.NBT_KEY, stored.copy());
        entity.getPersistentData().put(CNPC_KEY, stored.copy());
        if (!entity.level().isClientSide()) {
            NpcVitalitySync.apply(entity, NpcCombatProfile.read(entity));
        }
    }
}
