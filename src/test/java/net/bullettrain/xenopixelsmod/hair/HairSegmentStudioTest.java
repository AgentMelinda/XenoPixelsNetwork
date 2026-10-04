package net.bullettrain.xenopixelsmod.hair;

import com.dragonminez.common.hair.CustomHair;
import net.bullettrain.xenopixelsmod.client.maker.HairStrandPick;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HairSegmentStudioTest {
    @Test
    void createWeldDuplicateDeleteRespectFaceCapacity() {
        HairMakerDocument doc = new HairMakerDocument();
        doc.face("FRONT");
        assertEquals(4, doc.faceCapacity("FRONT"));

        int first = doc.createSegment();
        assertEquals(0, first);
        assertTrue(doc.selected().visible());
        assertEquals(4, doc.selected().length());

        int welded = doc.weldSegment();
        assertEquals(1, welded);
        HairStrandModel child = doc.selected();
        assertTrue(child.visible());
        assertTrue(child.rotationX() != 0f || child.curveX() != 0f,
                "weld applies tip offset");

        int dup = doc.duplicateSegment();
        assertEquals(2, dup);

        doc.deleteSegment();
        assertEquals(0, doc.selected().length());

        assertEquals(2, doc.createSegment());
        assertEquals(3, doc.createSegment());
        assertEquals(-1, doc.createSegment());
    }

    @Test
    void strandPickUsesProjectedBaseXyz() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.face("TOP");
        doc.strandIndex(0);

        int entityX = 200;
        int entityY = 300;
        float scale = 80f;
        float yaw = 180f;

        Vector3f basePx = CustomHair.getStrandBasePosition(CustomHair.HairFace.TOP, 0);
        HairStrandPick.ScreenPoint expected = HairStrandPick.project(
                basePx.x * HairStrandPick.PIXEL_TO_BLOCK,
                HairStrandPick.HEAD_Y_BLOCKS + basePx.y * HairStrandPick.PIXEL_TO_BLOCK,
                basePx.z * HairStrandPick.PIXEL_TO_BLOCK,
                entityX, entityY, scale, yaw);

        HairStrandPick.Hit hit = HairStrandPick.pick(
                doc, Math.round(expected.x()), Math.round(expected.y()),
                entityX, entityY, scale, yaw, true);
        assertNotNull(hit);
        assertEquals("TOP", hit.face());
        assertEquals(0, hit.index());
        assertTrue(hit.distSq() < HairStrandPick.hitRadiusSqForScale(scale));
    }

    @Test
    void facePickOrderPrefersFrontAtDefaultYaw() {
        assertEquals("FRONT", HairStrandPick.facePickOrder(180f)[0]);
        assertEquals("BACK", HairStrandPick.facePickOrder(0f)[0]);
    }

    @Test
    void pickRadiusShrinksWhenZoomedIn() {
        assertTrue(HairStrandPick.hitRadiusPxForScale(120f)
                < HairStrandPick.hitRadiusPxForScale(40f));
    }

    @Test
    void projectFlipsWithRotateZPi() {
        // At yaw 0, +X model should land left of entityX after rotateZ(PI).
        HairStrandPick.ScreenPoint p = HairStrandPick.project(1f, 1.5f, 0f, 100, 200, 40f, 0f);
        assertTrue(p.x() < 100f, "x=" + p.x());
        assertTrue(p.y() < 200f, "head should be above feet (smaller Y); y=" + p.y());
    }

    @Test
    void faceGridMatchesDmz() {
        assertEquals(1, HairStrandPick.faceGrid("FRONT")[0]);
        assertEquals(4, HairStrandPick.faceGrid("FRONT")[1]);
        assertEquals(4, HairStrandPick.faceGrid("TOP")[0]);
        assertEquals(4, HairStrandPick.faceGrid("BACK")[1]);
    }
}
