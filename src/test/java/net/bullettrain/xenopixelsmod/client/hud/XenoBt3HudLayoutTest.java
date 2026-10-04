package net.bullettrain.xenopixelsmod.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The BT3 HUD's arithmetic: where its pieces go, and how much of each bar is lit.
 *
 * <p>Worth pinning because every one of these is invisible from the code and expensive to check in
 * a client. A bar clipped a pixel past a segment boundary shows a sliver of the next segment, a
 * panel height that does not grow with the rail lets the panel hang off the bottom of the screen
 * and stops the editor's drag short of it, and a shell picked by position rather than by colour
 * quietly reshuffles the rail whenever a player turns a chip off.
 */
class XenoBt3HudLayoutTest {

    @Test
    void everyMeasuredWellLiesInsideItsSprite() {
        assertWellInside(XenoBt3HudAtlas.PORTRAIT_WELL, XenoBt3HudAtlas.PORTRAIT_RING,
                "portrait well");
        assertWellInside(XenoBt3HudAtlas.NAMEPLATE_WELL, XenoBt3HudAtlas.NAMEPLATE,
                "nameplate well");
        assertTrue(XenoBt3HudAtlas.HP_TRACK_X + XenoBt3HudAtlas.HP_TRACK_WIDTH
                        <= XenoBt3HudAtlas.HP_BAR.width(),
                "the health track runs past the end of its own sprite");
    }

    private static void assertWellInside(XenoBt3HudAtlas.Well well, XenoBt3HudAtlas.Sprite sprite,
                                         String what) {
        assertTrue(well.x() >= 0 && well.y() >= 0, what + " starts outside its sprite");
        assertTrue(well.x() + well.width() <= sprite.width(), what + " is too wide for its sprite");
        assertTrue(well.y() + well.height() <= sprite.height(),
                what + " is too tall for its sprite");
    }

    @Test
    void segmentStartsAscendAndStayInsideTheBar() {
        assertAscending(XenoBt3HudAtlas.kiSegmentCount(), XenoBt3HudAtlas::kiSegmentStart,
                XenoBt3HudAtlas.KI_SEGMENT_WIDTH, XenoBt3HudAtlas.KI_BAR.width(), "ki");
        assertAscending(XenoBt3HudAtlas.staminaSegmentCount(), XenoBt3HudAtlas::staminaSegmentStart,
                XenoBt3HudAtlas.STAMINA_SEGMENT_WIDTH, XenoBt3HudAtlas.STAMINA_BAR.width(),
                "stamina");
    }

    private static void assertAscending(int count, java.util.function.IntUnaryOperator start,
                                        int segmentWidth, int spriteWidth, String bar) {
        assertTrue(count > 1, bar + " lost its segments");
        for (int i = 1; i < count; i++) {
            assertTrue(start.applyAsInt(i) > start.applyAsInt(i - 1),
                    bar + " segment " + i + " does not start after segment " + (i - 1));
        }
        assertTrue(start.applyAsInt(count - 1) + segmentWidth <= spriteWidth,
                bar + "'s last segment runs off the end of its sprite");
    }

    /**
     * The ki bar is drawn with as many segments as the Sparking charge converts.
     *
     * <p>Not a coincidence worth relying on silently: the Max Power staging lights one art segment
     * per charge segment, so if the art is ever re-cut with a different number the staging stops
     * lining up with the bar and this says so.
     */
    @Test
    void theKiArtHasOneSegmentPerSparkingChargeSegment() {
        assertEquals(net.bullettrain.xenopixelsmod.combat.Bt3SparkingCharge.SEGMENTS,
                XenoBt3HudAtlas.kiSegmentCount());
    }

    @Test
    void anEmptyBarShowsNothingAndAFullBarShowsAllOfIt() {
        assertEquals(0, XenoBt3HudLayout.kiClipWidth(0f));
        assertEquals(0, XenoBt3HudLayout.staminaClipWidth(0f));
        assertEquals(0, XenoBt3HudLayout.hpFillWidth(0f));
        assertEquals(XenoBt3HudAtlas.KI_BAR.width(), XenoBt3HudLayout.kiClipWidth(1f));
        assertEquals(XenoBt3HudAtlas.STAMINA_BAR.width(), XenoBt3HudLayout.staminaClipWidth(1f));
        assertEquals(XenoBt3HudAtlas.HP_TRACK_WIDTH, XenoBt3HudLayout.hpFillWidth(1f));
    }

    @Test
    void outOfRangeAndNaNFractionsAreClampedRatherThanDrawn() {
        assertEquals(0, XenoBt3HudLayout.kiClipWidth(-3f));
        assertEquals(0, XenoBt3HudLayout.kiClipWidth(Float.NaN));
        assertEquals(XenoBt3HudAtlas.KI_BAR.width(), XenoBt3HudLayout.kiClipWidth(4f));
        assertEquals(0, XenoBt3HudLayout.hpFillWidth(Float.NaN));
    }

    /**
     * A whole number of segments cuts exactly on the next segment's left edge.
     *
     * <p>That is the whole point of measuring the starts: the cut lands in the gap before the next
     * segment, so a bar at three eighths shows three lit segments and no sliver of a fourth.
     */
    @Test
    void wholeSegmentsCutOnTheNextSegmentsMeasuredEdge() {
        int count = XenoBt3HudAtlas.kiSegmentCount();
        for (int lit = 1; lit < count; lit++) {
            assertEquals(XenoBt3HudAtlas.kiSegmentStart(lit),
                    XenoBt3HudLayout.kiClipWidth(lit / (float) count),
                    "ki bar at " + lit + "/" + count);
        }
        int stmCount = XenoBt3HudAtlas.staminaSegmentCount();
        for (int lit = 1; lit < stmCount; lit++) {
            assertEquals(XenoBt3HudAtlas.staminaSegmentStart(lit),
                    XenoBt3HudLayout.staminaClipWidth(lit / (float) stmCount),
                    "stamina bar at " + lit + "/" + stmCount);
        }
    }

    @Test
    void theLeadingSegmentFillsPartOfTheWay() {
        int count = XenoBt3HudAtlas.kiSegmentCount();
        int oneAndAHalf = XenoBt3HudLayout.kiClipWidth(1.5f / count);
        assertEquals(XenoBt3HudAtlas.kiSegmentStart(1) + XenoBt3HudAtlas.KI_SEGMENT_WIDTH / 2,
                oneAndAHalf);
        assertNotEquals(XenoBt3HudLayout.kiClipWidth(1f / count), oneAndAHalf,
                "a part-filled segment must be distinguishable from a whole one");
    }

    @Test
    void aTinyFractionStillShowsSomething() {
        assertTrue(XenoBt3HudLayout.kiClipWidth(0.01f) > 0,
                "a nearly empty ki bar drew nothing at all, which reads as zero ki");
    }

    @Test
    void theRailWrapsAtFourAndSlotsFollowTheWrap() {
        assertEquals(0, XenoBt3HudLayout.railRows(0));
        assertEquals(1, XenoBt3HudLayout.railRows(1));
        assertEquals(1, XenoBt3HudLayout.railRows(4));
        assertEquals(2, XenoBt3HudLayout.railRows(5));
        assertEquals(2, XenoBt3HudLayout.railRows(8));
        assertEquals(3, XenoBt3HudLayout.railRows(9));

        assertEquals(XenoBt3HudLayout.slotX(0), XenoBt3HudLayout.slotX(4), "column repeats");
        assertEquals(XenoBt3HudLayout.slotY(0) + XenoBt3HudLayout.SLOT_ROW_HEIGHT,
                XenoBt3HudLayout.slotY(4), "the fifth chip starts the second row");
        assertTrue(XenoBt3HudLayout.slotX(3) + XenoBt3HudLayout.SLOT_PITCH_X
                        <= XenoBt3HudLayout.PANEL_WIDTH,
                "a full rail row is wider than the panel it is drawn in");
    }

    @Test
    void thePanelGrowsWithTheRailAndThePrompt() {
        int bare = XenoBt3HudLayout.panelHeight(0, false);
        int withRail = XenoBt3HudLayout.panelHeight(8, false);
        int withPrompt = XenoBt3HudLayout.panelHeight(8, true);
        assertEquals(XenoBt3HudLayout.CLUSTER_HEIGHT, bare);
        assertTrue(withRail > bare, "the rail did not add to the reported height");
        assertTrue(withPrompt > withRail, "the prompt did not add to the reported height");
        assertTrue(withRail >= XenoBt3HudLayout.slotY(7) + XenoBt3HudLayout.SLOT_HEIGHT,
                "the last chip is drawn below the bottom the panel reports");
    }

    @Test
    void thePromptSitsBelowTheClusterEvenWithNoRail() {
        assertTrue(XenoBt3HudLayout.promptY(0) >= XenoBt3HudLayout.CLUSTER_HEIGHT,
                "the prompt would be drawn over the form line");
    }

    /**
     * Each combat chip keeps the colour it already has.
     *
     * <p>Picked by hue rather than by rail position so turning a chip off in the cooldown settings
     * does not recolour every chip after it.
     */
    @Test
    void chipsLandOnTheShellNearestTheirOwnAccent() {
        assertEquals(0, XenoBt3HudLayout.shellForAccent(0xFFCE93D8), "Z-Burst purple");
        assertEquals(1, XenoBt3HudLayout.shellForAccent(0xFF42A5F5), "Vanish blue");
        assertEquals(1, XenoBt3HudLayout.shellForAccent(0xFF4FC3F7), "Ki cancel light blue");
        assertEquals(2, XenoBt3HudLayout.shellForAccent(0xFF66BB6A), "a green move");
        assertEquals(3, XenoBt3HudLayout.shellForAccent(0xFFFFB74D), "Charge amber");
        assertEquals(3, XenoBt3HudLayout.shellForAccent(0xFFFF8A65), "Chase orange");
    }

    @Test
    void aColourlessAccentStillPicksAShell() {
        int shell = XenoBt3HudLayout.shellForAccent(0xFF808080);
        assertTrue(shell >= 0 && shell < 4, "grey fell outside the four shells");
    }
}
