package net.bullettrain.xenopixelsmod.combat.v3.ki;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3NativeRenderTypeTest {
    @Test void knownTemplatesSelectTheirPinnedNativeRendererVariants() {
        assertEquals(1, V3KiStyle.of("beam", "kamehameha").nativeRenderType());
        assertEquals(2, V3KiStyle.of("beam", "galick_gun").nativeRenderType());
        assertEquals(3, V3KiStyle.of("beam", "final_flash").nativeRenderType());
        assertEquals(4, V3KiStyle.of("beam", "masenko").nativeRenderType());
        assertEquals(1, V3KiStyle.of("laser", "makkanko").nativeRenderType());
        assertEquals(5, V3KiStyle.of("giant_ball", "spiritbomb").nativeRenderType());
        assertEquals(6, V3KiStyle.of("giant_ball", "supernova").nativeRenderType());
        assertEquals(8, V3KiStyle.of("projectile", "sokidan").nativeRenderType());
    }
    @Test void chargePreservesVariantAlongsideColorsAndShape() {
        var style = V3KiStyle.of("laser", "makkanko");
        var charged = style.charged(2);
        assertEquals(style.nativeRenderType(), charged.nativeRenderType());
        assertEquals(style.kind(), charged.kind());
        assertEquals(style.core(), charged.core());
        assertEquals(style.edge(), charged.edge());
    }
}
