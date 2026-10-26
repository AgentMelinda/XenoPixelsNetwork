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
        assertEquals(1, GeneratedBodyBuildPolicy.resolve(20, 0, true).activeVoxelsPerTick());
        assertEquals(2_000, GeneratedBodyBuildPolicy.resolve(20, Integer.MAX_VALUE, true).activeVoxelsPerTick());
        assertEquals(1, GeneratedBodyBuildPolicy.resolve(20, Integer.MIN_VALUE, true).activeVoxelsPerTick());
    }

    @Test void migratesMissingLegacyBuildTimeToTheBoundedDefault() {
        assertEquals(8.0, GeneratedBodyBuildPolicy.resolve(19, 2_000, true, 0.0, false).millisPerTick(), 0.0);
        assertEquals(8.0, GeneratedBodyBuildPolicy.resolve(null, 2_000, true, 0.0, false).millisPerTick(), 0.0);
    }

    @Test void preservesAnExplicitLegacyZeroBuildTimeOptOut() {
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(19, 2_000, true, 0.0, true).millisPerTick(), 0.0);
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(null, 2_000, true, 0.0, true).millisPerTick(), 0.0);
    }

    @Test void preservesExplicitPositiveLegacyBuildTimeWhenMigrating() {
        assertEquals(3.5, GeneratedBodyBuildPolicy.resolve(19, 2_000, true, 3.5).millisPerTick(), 0.0);
        assertEquals(20.0, GeneratedBodyBuildPolicy.resolve(19, 2_000, true, 25.0).millisPerTick(), 0.0);
    }

    @Test void preservesAnExplicitNewBuildTimeBudgetAndAllowsExplicitOptOut() {
        assertEquals(3.5, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, 3.5).millisPerTick(), 0.0);
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, 0.0).millisPerTick(), 0.0);
    }

    @Test void clampsInvalidOrOutOfRangeBuildTimeBudgets() {
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, Double.NaN).millisPerTick(), 0.0);
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, Double.POSITIVE_INFINITY).millisPerTick(), 0.0);
        assertEquals(20.0, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, 21.0).millisPerTick(), 0.0);
        assertEquals(0.0, GeneratedBodyBuildPolicy.resolve(20, 2_000, true, -1.0).millisPerTick(), 0.0);
    }
}
