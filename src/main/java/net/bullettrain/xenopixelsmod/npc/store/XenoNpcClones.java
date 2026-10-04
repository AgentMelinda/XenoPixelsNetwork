package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.npc.XenoNpcData;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared library of saved NPC templates.
 *
 * <p>My NPCs' cloner keeps its clones in nine numbered tabs; this is the same thing on the world
 * store, so a template saved by one operator is there for the next. The Cloner item carries one
 * template on the stack for immediate reuse — this is what you reach for when the template should
 * outlive the item.
 *
 * <p><b>Retires the reservation.</b> {@link XenoNpcStoreCategory#CLONES} has existed with no reader
 * since the store was written, and its own note said what would unblock it: <em>"a path that applies
 * a stored template to a live or newly placed NPC; until something can spend a clone there is no
 * point writing one."</em> The Cloner item is that path, so the category can finally be read.
 *
 * <p>A stored clone is the same payload the item carries and the editor opens with — see
 * {@code XenoNpcPayload}. One format, so a template saved from the library and one carried on a
 * stack cannot disagree about what an NPC is.
 */
public final class XenoNpcClones {

    /** Tabs 1 through 9, as theirs are. */
    public static final int MIN_TAB = 1;
    public static final int MAX_TAB = 9;

    /** How many templates one tab may hold. Past this a tab is a list nobody can read. */
    public static final int MAX_PER_TAB = 64;

    private XenoNpcClones() {
    }

    /** A tab number as the store's group folder. */
    public static String group(int tab) {
        return String.valueOf(clampTab(tab));
    }

    /** A tab number inside the range, so a bad one lands on tab 1 rather than writing nowhere. */
    public static int clampTab(int tab) {
        return Math.max(MIN_TAB, Math.min(MAX_TAB, tab));
    }

    /**
     * Saves an NPC as a template.
     *
     * @return null on success, or why it was refused
     */
    @Nullable
    public static String save(XenoNpcEntity npc, int tab, String id) {
        if (npc == null) {
            return "no NPC to save";
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the world store is not loaded";
        }
        String bad = XenoNpcStorePaths.reject(id);
        if (bad != null) {
            return bad;
        }
        String folder = group(tab);
        // Counted before writing: a tab that has hit the cap refuses rather than growing past the
        // point where the list is usable. Replacing an existing id is always allowed - that is an
        // overwrite, not growth.
        if (store.get(XenoNpcStoreCategory.CLONES, folder, id) == null
                && ids(tab).size() >= MAX_PER_TAB) {
            return "tab " + folder + " is full at " + MAX_PER_TAB + " templates";
        }

        CompoundTag payload = XenoNpcData.editorPayload(npc, npc.npcData());
        if (NpcSlotStack.tagBytes(payload) > net.bullettrain.xenopixelsmod.item.custom
                .XenoNpcPayload.MAX_BYTES) {
            return "that NPC is too large to save";
        }
        return store.put(XenoNpcStoreCategory.CLONES, folder, id, payload);
    }

    /** One saved template, or null. */
    @Nullable
    public static CompoundTag load(int tab, String id) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null || id == null || id.isEmpty()) {
            return null;
        }
        return store.get(XenoNpcStoreCategory.CLONES, group(tab), id);
    }

    /**
     * The template ids in one tab.
     *
     * <p>Filtered out of the whole category rather than asked for per tab, because the store lists a
     * category at a time and a clone's group <em>is</em> its tab.
     */
    public static List<String> ids(int tab) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        String folder = group(tab);
        List<String> out = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.CLONES)) {
            if (folder.equals(entry.group())) {
                out.add(entry.id());
            }
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    /** How many templates are saved across every tab. */
    public static int total() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null ? 0 : store.list(XenoNpcStoreCategory.CLONES).size();
    }
}
