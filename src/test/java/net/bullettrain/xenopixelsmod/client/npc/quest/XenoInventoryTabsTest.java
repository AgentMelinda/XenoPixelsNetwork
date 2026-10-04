package net.bullettrain.xenopixelsmod.client.npc.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hit-testing the tab strip.
 *
 * <p>Pure geometry, deliberately: the render half needs a running client, but "did the click land
 * on the tab the player aimed at" is arithmetic, and it is the half that silently misbehaves.
 */
class XenoInventoryTabsTest {

    private static final int LEFT = 100;
    private static final int TOP = 50;

    @Test
    void theTabOrderIsInventoryThenFactionsThenQuests() {
        // The reference groups them in this order, and the drawn order comes from the enum. A
        // reordering here silently moves every player's tabs.
        assertArrayEquals(
                new XenoInventoryTabs.Tab[]{
                        XenoInventoryTabs.Tab.INVENTORY,
                        XenoInventoryTabs.Tab.FACTIONS,
                        XenoInventoryTabs.Tab.QUESTS},
                XenoInventoryTabs.Tab.values());
    }

    @Test
    void tabsDoNotOverlap() {
        // Overlapping rectangles mean the top edge of one tab activates its neighbour, which reads
        // as a misclick the player cannot explain.
        for (int i = 1; i < XenoInventoryTabs.Tab.values().length; i++) {
            int[] above = XenoInventoryTabs.tabRect(LEFT, TOP, i - 1);
            int[] below = XenoInventoryTabs.tabRect(LEFT, TOP, i);
            assertTrue(above[1] + above[3] <= below[1],
                    "tab " + i + " must start below the previous one's bottom edge");
        }
    }

    @Test
    void aClickInTheMiddleOfEachTabFindsThatTab() {
        for (int index = 0; index < XenoInventoryTabs.Tab.values().length; index++) {
            int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, index);
            XenoInventoryTabs.Tab hit = XenoInventoryTabs.hit(LEFT, TOP,
                    rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0);
            assertEquals(XenoInventoryTabs.Tab.values()[index], hit, "tab index " + index);
        }
    }

    @Test
    void aClickInsideTheInventoryItselfHitsNoTab() {
        // The tabs sit to the left of the inventory window. A click on a slot must not be stolen,
        // or the player cannot pick up their own items.
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, LEFT + 40, TOP + 40));
    }

    @Test
    void aClickJustPastTheBottomTabHitsNothing() {
        // Off-by-one at the far edge: the first pixel below the strip must not belong to the last
        // tab, or clicks in empty space open the quest log.
        int last = XenoInventoryTabs.Tab.values().length - 1;
        int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, last);
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, rect[0] + 2.0, rect[1] + rect[3] + 1.0));
    }

    @Test
    void aClickOnTheTopEdgeCountsAndOnTheRightEdgeDoesNot() {
        // Half-open intervals, so two stacked tabs can never both claim one pixel row, and the
        // strip never bleeds into the inventory window beside it.
        int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, 0);
        assertEquals(XenoInventoryTabs.Tab.INVENTORY,
                XenoInventoryTabs.hit(LEFT, TOP, rect[0], rect[1]));
        assertNull(XenoInventoryTabs.hit(LEFT, TOP, rect[0] + rect[2], rect[1]));
    }

    @Test
    void theStripSitsLeftOfTheInventoryWindow() {
        // Anchored off getGuiLeft() the way XenoInventoryEffects anchors its rail off
        // getGuiLeft() + getXSize(). Drawing over the window would cover the armour slots.
        for (int index = 0; index < XenoInventoryTabs.Tab.values().length; index++) {
            int[] rect = XenoInventoryTabs.tabRect(LEFT, TOP, index);
            assertTrue(rect[0] + rect[2] <= LEFT,
                    "tab " + index + " must not overlap the window");
        }
    }

    @Test
    void theInventoryTabIsOpenBeforeAnybodyClicksAnything() {
        // Otherwise opening the inventory would show a quest log over the player's items.
        assertEquals(XenoInventoryTabs.Tab.INVENTORY, XenoInventoryTabs.open());
    }
}
