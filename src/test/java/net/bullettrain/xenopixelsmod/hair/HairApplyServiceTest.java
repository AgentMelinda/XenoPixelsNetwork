package net.bullettrain.xenopixelsmod.hair;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairStrand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D7c — apply plan + fake transport (no live network). Maps document →
 * {@link CustomHair} field checks; send path is injected.
 */
class HairApplyServiceTest {
    @Test
    void planMapsStyleIndexAndCopiesOneStrandDemo() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.style("SSJ2");
        HairApplyService.ApplyPlan plan = HairApplyService.plan(doc);
        assertEquals(2, plan.hairIndex());
        assertEquals("SSJ2", plan.styleName());
        assertEquals("One Strand", plan.hairName());

        HairApplyService.FacePlan top = plan.faces().stream()
                .filter(f -> "TOP".equals(f.faceName()))
                .findFirst()
                .orElseThrow();
        HairStrandModel strand = top.strands().get(0);
        assertEquals(4, strand.length());
        assertEquals(1.25f, strand.lengthScale(), 1e-5f);
        assertEquals(10f, strand.rotationX(), 1e-5f);
        assertEquals(2.5f, strand.cubeWidth(), 1e-5f);
        assertEquals("#ffaa00", strand.color());
        assertFalse(plan.faces().isEmpty());
    }

    @Test
    void toCustomHairCopiesLabFieldsOntoDmzStrand() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        CustomHair hair = HairApplyService.toCustomHair(HairApplyService.plan(doc));
        assertNotNull(hair);
        assertEquals("One Strand", hair.getName());
        HairStrand top0 = hair.getStrand(CustomHair.HairFace.TOP, 0);
        assertNotNull(top0);
        assertEquals(4, top0.getSegments());
        float decaySum = 1f + 0.85f + 0.85f * 0.85f + 0.85f * 0.85f * 0.85f;
        assertEquals(2f * 1.25f * decaySum, top0.getLength(), 1e-5f);
        assertEquals(10f, top0.getRotationX(), 1e-5f);
        assertEquals(-5f, top0.getRotationY(), 1e-5f);
        assertEquals(15f, top0.getRotationZ(), 1e-5f);
        assertEquals(2.5f * 1.1f, top0.getWidth(), 1e-5f);
        assertEquals(1.5f * 0.9f, top0.getDepth(), 1e-5f);
        assertEquals(6f, top0.getBendX(), 1e-5f);
        assertEquals((float) Math.pow(0.85f, 3), top0.getLengthRatio(), 1e-5f);
        assertEquals("#FFAA00", top0.getColor().toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    void applyClientUsesFakeTransportWithStyleIndex() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.style("SSJ");
        AtomicReference<Integer> index = new AtomicReference<>();
        AtomicReference<CustomHair> sent = new AtomicReference<>();
        List<String> forbidden = new ArrayList<>();

        HairApplyService.applyClient(doc, (hairIndex, hair) -> {
            index.set(hairIndex);
            sent.set(hair);
            // Guard: never confuse with CustomizationManager cosmetic ids.
            if (hair.getName() != null && hair.getName().startsWith("hair_style_")) {
                forbidden.add(hair.getName());
            }
        });

        assertEquals(1, index.get());
        assertNotNull(sent.get());
        assertEquals(4, sent.get().getStrand(CustomHair.HairFace.TOP, 0).getSegments());
        assertTrue(forbidden.isEmpty(), "must not send CustomizationManager hair_style_* names");
        assertTrue(HairApplyService.WRITE_PATH.contains("UpdateCustomHairC2S"));
    }

    @Test
    void documentEnablesApplyAfterD7cWithReplaceWarning() {
        assertTrue(new HairMakerDocument().isApplyEnabled());
        assertEquals("path_ready_runtime_unverified", HairMakerDocument.APPLY_STATUS);
        assertTrue(HairMakerDocument.APPLY_NOTE.contains("runtime unverified"));
        assertTrue(HairMakerDocument.APPLY_NOTE.contains("UpdateCustomHairC2S"));
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("Replace-current-style"));
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("does not merge"));
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("runtime unverified"));
    }
}
