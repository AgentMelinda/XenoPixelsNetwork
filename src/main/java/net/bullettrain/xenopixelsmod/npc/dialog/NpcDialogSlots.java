package net.bullettrain.xenopixelsmod.npc.dialog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dialogues one NPC offers, assigned to numbered slots.
 *
 * <p>My NPCs' model, which this matches deliberately: dialogues live in a <b>shared library</b>
 * organised into categories, and an NPC points at up to twelve of them. Ours already stores them
 * that way — {@code XenoNpcStoreCategory.DIALOGS} is a grouped category, so
 * {@code dialogs/&lt;category&gt;/&lt;id&gt;.json} is the same shape their
 * {@code dialogs/&lt;category&gt;/&lt;id&gt;.json} is.
 *
 * <p>The alternative, a dialogue tree written into each NPC's own profile, is what the editor's
 * older Dialogs pages do. It is easier to reach and much worse to author with: twenty guards who
 * should greet a player the same way need twenty copies, and fixing a typo means editing all
 * twenty. A reference costs one indirection and makes the library reusable, which is the whole
 * reason their editor is built this way.
 *
 * <p>A slot holds a reference, never a copy. A reference to a dialogue that has since been deleted
 * resolves to nothing and the slot reads as empty, rather than resurrecting a stale copy of
 * something an operator removed on purpose.
 */
public final class NpcDialogSlots {

    /** Twelve, because that is how many their editor lays out — two columns of six. */
    public static final int MAX_SLOTS = 12;

    private static final String TAG_SLOTS = "DialogSlots";
    private static final String TAG_GROUP = "G";
    private static final String TAG_ID = "I";

    /**
     * One assignment: which category the dialogue lives in, and which dialogue.
     *
     * <p>Both blank is an empty slot, which is the normal state of most of the twelve.
     */
    public record Slot(String group, String id) {
        public Slot {
            group = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        }

        public static Slot empty() {
            return new Slot("", "");
        }

        /** A slot needs both halves; one alone cannot name a file in a grouped category. */
        public boolean assigned() {
            return !group.isEmpty() && !id.isEmpty();
        }
    }

    private final List<Slot> slots = new ArrayList<>();

    public NpcDialogSlots() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots.add(Slot.empty());
        }
    }

    /** All twelve, always — the grid draws every slot whether or not it is filled. */
    public List<Slot> all() {
        return List.copyOf(slots);
    }

    public Slot get(int index) {
        return index >= 0 && index < MAX_SLOTS ? slots.get(index) : Slot.empty();
    }

    /** Assigns one slot, or clears it when either half is blank. */
    public void set(int index, String group, String id) {
        if (index < 0 || index >= MAX_SLOTS) {
            return;
        }
        Slot slot = new Slot(group, id);
        slots.set(index, slot.assigned() ? slot : Slot.empty());
    }

    public void clear(int index) {
        set(index, "", "");
    }

    /** Whether this NPC points at any dialogue at all. */
    public boolean isEmpty() {
        for (Slot slot : slots) {
            if (slot.assigned()) {
                return false;
            }
        }
        return true;
    }

    /** The first assigned slot, which is the one an ordinary right-click opens. */
    public Slot primary() {
        for (Slot slot : slots) {
            if (slot.assigned()) {
                return slot;
            }
        }
        return Slot.empty();
    }

    public void copyFrom(NpcDialogSlots other) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots.set(i, other == null ? Slot.empty() : other.get(i));
        }
    }

    /**
     * Written only when something is assigned.
     *
     * <p>Every NPC carries this object and almost none of them are conversationalists; twelve
     * empty entries on every NPC in a world is a cost with no reader.
     */
    public void saveTo(CompoundTag tag) {
        if (isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Slot slot : slots) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_GROUP, slot.group());
            entry.putString(TAG_ID, slot.id());
            list.add(entry);
        }
        tag.put(TAG_SLOTS, list);
    }

    public void loadFrom(CompoundTag tag) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots.set(i, Slot.empty());
        }
        if (tag == null || !tag.contains(TAG_SLOTS)) {
            return;
        }
        ListTag list = tag.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_SLOTS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            set(i, entry.getString(TAG_GROUP), entry.getString(TAG_ID));
        }
    }
}
