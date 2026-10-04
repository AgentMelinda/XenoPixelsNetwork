package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A faction as a store file.
 *
 * <p>Field-for-field with My NPCs' own faction entry, under our names, so a later import is a
 * rename:
 *
 * <pre>
 *   ours              theirs
 *   Name              Name
 *   Color             Color
 *   HostileTo         AttackFactions
 *   DefaultStanding   DefaultPoints
 *   AttackedByMobs    GetsAttacked
 *   -                 HideFaction        not carried: nothing reads it
 *   -                 FriendlyPoints     not carried: ours is a constant
 *   -                 NeutralPoints      not carried: ours is a constant
 *   -                 Slot               not carried: the filename is the id
 * </pre>
 *
 * <p>The four omissions are deliberate and belong in the schema doc rather than in the file.
 * {@code FriendlyPoints}/{@code NeutralPoints} are per-faction thresholds there and compile-time
 * constants here ({@code XenoFaction.FRIENDLY_AT}, {@code HOSTILE_BELOW}); writing them would be a
 * stored field nothing reads, which is the shape this codebase keeps out. The importer drops them
 * with a logged note, the way {@code NpcCloneConverter.droppedKeys} already reports what it cannot
 * rehome.
 */
public final class XenoFactionNbt {

    private static final String NAME = "Name";
    private static final String COLOR = "Color";
    private static final String HOSTILE = "HostileTo";
    private static final String STANDING = "DefaultStanding";
    private static final String ATTACKED_BY_MOBS = "AttackedByMobs";
    private static final String AGGRESSIVE_TO_MOBS = "AggressiveToMobs";
    private static final String ATTACKABLE_MOBS = "AttackableMobs";

    /** A faction's own id is its filename, so it is not repeated inside. */
    private XenoFactionNbt() {
    }

    public static CompoundTag write(XenoFaction faction) {
        CompoundTag tag = new CompoundTag();
        if (faction == null) {
            return tag;
        }
        tag.putString(NAME, faction.name());
        tag.putInt(COLOR, faction.color() & 0xFFFFFF);
        ListTag hostile = new ListTag();
        for (String id : faction.hostileTo()) {
            if (id != null && !id.isBlank()) {
                hostile.add(StringTag.valueOf(id));
            }
        }
        tag.put(HOSTILE, hostile);
        tag.putInt(STANDING, faction.defaultStanding());
        tag.putBoolean(ATTACKED_BY_MOBS, faction.attackedByMobs());
        tag.putBoolean(AGGRESSIVE_TO_MOBS, faction.aggressiveToMobs());
        ListTag mobs = new ListTag();
        for (String id : faction.attackableMobs()) mobs.add(StringTag.valueOf(id));
        tag.put(ATTACKABLE_MOBS, mobs);
        return tag;
    }

    /**
     * Reads a faction, or null when the tag holds none.
     *
     * <p>Null rather than a blank faction: a caller falls through to the datapack on null, and an
     * empty-but-present faction would shadow the pack's and leave an NPC belonging to nothing.
     *
     * @param id the filename, which is the faction's id
     */
    public static XenoFaction read(String id, CompoundTag tag) {
        if (tag == null || id == null || id.isBlank()) {
            return null;
        }
        List<String> hostile = new ArrayList<>();
        ListTag list = tag.getList(HOSTILE, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isBlank()) {
                hostile.add(value);
            }
        }
        // The record's constructor lower-cases the id, clamps the standing and copies the list, so
        // a hand-edited file cannot put a faction outside the range every comparison assumes.
        List<String> mobs = new ArrayList<>();
        ListTag mobList = tag.getList(ATTACKABLE_MOBS, Tag.TAG_STRING);
        for (int i = 0; i < mobList.size(); i++) mobs.add(mobList.getString(i));
        return new XenoFaction(id,
                tag.contains(NAME) ? tag.getString(NAME) : id,
                tag.getInt(COLOR),
                hostile,
                tag.getInt(STANDING),
                tag.getBoolean(ATTACKED_BY_MOBS),
                tag.getBoolean(AGGRESSIVE_TO_MOBS), mobs);
    }
}
