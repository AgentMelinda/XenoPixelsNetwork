package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaMultiBufferSourceTest {

    @Test
    void onlyEntityVertexFormatsCanBeReplacedWithEntityTranslucency() {
        assertTrue(AlphaMultiBufferSource.supportsTranslucentRemap(DefaultVertexFormat.NEW_ENTITY));
        assertFalse(AlphaMultiBufferSource.supportsTranslucentRemap(
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP));
    }
}
