package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedBodyBuildPolicyTest {
    @Test void migratesAnOldZeroBudgetToTheBoundedDefault() {
        var migrated = GeneratedBodyBuildPolicy.resolve(18, 0, true);
        assertTrue(migrated.enabled());
        assertEquals(2_000, migrated.activeVoxelsPerTick());
    }

    @Test void migratesVersionlessLegacyZeroToTheBoundedDefault() {
        var migrated = GeneratedBodyBuildPolicy.resolve(null, 0, true);
        assertTrue(migrated.enabled());
        assertEquals(2_000, migrated.activeVoxelsPerTick());
    }

    @Test void honorsTheExplicitOffSwitchWhileRetainingTheConfiguredCeiling() {
        var disabled = GeneratedBodyBuildPolicy.resolve(19, 750, false);
        assertFalse(disabled.enabled());
        assertEquals(750, disabled.voxelsPerTick());
        assertEquals(0, disabled.activeVoxelsPerTick());
    }

    @Test void clampsNewConfigurationToAUsefulPositiveCeiling() {
        assertEquals(1, GeneratedBodyBuildPolicy.resolve(19, 0, true).activeVoxelsPerTick());
        assertEquals(2_000, GeneratedBodyBuildPolicy.resolve(19, Integer.MAX_VALUE, true).activeVoxelsPerTick());
        assertEquals(1, GeneratedBodyBuildPolicy.resolve(19, Integer.MIN_VALUE, true).activeVoxelsPerTick());
    }
}
