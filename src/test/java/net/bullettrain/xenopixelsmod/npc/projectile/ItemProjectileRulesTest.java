package net.bullettrain.xenopixelsmod.npc.projectile;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ItemProjectileRulesTest {
    @Test void targetBoundsRejectUnboundedAndDegenerateVectorsBeforeSpawningOrSteering() {
        assertTrue(ItemProjectileRules.allowedDistance(256 * 256));
        assertTrue(ItemProjectileRules.allowedDistance(1));
        assertFalse(ItemProjectileRules.allowedDistance(257 * 257));
        assertFalse(ItemProjectileRules.allowedDistance(0));
        assertFalse(ItemProjectileRules.allowedDistance(-1));
        assertFalse(ItemProjectileRules.allowedDistance(Double.NaN));
        assertFalse(ItemProjectileRules.allowedDistance(Double.POSITIVE_INFINITY));
        assertFalse(ItemProjectileRules.allowedDistance(Double.NEGATIVE_INFINITY));
    }

    @Test void corruptSavedOrScriptedSettingsCannotProduceNonFinitePhysicsOrAnUnlimitedLifetime() {
        assertDoesNotThrow(() -> ItemProjectileRules.accuracy(0));
        assertDoesNotThrow(() -> ItemProjectileRules.accuracy(100));
        assertThrows(IllegalArgumentException.class, () -> ItemProjectileRules.accuracy(-1));
        assertThrows(IllegalArgumentException.class, () -> ItemProjectileRules.accuracy(101));
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1, 0, Float.MAX_VALUE}) {
            assertTrue(Float.isFinite(ItemProjectileRules.speed(value)));
            assertTrue(ItemProjectileRules.speed(value) > 0 && ItemProjectileRules.speed(value) <= 3);
            assertTrue(Float.isFinite(ItemProjectileRules.damage(value)));
            assertTrue(ItemProjectileRules.damage(value) >= 0 && ItemProjectileRules.damage(value) <= 1000000);
        }
        assertTrue(ItemProjectileRules.expired(-1));
        assertFalse(ItemProjectileRules.expired(0));
        assertFalse(ItemProjectileRules.expired(199));
        assertTrue(ItemProjectileRules.expired(200));
        assertTrue(ItemProjectileRules.expired(Integer.MAX_VALUE));
    }

    @Test void zeroConfiguredStrengthRemainsHarmless() {
        assertEquals(0f, ItemProjectileRules.damage(0f));
        assertEquals(1f, ItemProjectileRules.damage(Float.NaN));
        assertEquals(1f, ItemProjectileRules.damage(-1f));
    }
}
