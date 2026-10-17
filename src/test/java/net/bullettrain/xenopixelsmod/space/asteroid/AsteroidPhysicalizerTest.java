package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidPhysicalizerTest {
    @Test
    void mixinBridgeTypeLivesOutsideTheMixinPackage() {
        assertEquals("net.bullettrain.xenopixelsmod.space.asteroid",
                SableHeatMapFractureAccess.class.getPackageName());
    }

    @Test
    void heatMapBootstrapClearsAirCellsAndReaddsCurrentSolids() {
        assertTrue(AsteroidPhysicalizer.shouldVisitHeatMapCell(true, false),
                "clear pass must erase stale heat values from formerly-solid cells");
        assertFalse(AsteroidPhysicalizer.shouldVisitHeatMapCell(false, false),
                "add pass must not register air");
        assertTrue(AsteroidPhysicalizer.shouldVisitHeatMapCell(false, true),
                "add pass must inspect source solid positions");
        assertTrue(AsteroidPhysicalizer.shouldVisitHeatMapCell(false, false, true),
                "add pass must also include player-placed solids inside the scanned bounds");
    }

    @Test
    void dirtyHeatMapRestartCircuitBreakerStopsAfterConfiguredSafetyLimit() {
        assertFalse(AsteroidPhysicalizer.shouldAbortDirtyHeatMapRestart(1, 2));
        assertTrue(AsteroidPhysicalizer.shouldAbortDirtyHeatMapRestart(2, 2));
        assertTrue(AsteroidPhysicalizer.shouldAbortDirtyHeatMapRestart(3, 2));
    }

    @Test void explicitBlockEditSurvivesEqualMass() {
        CompoundTag userData = new CompoundTag();
        CompoundTag asteroid = new CompoundTag();
        asteroid.putBoolean("touched", true);
        userData.put(AsteroidPhysicalizer.TAG, asteroid);

        assertTrue(AsteroidPhysicalizer.isTouched(userData, 100.0, 100.0));
    }

    @Test void massChangeRemainsFallbackForNonBlockEventChanges() {
        assertTrue(AsteroidPhysicalizer.isTouched(new CompoundTag(), 100.0, 95.0));
        assertFalse(AsteroidPhysicalizer.isTouched(new CompoundTag(), 100.0, 100.0));
        assertFalse(AsteroidPhysicalizer.isTouched(null, Double.NaN, 95.0));
    }
}
