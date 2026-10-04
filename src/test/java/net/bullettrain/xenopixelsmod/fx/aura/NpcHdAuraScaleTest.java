package net.bullettrain.xenopixelsmod.fx.aura;

import net.bullettrain.xenopixelsmod.mixin.SharedNpcMixinPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcHdAuraScaleTest {
    @Test void explicitNpcSizeIsAppliedAfterPowerGrowthCapExactlyOnce() {
        var ordinary = HdAuraPlan.capNpcStretch(1, 1, 3, 1);
        var large = HdAuraPlan.capNpcStretch(1, 1, 3, 4); // size 10 (2x), aura scale 2
        assertArrayEquals(new float[]{1, 1}, ordinary);
        assertArrayEquals(new float[]{4, 4}, large);
        var powered = HdAuraPlan.capStretch(2, 8, 3);
        var largePowered = HdAuraPlan.capNpcStretch(2, 8, 3, 4);
        assertEquals(powered[0] * 4, largePowered[0]);
        assertEquals(powered[1] * 4, largePowered[1]);
        assertArrayEquals(ordinary, HdAuraPlan.capNpcStretch(1, 1, 3, Float.NaN));
    }

    @Test void nativeNpcAuraHookDoesNotDependOnEitherOptionalNpcMod() {
        assertTrue(SharedNpcMixinPolicy.shouldApply("compat.shared.DmzNpcAuraScaleMixin", false, false));
        assertFalse(SharedNpcMixinPolicy.shouldApply("compat.shared.CustomNpcUnrelatedMixin", false, false));
    }
}
