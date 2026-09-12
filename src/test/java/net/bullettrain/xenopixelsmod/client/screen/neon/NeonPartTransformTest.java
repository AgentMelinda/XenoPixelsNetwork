package net.bullettrain.xenopixelsmod.client.screen.neon;

import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.Part;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Block;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The neon screen's layout maths.
 *
 * <p>Worth pinning because the failures it produces are invisible in code review and obvious in
 * play: a control drawn in one place and clicked in another, a group that walks across the panel as
 * it is resized, labels that stay behind when their rows grow.
 */
class NeonPartTransformTest {

    private static final float EPSILON = 0.001f;

    @BeforeEach
    @AfterEach
    void resetLayout() {
        // The config is static and shared, so a test that left a scale behind would change the
        // meaning of every test after it.
        XenoDmzNeonConfig.resetParts();
    }

    @Test
    void aPartScaledAboutItsCentreStaysWhereItWasPut() {
        XenoDmzNeonConfig.customLayout = true;
        Rect unscaled = NeonPartTransform.rect(100, 50, 20, 30, Part.INFO_PANEL);
        XenoDmzNeonConfig.partScale[Part.INFO_PANEL] = 2.0f;
        Rect scaled = NeonPartTransform.rect(100, 50, 20, 30, Part.INFO_PANEL);

        assertEquals(centreX(unscaled), centreX(scaled), EPSILON,
                "scaling moved the part sideways");
        assertEquals(centreY(unscaled), centreY(scaled), EPSILON,
                "scaling moved the part down the screen");
        assertEquals(200.0f, scaled.width(), EPSILON);
        assertEquals(100.0f, scaled.height(), EPSILON);
    }

    @Test
    void offsetsApplyOnlyWhileTheOverrideIsOn() {
        XenoDmzNeonConfig.partX[Part.NAMEPLATE] = 40;
        XenoDmzNeonConfig.partY[Part.NAMEPLATE] = -15;

        XenoDmzNeonConfig.customLayout = false;
        assertEquals(10.0f, NeonPartTransform.rect(20, 20, 10, 10, Part.NAMEPLATE).x(), EPSILON,
                "the shipped layout drifted with the override off");

        XenoDmzNeonConfig.customLayout = true;
        Rect moved = NeonPartTransform.rect(20, 20, 10, 10, Part.NAMEPLATE);
        assertEquals(50.0f, moved.x(), EPSILON);
        assertEquals(-5.0f, moved.y(), EPSILON);
    }

    @Test
    void scaleIsClampedToTheConfigRange() {
        XenoDmzNeonConfig.partScale[Part.ORBS] = 99.0f;
        assertEquals(3.0f, XenoDmzNeonConfig.partScale(Part.ORBS), EPSILON);
        XenoDmzNeonConfig.partScale[Part.ORBS] = 0.0f;
        assertEquals(0.25f, XenoDmzNeonConfig.partScale(Part.ORBS), EPSILON);
        // A clamped scale must be the one the rectangle is built from, or the drawn size and the
        // clickable size part company at the extremes.
        XenoDmzNeonConfig.partScale[Part.ORBS] = 99.0f;
        assertEquals(30.0f, NeonPartTransform.rect(10, 10, 0, 0, Part.ORBS).width(), EPSILON);
    }

    @Test
    void aGroupScalesAboutItsOwnCentreAndKeepsItsRowsEvenlySpaced() {
        XenoDmzNeonConfig.customLayout = true;
        Block block = new Block(Part.STAT_ROWS, 100, 200, 104, 77);
        float[] unscaledTops = rowTops(block);

        XenoDmzNeonConfig.partScale[Part.STAT_ROWS] = 2.0f;
        float[] scaledTops = rowTops(block);

        assertEquals(block.centreY(), block.mapY(block.centreY()), EPSILON,
                "the group did not pivot on its own centre");
        float unscaledGap = unscaledTops[1] - unscaledTops[0];
        for (int i = 1; i < scaledTops.length; i++) {
            assertEquals(unscaledGap * 2.0f, scaledTops[i] - scaledTops[i - 1], EPSILON,
                    "row " + i + " drifted out of step with the rest of the group");
        }
    }

    @Test
    void aPartInsideAGroupCarriesBothScales() {
        XenoDmzNeonConfig.customLayout = true;
        Block block = new Block(Part.STAT_ROWS, 100, 200, 104, 77);
        XenoDmzNeonConfig.partScale[Part.STAT_ROWS] = 2.0f;
        XenoDmzNeonConfig.partScale[Part.PLUS_BUTTON] = 1.5f;

        Rect plus = NeonPartTransform.rect(9, 9, 150, 210, Part.PLUS_BUTTON, block);
        assertEquals(9 * 1.5f * 2.0f, plus.width(), EPSILON,
                "the group's scale and the part's own did not compose");
        assertEquals(NeonPartTransform.textScale(Part.PLUS_BUTTON, block), 3.0f, EPSILON);
    }

    @Test
    void aPartInsideAGroupAtScaleOneIsWhereItWasBefore() {
        // The composition must be the identity when nothing is scaled, or every shipped layout
        // shifts the moment a group gains a transform.
        Block block = new Block(Part.STAT_ROWS, 100, 200, 104, 77);
        Rect alone = NeonPartTransform.rect(9, 9, 150, 210, Part.PLUS_BUTTON);
        Rect inGroup = NeonPartTransform.rect(9, 9, 150, 210, Part.PLUS_BUTTON, block);
        assertEquals(alone.x(), inGroup.x(), EPSILON);
        assertEquals(alone.y(), inGroup.y(), EPSILON);
        assertEquals(alone.width(), inGroup.width(), EPSILON);
    }

    @Test
    void theRectangleDrawnIsTheRectangleClicked() {
        XenoDmzNeonConfig.customLayout = true;
        Block block = new Block(Part.STAT_ROWS, 100, 200, 104, 77);
        XenoDmzNeonConfig.partScale[Part.STAT_ROWS] = 2.4f;
        XenoDmzNeonConfig.partX[Part.STAT_ROWS] = 17;
        XenoDmzNeonConfig.partY[Part.STAT_ROWS] = -9;

        Rect plus = NeonPartTransform.rect(9, 9, 150, 210, Part.PLUS_BUTTON, block);
        assertTrue(plus.contains(plus.x() + 0.5, plus.y() + 0.5),
                "the rectangle does not contain its own top-left corner");
        assertTrue(plus.contains(plus.x() + plus.width() - 0.5,
                        plus.y() + plus.height() - 0.5),
                "the rectangle does not contain its own bottom-right corner");
        assertFalse(plus.contains(plus.x() - 0.5, plus.y() + 0.5));
        assertFalse(plus.contains(plus.x() + plus.width() + 0.5, plus.y() + 0.5));
    }

    @Test
    void unionCoversEveryPartItIsGiven() {
        Rect first = new Rect(10, 10, 20, 20);
        Rect second = new Rect(100, -5, 10, 10);
        Rect box = first.union(second);
        assertEquals(10.0f, box.x(), EPSILON);
        assertEquals(-5.0f, box.y(), EPSILON);
        assertEquals(100.0f, box.width(), EPSILON);
        assertEquals(35.0f, box.height(), EPSILON);
        // A null neighbour is the empty case the preview hits before it has drawn anything.
        assertEquals(first, first.union(null));
    }

    @Test
    void hiddenIsReportedForBothPartsAndGroups() {
        Block block = new Block(Part.SUMMARY, 0, 0, 10, 10);
        assertFalse(NeonPartTransform.hidden(Part.SUMMARY));
        assertFalse(block.hidden());
        XenoDmzNeonConfig.partHidden[Part.SUMMARY] = true;
        assertTrue(NeonPartTransform.hidden(Part.SUMMARY));
        assertTrue(block.hidden(), "a hidden group must report itself hidden, or it stays clickable");
    }

    private static float[] rowTops(Block block) {
        float[] tops = new float[7];
        for (int i = 0; i < tops.length; i++) {
            tops[i] = block.map(block.x(), block.y() + i * 11, 104, 11).y();
        }
        return tops;
    }

    private static float centreX(Rect rect) {
        return rect.x() + rect.width() / 2.0f;
    }

    private static float centreY(Rect rect) {
        return rect.y() + rect.height() / 2.0f;
    }
}
