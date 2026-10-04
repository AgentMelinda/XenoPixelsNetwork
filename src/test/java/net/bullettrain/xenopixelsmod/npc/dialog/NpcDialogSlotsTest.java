package net.bullettrain.xenopixelsmod.npc.dialog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Twelve dialogue slots on an NPC, matching My NPCs' editor one for one.
 *
 * <p>Their model is a shared library — categories of dialogues — with each NPC pointing at up to
 * twelve. Ours already stores dialogues that way, so the slot is a reference, never a copy.
 *
 * <p>That distinction is the whole point: twenty guards who greet a player the same way share one
 * dialogue, and fixing its typo fixes all twenty. A copy per NPC, which the older editor pages
 * make, needs twenty edits.
 */
class NpcDialogSlotsTest {

    @Test
    void thereAreAlwaysTwelveSlotsWhetherFilledOrNot() {
        // The grid draws every slot; a short list would leave holes in the layout.
        assertEquals(NpcDialogSlots.MAX_SLOTS, new NpcDialogSlots().all().size());
        assertEquals(12, NpcDialogSlots.MAX_SLOTS, "two columns of six, as their editor lays out");
    }

    @Test
    void aFreshNpcHasNothingAssigned() {
        NpcDialogSlots slots = new NpcDialogSlots();
        assertTrue(slots.isEmpty());
        assertFalse(slots.get(0).assigned());
    }

    @Test
    void aSlotNeedsBothACategoryAndADialogue() {
        // One half alone cannot name a file in a grouped store category, so it is not an
        // assignment - it is a half-finished click, and reads as empty.
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "dbz_01", "");
        assertFalse(slots.get(0).assigned());

        slots.set(0, "", "hey_its_me_goku");
        assertFalse(slots.get(0).assigned());

        slots.set(0, "dbz_01", "hey_its_me_goku");
        assertTrue(slots.get(0).assigned());
    }

    @Test
    void anAssignmentIsNormalisedTheWayStoreIdsAre() {
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "  DBZ_01  ", "  Hey_Its_Me_Goku ");
        assertEquals("dbz_01", slots.get(0).group());
        assertEquals("hey_its_me_goku", slots.get(0).id());
    }

    @Test
    void clearingASlotLeavesTheOthersAlone() {
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "a", "one");
        slots.set(5, "b", "two");
        slots.clear(0);
        assertFalse(slots.get(0).assigned());
        assertTrue(slots.get(5).assigned());
    }

    @Test
    void anOutOfRangeSlotIsIgnoredRatherThanThrowing() {
        // The grid addresses slots by index and a stale screen can outlive a cap change.
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(99, "a", "one");
        slots.set(-1, "a", "one");
        assertTrue(slots.isEmpty());
        assertFalse(slots.get(99).assigned());
    }

    @Test
    void thePrimaryIsTheFirstAssignedSlotNotTheFirstSlot() {
        // Slot 0 is often empty while slot 3 holds the greeting; an ordinary right-click should
        // still find something to say.
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(3, "dbz_01", "greeting");
        assertEquals("greeting", slots.primary().id());
    }

    @Test
    void assignmentsSurviveASaveAndLoadInTheirSlots() {
        // Slot number is meaningful - it is what the grid shows and what an author arranges.
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "dbz_01", "greeting");
        slots.set(7, "grindables", "farewell");

        CompoundTag tag = new CompoundTag();
        slots.saveTo(tag);
        NpcDialogSlots read = new NpcDialogSlots();
        read.loadFrom(tag);

        assertEquals("greeting", read.get(0).id());
        assertEquals("farewell", read.get(7).id());
        assertFalse(read.get(1).assigned(), "the gap between them stays a gap");
    }

    @Test
    void anNpcWithNoDialoguesWritesNothingAtAll() {
        // Every NPC carries this object and almost none are conversationalists; twelve empty
        // entries on every NPC in a world is a cost with no reader.
        CompoundTag tag = new CompoundTag();
        new NpcDialogSlots().saveTo(tag);
        assertTrue(tag.isEmpty());
    }

    @Test
    void loadingReplacesRatherThanMerges() {
        NpcDialogSlots slots = new NpcDialogSlots();
        slots.set(0, "old", "one");

        CompoundTag empty = new CompoundTag();
        slots.loadFrom(empty);
        assertTrue(slots.isEmpty(), "a tag with no slots means no slots");
    }

    @Test
    void aTagClaimingMoreThanTwelveIsTruncated() {
        CompoundTag tag = new CompoundTag();
        ListTag oversized = new ListTag();
        for (int i = 0; i < 100; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("G", "g");
            entry.putString("I", "d" + i);
            oversized.add(entry);
        }
        tag.put("DialogSlots", oversized);

        NpcDialogSlots read = new NpcDialogSlots();
        read.loadFrom(tag);
        assertEquals(NpcDialogSlots.MAX_SLOTS, read.all().size());
        assertEquals("d11", read.get(11).id(), "the first twelve, in order");
    }

    @Test
    void copyFromTakesEverySlotIncludingTheEmptyOnes() {
        NpcDialogSlots source = new NpcDialogSlots();
        source.set(2, "a", "one");

        NpcDialogSlots target = new NpcDialogSlots();
        target.set(0, "stale", "leftover");
        target.copyFrom(source);

        assertEquals("one", target.get(2).id());
        assertFalse(target.get(0).assigned(), "a slot the source left empty must not survive");
    }
}
