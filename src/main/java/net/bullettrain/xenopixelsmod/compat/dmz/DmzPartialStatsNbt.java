package net.bullettrain.xenopixelsmod.compat.dmz;

import net.minecraft.nbt.CompoundTag;

/**
 * DragonMineZ {@code ResourceSyncS2C} writes only {@code Resources} and {@code Status}.
 * {@code StatsData.load} treats a missing {@code PlayerQuestData} as fatal, so a cheap
 * resource packet must never go through that loader.
 */
public final class DmzPartialStatsNbt {
    public static final String PLAYER_QUEST_DATA = "PlayerQuestData";
    public static final String RESOURCES = "Resources";
    public static final String STATUS = "Status";

    private DmzPartialStatsNbt() {
    }

    public static boolean isFullBlob(CompoundTag nbt) {
        return nbt != null && nbt.contains(PLAYER_QUEST_DATA);
    }

    public static boolean shouldMergePartially(CompoundTag nbt) {
        return nbt != null && !isFullBlob(nbt);
    }

    public static CompoundTag resources(CompoundTag nbt) {
        return child(nbt, RESOURCES);
    }

    public static CompoundTag status(CompoundTag nbt) {
        return child(nbt, STATUS);
    }

    private static CompoundTag child(CompoundTag nbt, String key) {
        if (nbt == null || !nbt.contains(key)) {
            return null;
        }
        return nbt.getCompound(key);
    }
}
