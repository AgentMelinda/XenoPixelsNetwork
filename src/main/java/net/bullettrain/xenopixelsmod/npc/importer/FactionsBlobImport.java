package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Their single {@code factions.dat} blob, fanned out into our per-entry factions.
 *
 * <p>My NPCs keeps factions, banks, transport, recipes and spawns as one gzipped {@code .dat}
 * each. Ours are a folder of one file per entry, so that one can be diffed, hand-edited, backed up
 * and — the part that matters — <b>fail alone</b>: one bad byte in a combined blob takes every
 * faction with it. The fan-out is a cost paid once here rather than every day by the operator.
 *
 * <h2>Why the list is found by shape, not by name</h2>
 *
 * <p>Every {@code .dat} sampled while writing {@code docs/xeno-npc-schema.md} held an <em>empty</em>
 * list, so the key its entries live under was never confirmed. Guessing a name and quietly
 * importing nothing when the guess is wrong is the worst available outcome: it looks exactly like
 * a world that had no factions.
 *
 * <p>So this looks for a list of compounds by structure. Exactly one is used. Zero or several is a
 * loud failure that <b>names every top-level key it did find</b>, so whoever hits it can report the
 * real shape and this can be made exact rather than tolerant.
 */
public final class FactionsBlobImport {

    /** The blob was not shaped as expected. Names what was there, so it can be fixed properly. */
    public static final class UnknownShape extends RuntimeException {
        public UnknownShape(String message) {
            super(message);
        }
    }

    private FactionsBlobImport() {
    }

    /**
     * Converts one blob into our factions.
     *
     * <p>Two passes, and it has to be two: a faction can be hostile to one that appears later in
     * the list, so every id must exist before any hostility is resolved. A single pass would drop
     * every forward reference and report it as an unknown slot.
     */
    public static List<XenoFaction> fanOut(CompoundTag blob, NpcImportReport report) {
        ListTag entries = findEntryList(blob);
        SlotIndex index = new SlotIndex();

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            // Their own Slot when it has one, otherwise the position in the list. Position is a
            // fallback rather than the rule, because hostilities reference the stored slot.
            int slot = entry.contains("Slot") ? entry.getInt("Slot") : i;
            slots.add(slot);
            index.assign(slot, entry.contains("Name") ? entry.getString("Name") : null);
        }
        for (String rename : index.renames()) {
            report.note(rename);
        }

        List<XenoFaction> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int slot = slots.get(i);
            try {
                out.add(FactionImport.convert(slot, entries.getCompound(i), index, report));
            } catch (RuntimeException e) {
                // One entry failing costs one entry. A blob is already a single point of failure;
                // letting one bad faction take the other forty would make that worse.
                report.failed("factions", "slot " + slot, e.getMessage());
            }
        }
        return out;
    }

    /** Slot mapping used while converting placed NPCs that point at this same faction blob. */
    public static java.util.Map<Integer, String> slotIds(CompoundTag blob) {
        ListTag entries = findEntryList(blob);
        SlotIndex index = new SlotIndex();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.contains("Slot") ? entry.getInt("Slot") : i;
            index.assign(slot, entry.contains("Name") ? entry.getString("Name") : null);
        }
        java.util.Map<Integer, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.contains("Slot") ? entry.getInt("Slot") : i;
            result.put(slot, index.idFor(slot));
        }
        return java.util.Map.copyOf(result);
    }

    /**
     * The one list of compounds in the blob.
     *
     * @throws UnknownShape when there is not exactly one, naming what was found instead
     */
    static ListTag findEntryList(CompoundTag blob) {
        if (blob == null) {
            throw new UnknownShape("no tag at all");
        }
        ListTag found = null;
        String foundKey = null;
        List<String> others = new ArrayList<>();
        for (String key : blob.getAllKeys()) {
            Tag value = blob.get(key);
            if (value instanceof ListTag list
                    && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND)) {
                if (found != null) {
                    throw new UnknownShape("expected one list of entries, found at least two: "
                            + foundKey + " and " + key
                            + " - please report this file's shape so the reader can be made exact");
                }
                found = list;
                foundKey = key;
                continue;
            }
            others.add(key + " (" + (value == null ? "null" : value.getType().getName()) + ")");
        }
        if (found == null) {
            throw new UnknownShape("no list of entries; top-level keys were "
                    + (others.isEmpty() ? "none" : String.join(", ", others))
                    + " - please report this file's shape so the reader can be made exact");
        }
        return found;
    }
}
