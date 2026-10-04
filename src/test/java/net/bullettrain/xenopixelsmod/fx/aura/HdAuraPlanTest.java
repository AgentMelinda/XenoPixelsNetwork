package net.bullettrain.xenopixelsmod.fx.aura;

import net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan.Aura;
import net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan.Layer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "we broke stack aura ... with the new aura animtions ... and transformation aura color". */
class HdAuraPlanTest {
    static final int GOLD = 0xFFD700, RED = 0xDB182C, VIOLET = 0x6A1B9A, PINK = 0xEA80FC;
    static final int SSB = 0x29B6F6, ICE = 0xE1F5FE, NAVY = 0x01579B, WHITE = 0xFFFFFF;

    @Test
    void aNearWhiteExtraIsNotItsOwnAura() {
        assertTrue(HdAuraPlan.tooPaleToStack(ICE));
        assertTrue(HdAuraPlan.tooPaleToStack(WHITE));
        assertFalse(HdAuraPlan.tooPaleToStack(SSB));
        assertFalse(HdAuraPlan.tooPaleToStack(NAVY));
        assertEquals(List.of(new Aura(SSB, SSB, 1.0f, 1f)),
                HdAuraPlan.plan(List.of(new Layer(0, SSB, 1f), new Layer(1, ICE, 1f)), Set.of(), true));
    }

    @Test
    void aDarkerBlueExtraStacksAsItsOwnLargerAura() {
        List<Aura> auras = HdAuraPlan.plan(
                List.of(new Layer(0, SSB, 1f), new Layer(1, NAVY, 1f)), Set.of(), true);
        assertEquals(List.of(new Aura(SSB, SSB, 1.0f, 1f), new Aura(NAVY, NAVY, 1.15f, 1f)), auras);
    }

    @Test
    void aStackFormIsItsOwnLargerAura() {
        List<Aura> auras = HdAuraPlan.plan(List.of(new Layer(0, GOLD, 1f), new Layer(1, RED, 1f)), Set.of(), true);
        assertEquals(List.of(new Aura(GOLD, GOLD, 1.0f, 1f), new Aura(RED, RED, 1.15f, 1f)), auras);
    }

    @Test
    void aFormsExtraColourStaysTheOuterFlame() {
        List<Aura> auras = HdAuraPlan.plan(List.of(new Layer(0, VIOLET, 1f), new Layer(1, PINK, 1f)),
                Set.of(PINK), true);
        assertEquals(List.of(new Aura(VIOLET, PINK, 1.0f, 1f)), auras);
    }

    @Test
    void extraAndStackTogether() {
        List<Aura> auras = HdAuraPlan.plan(
                List.of(new Layer(0, VIOLET, 1f), new Layer(1, RED, 1f), new Layer(2, PINK, 1f)), Set.of(PINK), true);
        assertEquals(2, auras.size());
        assertEquals(RED, auras.get(1).inner());
        assertEquals(PINK, auras.get(1).outer(), "the extra colour shifted above the stack layer tints it");
    }

    @Test
    void theIncomingFormFadesIn() {
        assertEquals(1, HdAuraPlan.plan(List.of(new Layer(0, GOLD, 1f), new Layer(1, RED, 0.05f)), Set.of(), true)
                .size(), "too faint to play yet");
        List<Aura> half = HdAuraPlan.plan(List.of(new Layer(0, GOLD, 1f), new Layer(1, RED, 0.5f)), Set.of(), true);
        assertEquals(0.5f, half.get(1).alpha());
    }

    @Test
    void theAuraAndTheShineGrowWithThePowerRelease() {
        assertEquals(0.4f, HdAuraPlan.releaseScale(0), 1e-6f);
        assertEquals(0.7f, HdAuraPlan.releaseScale(50), 1e-6f);
        assertEquals(1.0f, HdAuraPlan.releaseScale(100), 1e-6f);
        assertEquals(1.3f, HdAuraPlan.releaseScale(400), 1e-6f, "capped past the limit");
        assertEquals(0.25f, HdAuraPlan.releaseShine(0), 1e-6f);
        assertEquals(1.0f, HdAuraPlan.releaseShine(100), 1e-6f);
        assertEquals(1.0f, HdAuraPlan.releaseShine(250), 1e-6f);
        assertEquals(0x804000, HdAuraPlan.shade(0xFF8000, 0.5f));
        assertEquals(0xFF8000, HdAuraPlan.shade(0xFF8000, 1.0f));
    }

    @Test
    void theKiAuraStretchIsCapped() {
        // 2026-10-02 owner's case: power 1.64 times a ki charge of 4 is 6.56 tall, 2.05 wide.
        assertArrayEquals(new float[] {2.05f, 1.8f}, HdAuraPlan.capStretch(2.05f, 6.56f, 1.8f));
        assertArrayEquals(new float[] {2.05f, 6.56f}, HdAuraPlan.capStretch(2.05f, 6.56f, 10f),
                "the default cap leaves DragonMineZ's own size alone");
        assertArrayEquals(new float[] {1.0f, 1.3f}, HdAuraPlan.capStretch(1.0f, 1.3f, 1.8f), "under the cap: untouched");
        assertArrayEquals(new float[] {1.0f, 1.0f}, HdAuraPlan.capStretch(Float.NaN, Float.POSITIVE_INFINITY, 1.8f));
        assertEquals(1.0f, HdAuraPlan.capStretch(1.0f, 5.0f, 0.2f)[1], "a cap below 1 never shrinks the aura");
    }

    @Test
    void theAuraIsSizedFromDragonMineZsOwnAuraScale() {
        float rest = HdAuraPlan.DMZ_REST_SCALE;
        assertArrayEquals(new float[] {1.0f, 1.0f}, HdAuraPlan.relativeToRest(new float[] {rest, rest, rest}), 1e-6f);
        // A ki charge at height 4: the same four times on the HD aura.
        assertArrayEquals(new float[] {1.25f, 4.0f},
                HdAuraPlan.relativeToRest(new float[] {rest * 1.25f, rest * 4.0f, rest}), 1e-5f);
        assertArrayEquals(new float[] {1.0f, 1.0f}, HdAuraPlan.relativeToRest(null));
        assertArrayEquals(new float[] {1.0f, 1.0f}, HdAuraPlan.shape(true), "variant 2 is authored to DMZ's box");
        assertEquals(0.0f, HdAuraPlan.drop(true));
        assertEquals(0.44f, HdAuraPlan.drop(false), 1e-6f, "variant 1 is lowered to where DMZ's flame starts");
    }

    @Test
    void v3UsesTheSilhouetteBoxAndKeepsTheV1Inner() {
        assertEquals("v3", HdAuraPlan.parseVariant("v3"));
        assertEquals("v3", HdAuraPlan.parseVariant("V3"));
        assertEquals("v2", HdAuraPlan.parseVariant("v2"));
        assertEquals("v1", HdAuraPlan.parseVariant("v1"));
        assertEquals("v1", HdAuraPlan.parseVariant("nope"));
        assertEquals("v1", HdAuraPlan.parseVariant(null));
        assertTrue(HdAuraPlan.silhouetteBox("v2"));
        assertTrue(HdAuraPlan.silhouetteBox("v3"));
        assertFalse(HdAuraPlan.silhouetteBox("v1"));
        assertTrue(HdAuraPlan.silhouetteEffect("aura3/aura3_ffd700"));
        assertTrue(HdAuraPlan.silhouetteEffect("aura2/aura2_ffd700_nz"));
        assertFalse(HdAuraPlan.silhouetteEffect("aura/aura_in_ffd700"));
        assertArrayEquals(HdAuraPlan.shape(true), HdAuraPlan.shape(HdAuraPlan.silhouetteBox("v3")));
        assertEquals(HdAuraPlan.drop(false), HdAuraPlan.drop(HdAuraPlan.silhouetteBox("v1")), 1e-6f);
    }

    @Test
    void v3PlaysFullV1UnderTheSilhouette() {
        // 2026-10-03 owner: v1 outer billow and inner shell were missing on v3.
        assertTrue(HdAuraPlan.playsV1Outer("v3"));
        assertTrue(HdAuraPlan.playsV1Inner("v3"));
        assertTrue(HdAuraPlan.playsSilhouette("v3"));
        assertTrue(HdAuraPlan.playsV1Outer("v1"));
        assertTrue(HdAuraPlan.playsV1Inner("v1"));
        assertFalse(HdAuraPlan.playsSilhouette("v1"));
        assertFalse(HdAuraPlan.playsV1Outer("v2"));
        assertFalse(HdAuraPlan.playsV1Inner("v2"));
        assertTrue(HdAuraPlan.playsSilhouette("v2"));
        assertFalse(HdAuraPlan.playsLeanOuter("v1"));
        assertFalse(HdAuraPlan.playsLeanOuter("v2"));
        assertFalse(HdAuraPlan.playsLeanOuter("v3"));
    }

    @Test
    void v4IsTheLeanDefaultUnderTheSilhouette() {
        assertEquals("v4", HdAuraPlan.parseVariant("v4"));
        assertEquals("v4", HdAuraPlan.parseVariant("V4"));
        assertTrue(HdAuraPlan.silhouetteBox("v4"));
        assertTrue(HdAuraPlan.playsSilhouette("v4"));
        assertTrue(HdAuraPlan.playsV1Inner("v4"));
        assertFalse(HdAuraPlan.playsV1Outer("v4"), "v4 must not spawn the dense v1 smoke column");
        assertTrue(HdAuraPlan.playsLeanOuter("v4"));
        assertTrue(HdAuraPlan.silhouetteEffect("aura4/aura4_ffd700"));
        assertTrue(HdAuraPlan.silhouetteEffect("aura4/aura4_ffd700_nz"));
        assertArrayEquals(HdAuraPlan.shape(true), HdAuraPlan.shape(HdAuraPlan.silhouetteBox("v4")));
    }

    @Test
    void whereDragonMineZsFlameIs() {
        // A player at rest: model scale 0.9375 times the 1.05 base.
        float[] box = HdAuraPlan.dmzFlameBox(HdAuraPlan.DMZ_REST_SCALE, HdAuraPlan.DMZ_REST_SCALE);
        assertEquals(-0.44f, box[0], 0.01f, "starts below the feet");
        assertEquals(3.61f, box[1], 0.01f);
        assertEquals(3.22f, box[2], 0.01f);
        // Twice the height: the top rises and the start sinks, as DMZ's own aura does.
        float[] tall = HdAuraPlan.dmzFlameBox(HdAuraPlan.DMZ_REST_SCALE, HdAuraPlan.DMZ_REST_SCALE * 2f);
        assertEquals(3.22f, tall[2], 0.01f, "the width follows only the X scale");
        assertEquals(7.17f, tall[1], 0.01f);
        assertTrue(tall[0] < box[0], "bottom " + tall[0]);
    }

    @Test
    void aFreezeIsNoticedSoTheAuraIsSentAgainAtOnce() {
        long start = 5_000_000_000L;
        assertFalse(HdAuraPlan.hitch(start, start + 50_000_000L), "an ordinary tick");
        assertTrue(HdAuraPlan.hitch(start, start + 900_000_000L), "a freeze of 0.9 s");
        assertFalse(HdAuraPlan.hitch(0L, start), "the first tick is not a freeze");
    }

    @Test
    void entityRenderPosUsesXoNotStaleXOld() {
        // After absMoveTo-style motion, xo == current while xOld can still be last tick's start.
        // Lerping from xOld puts the aura behind the body when flying fast (2026-10-03).
        float[] correct = HdAuraPlan.entityRenderPos(10.0, 64.0, 20.0, 10.0, 64.0, 20.0, 0.5f);
        assertEquals(10.0f, correct[0], 1e-5f);
        assertEquals(64.0f, correct[1], 1e-5f);
        assertEquals(20.0f, correct[2], 1e-5f);
        float[] mid = HdAuraPlan.entityRenderPos(0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.5f);
        assertEquals(5.0f, mid[0], 1e-5f);
        // Stale xOld (0) vs refreshed xo (10) at the same getX (10): wrong lerp lags by 5 blocks.
        float[] wrongIfXOld = HdAuraPlan.entityRenderPos(0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.5f);
        float[] rightIfXoEqualsX = HdAuraPlan.entityRenderPos(10.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.5f);
        assertEquals(5.0f, wrongIfXOld[0], 1e-5f);
        assertEquals(10.0f, rightIfXoEqualsX[0], 1e-5f);
        assertTrue(rightIfXoEqualsX[0] - wrongIfXOld[0] > 1.0f);
    }

    @Test
    void firstPersonUsesTheDmzCameraOverlay() {
        // 2026-10-03 owner: render HD aura in first person like normal DMZ aura.
        // AuraRenderer.executeAuraShaderDraw FP: identity; translate(0,-0.6,-0.7);
        // scale(normalized*3); draw at alpha*0.45 — 0.45 is ALPHA, not scale.
        assertTrue(HdAuraPlan.cameraSpaceInFirstPerson());
        assertFalse(HdAuraPlan.hideWorldSpaceInFirstPerson());
        assertArrayEquals(new float[] {0.0f, -0.6f, -0.7f}, HdAuraPlan.firstPersonEyeOffset(), 1e-6f);
        assertEquals(3.0f, HdAuraPlan.firstPersonScaleFactor(), 1e-6f);
        assertEquals(0.45f, HdAuraPlan.firstPersonAlphaFactor(), 1e-6f);
        // AAA head-space: +Z is behind the eyes; DMZ -0.7 view-Z (in front) → local Z -0.7.
        // Looking straight +Z (yaw 0): in-front offset lands at +world Z.
        float[] pos = HdAuraPlan.firstPersonWorldPos(10.0, 70.0, 20.0, 0.0f, 0.0f);
        assertEquals(10.0f, pos[0], 1e-4f);
        assertEquals(70.0f - 0.6f, pos[1], 1e-4f);
        assertEquals(20.0f + 0.7f, pos[2], 1e-4f);
        // Camera-lock euler matches AAA head-space (radians): (-pitch, PI - yaw, 0).
        float[] rot = HdAuraPlan.firstPersonRotationRadians(0.0f, 0.0f);
        assertEquals(0.0f, rot[0], 1e-5f);
        assertEquals((float) Math.PI, rot[1], 1e-5f);
        assertEquals(0.0f, rot[2], 1e-5f);
        // Silhouette authored CENTRE_Y=1.5: emitter nudges down so the sprite centre hits the
        // DMZ billboard centre after FP scale (owner: FP start must match DMZ).
        assertEquals(1.5f, HdAuraPlan.silhouetteCentreY(), 1e-6f);
        assertEquals(1.5f * 3.0f, HdAuraPlan.firstPersonEmitterCentreNudge(true, 3.0f), 1e-4f);
        assertEquals(0.0f, HdAuraPlan.firstPersonEmitterCentreNudge(false, 3.0f), 1e-6f);
    }

    @Test
    void brightnessRunsFromNothingToFull() {
        // 2026-10-02 owner: "let aura brightness go from 0 to 100".
        assertEquals(0.0f, AuraPalette.brightnessOf(0f));
        assertEquals(0.25f, AuraPalette.brightnessOf(25f), 1e-6f);
        assertEquals(1.0f, AuraPalette.brightnessOf(100f), 1e-6f);
        assertEquals(1.3f, AuraPalette.brightnessOf(130f), 1e-6f);
        assertEquals(0.75f, AuraPalette.brightnessOf(0.75f), 1e-6f, "the old 0.5-1.3 fraction still works");
        assertFalse(AuraPalette.visible(0f), "0 is off");
        assertTrue(AuraPalette.visible(0.1f));
        assertEquals("_b10", AuraPalette.brightnessSuffix(0.1f));
        assertEquals("_b25", AuraPalette.brightnessSuffix(0.3f));
        assertEquals("", AuraPalette.brightnessSuffix(1.0f));
    }

    @Test
    void variantTwoShowsEveryLayerAsItsOwnAura() {
        // A form with an extra aura colour, and a stack form on top: three DMZ layers.
        List<Aura> auras = HdAuraPlan.plan(
                List.of(new Layer(0, VIOLET, 1f), new Layer(1, PINK, 1f), new Layer(2, RED, 1f)), Set.of(), true);
        assertEquals(3, auras.size());
        assertEquals(PINK, auras.get(1).inner());
        assertEquals(1.15f, auras.get(1).scale(), 1e-6f);
        assertEquals(1.30f, auras.get(2).scale(), 1e-6f);
    }

    @Test
    void theFirstBehaviourIsKept() {
        assertEquals(List.of(new Aura(GOLD, RED, 1.0f, 1f)),
                HdAuraPlan.plan(List.of(new Layer(0, GOLD, 1f), new Layer(1, RED, 0.05f)), Set.of(), false));
        assertEquals(List.of(), HdAuraPlan.plan(List.of(), Set.of(), true));
    }
}
