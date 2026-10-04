package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CustomNPCs numbers for native factions, dialogs and quests.
 *
 * <p>CustomNPCs and My NPCs address all three by an int slot; native content is keyed by string
 * ids. Only content imported from one of those mods has a number: the import writes it as
 * {@code SourceMod}/{@code SourceSlot} on the store entry. Content imported before those tags were
 * written is recognised by the id the importer gave it ({@code <group>_q<slot>} for quests,
 * {@code dialog_<slot>} in a {@code mynpcs_}/{@code customnpcs_} group for dialogs). Xeno-made
 * content has no number: its id is {@link #NONE}, and scripts reach it through the handler lists or
 * by name.
 *
 * <p>A number that two entries claim resolves to nothing rather than to whichever loaded first,
 * so a script can never act on the wrong one of two.
 */
public final class XenoScriptIds {
    /** What {@code getId()} answers for content that has no CustomNPCs number. */
    public static final int NONE = -1;
    public static final String TAG_SOURCE_MOD = "SourceMod";
    public static final String TAG_SOURCE_SLOT = "SourceSlot";

    private static final Pattern QUEST_ID = Pattern.compile(".+_q(\\d{1,9})");
    private static final Pattern DIALOG_ID = Pattern.compile("dialog_(\\d{1,9})");

    private XenoScriptIds() {}

    /** A store entry that a number resolved to. */
    public record Ref(String group, String id) {}

    static boolean foreignMod(String mod) {
        return "mynpcs".equalsIgnoreCase(mod) || "customnpcs".equalsIgnoreCase(mod);
    }

    /** The CustomNPCs number a store entry carries, or {@link #NONE}. */
    public static int slotOf(XenoNpcStoreCategory category, String group, String id, CompoundTag tag) {
        if (tag != null && tag.contains(TAG_SOURCE_SLOT) && foreignMod(tag.getString(TAG_SOURCE_MOD))) {
            return tag.getInt(TAG_SOURCE_SLOT);
        }
        if (tag != null && tag.contains(TAG_SOURCE_SLOT)) return NONE;
        if (id == null) return NONE;
        if (category == XenoNpcStoreCategory.QUESTS) {
            Matcher matcher = QUEST_ID.matcher(id);
            return matcher.matches() ? Integer.parseInt(matcher.group(1)) : NONE;
        }
        if (category == XenoNpcStoreCategory.DIALOGS) {
            Matcher matcher = DIALOG_ID.matcher(id);
            boolean imported = group != null && (group.startsWith("mynpcs_") || group.startsWith("customnpcs_"));
            return imported && matcher.matches() ? Integer.parseInt(matcher.group(1)) : NONE;
        }
        return NONE;
    }

    /** The one store entry carrying {@code slot}; null for none or for an ambiguous number. */
    public static Ref resolve(XenoNpcStoreCategory category, int slot) {
        if (slot < 0) return null;
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) return null;
        Ref match = null;
        for (XenoNpcWorldStore.Entry entry : store.list(category)) {
            if (slotOf(category, entry.group(), entry.id(), entry.tag()) != slot) continue;
            Ref ref = new Ref(entry.group(), entry.id());
            if (match != null && !match.equals(ref)) return null;
            match = ref;
        }
        return match;
    }

    /** The CustomNPCs number of a stored entry, looked up by its native reference. */
    public static int slotOf(XenoNpcStoreCategory category, String group, String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null || id == null) return NONE;
        if (group == null) {
            for (XenoNpcWorldStore.Entry entry : store.list(category)) {
                if (entry.id().equals(id)) return slotOf(category, entry.group(), entry.id(), entry.tag());
            }
            return NONE;
        }
        CompoundTag tag = store.get(category, group, id);
        return tag == null ? NONE : slotOf(category, group, id, tag);
    }

    public static String factionId(int slot) {
        Ref ref = resolve(XenoNpcStoreCategory.FACTIONS, slot);
        return ref == null ? null : ref.id();
    }

    public static int factionSlot(String factionId) {
        return slotOf(XenoNpcStoreCategory.FACTIONS, "", factionId);
    }

    public static Ref dialog(int slot) {
        return resolve(XenoNpcStoreCategory.DIALOGS, slot);
    }

    public static String questId(int slot) {
        Ref ref = resolve(XenoNpcStoreCategory.QUESTS, slot);
        return ref == null ? null : ref.id();
    }

    public static int questSlot(String questId) {
        return slotOf(XenoNpcStoreCategory.QUESTS, null, questId);
    }

    /**
     * Copies the CustomNPCs number from the entry being replaced onto a replacement that lacks it,
     * so saving an imported faction, dialog or quest from the editor keeps its number.
     */
    public static void carrySourceSlot(CompoundTag previous, CompoundTag replacement) {
        if (previous == null || replacement == null || replacement.contains(TAG_SOURCE_SLOT)) return;
        if (!previous.contains(TAG_SOURCE_SLOT)) return;
        replacement.putInt(TAG_SOURCE_SLOT, previous.getInt(TAG_SOURCE_SLOT));
        if (previous.contains(TAG_SOURCE_MOD) && !replacement.contains(TAG_SOURCE_MOD)) {
            replacement.putString(TAG_SOURCE_MOD, previous.getString(TAG_SOURCE_MOD));
        }
    }
}
