package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaMultiBufferSourceTest {

    @Test
    void onlyEntityVertexFormatsCanBeReplacedWithEntityTranslucency() {
        assertTrue(AlphaMultiBufferSource.supportsTranslucentRemap(DefaultVertexFormat.NEW_ENTITY));
        assertFalse(AlphaMultiBufferSource.supportsTranslucentRemap(
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP));
    }

    /**
     * Outline sources are still {@link MultiBufferSource}. Glow fade remaps the body through the
     * inner {@code bufferSource}, never through {@link OutlineBufferSource#getBuffer}. Constructing
     * a live {@link OutlineBufferSource} needs a {@code ByteBufferBuilder}; this asserts the
     * type hierarchy and that the inner lookup is null-safe. End-to-end glow fade is a dev-run check.
     */
    @Test
    void anOutlineDelegateIsAMultiBufferSource() {
        assertTrue(MultiBufferSource.class.isAssignableFrom(OutlineBufferSource.class));
    }

    @Test
    void innerBufferSourceIsNullSafe() {
        assertNull(AlphaMultiBufferSource.innerBufferSource(null));
        assertNull(AlphaMultiBufferSource.outlineGenerator(null, null));
    }

    // ------------------------------------------------------------- scalePacked
    // The packed addVertex path hands the wrapper an ARGB int; only its alpha byte may move.

    @Test
    void scalePackedLeavesRgbUntouched() {
        int packed = FastColor.ARGB32.color(255, 0x12, 0x34, 0x56);
        int scaled = AlphaMultiBufferSource.scalePacked(packed, 0.5f);
        assertEquals(0x12, FastColor.ARGB32.red(scaled));
        assertEquals(0x34, FastColor.ARGB32.green(scaled));
        assertEquals(0x56, FastColor.ARGB32.blue(scaled));
        assertEquals(128, FastColor.ARGB32.alpha(scaled));
    }

    @Test
    void scalePackedScalesAnAlreadyPartialAlpha() {
        int packed = FastColor.ARGB32.color(100, 1, 2, 3);
        assertEquals(25, FastColor.ARGB32.alpha(AlphaMultiBufferSource.scalePacked(packed, 0.25f)));
    }

    @Test
    void scalePackedAlphaOneIsIdentity() {
        int packed = FastColor.ARGB32.color(200, 0xAB, 0xCD, 0xEF);
        assertEquals(packed, AlphaMultiBufferSource.scalePacked(packed, 1.0f));
    }

    @Test
    void scalePackedClampsTheMultiplier() {
        int packed = FastColor.ARGB32.color(255, 9, 8, 7);
        assertEquals(0, FastColor.ARGB32.alpha(AlphaMultiBufferSource.scalePacked(packed, -3.0f)));
        assertEquals(255, FastColor.ARGB32.alpha(AlphaMultiBufferSource.scalePacked(packed, 7.0f)));
        assertEquals(7, FastColor.ARGB32.blue(AlphaMultiBufferSource.scalePacked(packed, 7.0f)));
    }
}
