package net.bullettrain.xenopixelsmod.compat.sable;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableContraptionCullTest {
    @Test
    void infiniteInvertedAndContinentBoxesAreUnqueryable() {
        assertTrue(SableContraptionCull.isUnqueryable(null));
        assertTrue(SableContraptionCull.isUnqueryable(new AABB(
                Double.NEGATIVE_INFINITY, 0, 0, Double.POSITIVE_INFINITY, 1, 1)));
        assertTrue(SableContraptionCull.isUnqueryable(new AABB(Double.NaN, 0, 0, 1, 1, 1)));
        assertTrue(SableContraptionCull.isUnqueryable(new AABB(0, 0, 0, 2_000_000, 1, 1)));
        assertFalse(SableContraptionCull.isUnqueryable(new AABB(0, -2015, 0, 4, -2010, 4)));
        assertFalse(SableContraptionCull.isUnqueryable(new AABB(-96, -2015, 71, -90, -2010, 77)));
    }
}
