package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;

/**
 * The version stamped on every store file, and what to do with an old one.
 *
 * <p>Per file rather than per folder, which is what both reference mods do: My NPCs writes
 * {@code ModRev} into each dialogue it saves, currently 18, with upgrade steps keyed on the
 * versions where its shape changed. A per-file stamp means one file written by an older build can
 * be brought forward without touching its neighbours.
 *
 * <p>The key is {@code Schema}, the same spelling {@code NpcCombatProfile} already uses - a clone
 * file <em>is</em> a profile payload, so a second name would put two version numbers in one tag.
 *
 * <p>Three rules:
 * <ul>
 *   <li><b>Absent</b> reads as 1, the same defensive default {@code XenoNpcData.fromTag} applies to
 *       a field written before it existed. Reading a missing version as 0 would re-run migrations
 *       that had already been applied.</li>
 *   <li><b>Older</b> is upgraded on the way into memory. The file on disk is left alone until the
 *       entry is next saved, so a mistaken migration cannot rewrite a world before anyone sees it.</li>
 *   <li><b>Newer</b> is refused outright - not read, not deleted, not overwritten - and the whole
 *       category goes read-only for the session. Reading a newer file would drop the fields this
 *       build does not know about; writing over it would then make that loss permanent.</li>
 * </ul>
 */
public final class XenoNpcStoreSchema {

    /** The key, shared with {@code NpcCombatProfile} on purpose. */
    public static final String TAG = "Schema";

    /** What this build writes. */
    public static final int CURRENT = 1;

    private XenoNpcStoreSchema() {
    }

    /** The version a tag declares. An absent stamp is version 1. */
    public static int versionOf(CompoundTag tag) {
        if (tag == null) {
            return CURRENT;
        }
        return tag.contains(TAG, net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt(TAG) : 1;
    }

    /** Whether a tag was written by a build newer than this one. */
    public static boolean isFromTheFuture(CompoundTag tag) {
        return versionOf(tag) > CURRENT;
    }

    /**
     * Brings a tag forward to {@link #CURRENT}.
     *
     * <p>There is nothing to do yet - version 1 is the first. The method exists and is called from
     * day one so that the shape is exercised before it matters: a migration hook added the first
     * time it is needed is a migration hook nobody has ever run.
     */
    public static CompoundTag upgrade(XenoNpcStoreCategory category, CompoundTag tag, int from) {
        if (tag == null || from >= CURRENT) {
            return tag;
        }
        CompoundTag upgraded = tag.copy();
        // Steps go here as `if (from < N) { ... }`, in order, each one a single version hop.
        upgraded.putInt(TAG, CURRENT);
        return upgraded;
    }
}
