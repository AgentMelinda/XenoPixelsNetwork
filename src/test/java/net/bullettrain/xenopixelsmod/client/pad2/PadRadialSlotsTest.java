package net.bullettrain.xenopixelsmod.client.pad2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The widened radial's ordering rules.
 *
 * <p>Pure, and that is the point of the class existing at all: the rules below decide what a
 * player sees on a ring they open mid-fight, and none of them need a controller, a client, or
 * Controlify on the classpath to check.
 *
 * <p>The rule doing the most work is the first one. Controlify's eight come through untouched
 * <em>including their empty slots</em> — closing a gap would rotate every action after it onto a
 * different angle, which a player would experience as their radial rearranging itself.
 */
class PadRadialSlotsTest {

    private static final String EMPTY = PadRadialSlots.EMPTY_ACTION;

    private static List<String> stock(String... ids) {
        return List.of(ids);
    }

    // ------------------------------------------------------------ merge

    @Test
    void controlifysOwnEntriesComeFirstAndInItsOrder() {
        List<String> merged = PadRadialSlots.merge(
                stock("a:one", "a:two", "a:three"), List.of("b:extra"));
        assertEquals(List.of("a:one", "a:two", "a:three", "b:extra"), merged);
    }

    @Test
    void anEmptySlotControlifyLeftIsPreservedRatherThanClosedUp() {
        // The trap. Dropping the empty would move a:three from angle 3 to angle 2 and every extra
        // with it - the radial would look rearranged after an unrelated change.
        List<String> merged = PadRadialSlots.merge(
                stock("a:one", EMPTY, "a:three"), List.of("b:extra"));
        assertEquals(List.of("a:one", EMPTY, "a:three", "b:extra"), merged);
    }

    @Test
    void anExtraAlreadyBoundInControlifyIsNotShownTwice() {
        List<String> merged = PadRadialSlots.merge(
                stock("a:one", "b:shared"), List.of("b:shared", "b:other"));
        assertEquals(List.of("a:one", "b:shared", "b:other"), merged);
    }

    @Test
    void aDuplicateInsideTheExtrasIsAlsoDropped() {
        List<String> merged = PadRadialSlots.merge(
                stock("a:one"), List.of("b:extra", "b:extra"));
        assertEquals(List.of("a:one", "b:extra"), merged);
    }

    @Test
    void severalEmptiesAreAllKeptBecauseAnEmptyIsNotADuplicateOfNothing() {
        List<String> merged = PadRadialSlots.merge(
                stock(EMPTY, EMPTY, EMPTY), List.of("b:extra"));
        assertEquals(List.of(EMPTY, EMPTY, EMPTY, "b:extra"), merged);
    }

    @Test
    void anEmptyAmongTheExtrasIsSkippedRatherThanTakingASlot() {
        // Controlify's empties are a layout choice; one in our own list is just a blank row.
        List<String> merged = PadRadialSlots.merge(
                stock("a:one"), List.of(EMPTY, "", "   ", "b:extra"));
        assertEquals(List.of("a:one", "b:extra"), merged);
    }

    @Test
    void theResultIsCappedAtTwenty() {
        List<String> extras = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            extras.add("b:extra_" + i);
        }
        List<String> merged = PadRadialSlots.merge(
                stock("a:one", "a:two"), extras);
        assertEquals(PadRadialSlots.MAX_SLOTS, merged.size());
        assertEquals("a:one", merged.get(0), "and the cap trims the tail, not the head");
    }

    @Test
    void moreControlifySlotsThanTheCapStillDoesNotOverflow() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add("a:slot_" + i);
        }
        assertEquals(PadRadialSlots.MAX_SLOTS,
                PadRadialSlots.merge(many, List.of("b:extra")).size());
    }

    @Test
    void idsAreComparedCaseInsensitivelyBecauseAResourceLocationIsLowerCase() {
        List<String> merged = PadRadialSlots.merge(
                stock("a:shared"), List.of("A:SHARED", "b:other"));
        assertEquals(List.of("a:shared", "b:other"), merged);
    }

    @Test
    void nullsAreTreatedAsEmptyListsRatherThanThrowing() {
        assertEquals(List.of(), PadRadialSlots.merge(null, null));
        assertEquals(List.of("b:extra"), PadRadialSlots.merge(null, List.of("b:extra")));
        assertEquals(List.of("a:one"), PadRadialSlots.merge(stock("a:one"), null));
    }

    // ------------------------------------------------------------ editing

    @Test
    void addAppendsAndRefusesADuplicate() {
        List<String> once = PadRadialSlots.add(List.of("b:one"), "b:two");
        assertEquals(List.of("b:one", "b:two"), once);
        assertEquals(once, PadRadialSlots.add(once, "b:two"));
        assertEquals(once, PadRadialSlots.add(once, "B:TWO"), "and it folds case first");
    }

    @Test
    void addRefusesOnceTheListIsFull() {
        List<String> full = new ArrayList<>();
        for (int i = 0; i < PadRadialSlots.MAX_SLOTS; i++) {
            full.add("b:extra_" + i);
        }
        assertEquals(PadRadialSlots.MAX_SLOTS,
                PadRadialSlots.add(full, "b:one_more").size());
    }

    @Test
    void removeTakesOneOutAndIsQuietAboutOneThatWasNotThere() {
        assertEquals(List.of("b:one"), PadRadialSlots.remove(List.of("b:one", "b:two"), "b:two"));
        List<String> unchanged = List.of("b:one");
        assertEquals(unchanged, PadRadialSlots.remove(unchanged, "b:nope"));
    }

    @Test
    void moveShiftsTheRestRatherThanSwapping() {
        // A swap would move two entries for one command; shifting is what a reorder means.
        assertEquals(List.of("b:two", "b:three", "b:one"),
                PadRadialSlots.move(List.of("b:one", "b:two", "b:three"), 0, 2));
        assertEquals(List.of("b:three", "b:one", "b:two"),
                PadRadialSlots.move(List.of("b:one", "b:two", "b:three"), 2, 0));
    }

    @Test
    void moveWithABadIndexChangesNothingInsteadOfThrowing() {
        // Driven by a command a player types, so a typo must say nothing happened.
        List<String> list = List.of("b:one", "b:two");
        assertEquals(list, PadRadialSlots.move(list, -1, 0));
        assertEquals(list, PadRadialSlots.move(list, 0, 9));
        assertEquals(list, PadRadialSlots.move(list, 1, 1));
        assertEquals(List.of(), PadRadialSlots.move(null, 0, 1), "a null list stays empty");
    }

    // ------------------------------------------------------------ the defaults

    @Test
    void theDefaultsFitAndAreUnique() {
        assertTrue(PadRadialSlots.DEFAULT_EXTRAS.size()
                        <= PadRadialSlots.MAX_SLOTS - PadRadialSlots.CONTROLIFY_SLOTS,
                "the defaults must fit after Controlify's own eight");
        assertEquals(PadRadialSlots.DEFAULT_EXTRAS.size(),
                Set.copyOf(PadRadialSlots.DEFAULT_EXTRAS).size(), "no duplicates");
    }

    @Test
    void theDefaultsAreAllThisModsOwnBindings() {
        for (String id : PadRadialSlots.DEFAULT_EXTRAS) {
            assertTrue(id.startsWith("xenopixelsmod:"), id);
            assertEquals(id, PadRadialSlots.clean(id), id + " should already be normalised");
        }
    }

    @Test
    void theDefaultsLeadWithTheTechniqueSlots() {
        // They are the reason the radial needed widening: DragonMineZ reaches them with Alt+1..4
        // and Ctrl+1..4, chords no gamepad can produce.
        assertTrue(PadRadialSlots.DEFAULT_EXTRAS.get(0).contains("technique_slot_1"));
    }

    @Test
    void mergingTheDefaultsOntoAFullControlifyRingGivesTwenty() {
        // The end-to-end shape of the headline change: eight plus twelve.
        List<String> eight = new ArrayList<>();
        for (int i = 0; i < PadRadialSlots.CONTROLIFY_SLOTS; i++) {
            eight.add("controlify:action_" + i);
        }
        List<String> merged = PadRadialSlots.merge(eight, PadRadialSlots.DEFAULT_EXTRAS);
        assertEquals(PadRadialSlots.MAX_SLOTS, merged.size());
        assertFalse(merged.subList(0, 8).contains("xenopixelsmod:technique_slot_1"),
                "Controlify's own eight are untouched");
        assertTrue(merged.get(8).startsWith("xenopixelsmod:"), "ours start at slot nine");
    }
}
