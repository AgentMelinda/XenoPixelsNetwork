package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogues;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFaction;
import net.bullettrain.xenopixelsmod.npc.faction.XenoFactions;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where content comes from, now that it can come from two places.
 *
 * <p>The world store wins over the datapack, per id. That is not a new rule - it is the one
 * {@code ParallelQuests.definition} already applies, where a pack-defined quest shadows a built-in
 * one. Adding a second, different precedence for a sibling category would be exactly the drift this
 * is meant to avoid, so the stack simply grows by one: <b>store, then datapack, then built-in</b>.
 *
 * <p>Shadowing rather than replacing matters for two reasons. A pack's shipped content keeps
 * working the moment an operator saves their first entry, instead of disappearing. And deleting a
 * world entry means something: the pack's version comes back, rather than leaving a hole. That is
 * the same reversibility that makes {@code NpcWorldMigrator}'s never-overwrite rule safe to run
 * unattended.
 *
 * <p>The datapack loaders themselves are untouched. {@code XenoDialogues.apply} still means
 * exactly "what the pack defines"; precedence lives here and nowhere else.
 */
public final class XenoNpcDataSource {

    /** Dialogues in the store are grouped, as My NPCs groups its dialogs by category. */
    public static final String DEFAULT_GROUP = "npc";

    private XenoNpcDataSource() {
    }

    /**
     * A dialogue by id, from the world store if it has one, otherwise from the datapack.
     *
     * <p>A stored entry that will not parse falls through to the pack rather than leaving the NPC
     * silent - an unreadable override should cost the override, not the conversation.
     */
    public static XenoDialogue dialogue(String id) {
        CompoundTag stored = storedDialogue(id);
        if (stored != null) {
            XenoDialogue parsed = XenoDialogueNbt.read(stored);
            if (parsed != null) {
                return parsed;
            }
        }
        return XenoDialogues.get(id);
    }

    /** Resolve an NPC slot by both parts of its stored reference. */
    public static XenoDialogue assignedDialogue(String group, String id) {
        return assignedDialogue(XenoNpcStores.get(), group, id);
    }

    static XenoDialogue assignedDialogue(XenoNpcWorldStore store, String group, String id) {
        if (store == null || group == null || id == null || group.isBlank() || id.isBlank()) {
            return null;
        }
        return XenoDialogueNbt.read(store.get(XenoNpcStoreCategory.DIALOGS, group, id));
    }

    private static CompoundTag storedDialogue(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null || id == null || id.isBlank()) {
            return null;
        }
        String key = bare(id);
        // Any group, because a dialogue is addressed by id and the group is only a folder for an
        // operator's benefit. First match in load order wins, which is sorted and therefore stable.
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.DIALOGS)) {
            if (entry.id().equals(key)) {
                return entry.tag();
            }
        }
        return null;
    }

    /** A faction by id, store first. */
    public static XenoFaction faction(String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store != null && id != null && !id.isBlank()) {
            CompoundTag stored = store.get(XenoNpcStoreCategory.FACTIONS, "", bare(id));
            if (stored != null) {
                XenoFaction parsed = XenoFactionNbt.read(bare(id), stored);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return XenoFactions.get(id);
    }

    /**
     * Every faction id that resolves, store and datapack together.
     *
     * <p>Store first so its order leads, then any pack id it does not shadow. A {@code
     * LinkedHashSet} because an id defined in both must appear once, and because the order a cycler
     * walks should not change between reloads.
     */
    public static List<String> factionIds() {
        Set<String> ids = new LinkedHashSet<>();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store != null) {
            for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.FACTIONS)) {
                ids.add(entry.id());
            }
        }
        ids.addAll(XenoFactions.ids());
        return List.copyOf(ids);
    }

    /** Every faction that resolves, in {@link #factionIds()} order, skipping any that will not. */
    public static List<XenoFaction> factions() {
        List<XenoFaction> out = new ArrayList<>();
        for (String id : factionIds()) {
            XenoFaction faction = faction(id);
            if (faction != null) {
                out.add(faction);
            }
        }
        return List.copyOf(out);
    }

    /**
     * The id a store file is named by.
     *
     * <p>A ref may be written as {@code xenopixelsmod:npcs/dialogue/default} or as the bare
     * {@code default}; the store keys by the last path segment, which is what the filename is.
     */
    private static String bare(String ref) {
        String value = ref.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }
}
