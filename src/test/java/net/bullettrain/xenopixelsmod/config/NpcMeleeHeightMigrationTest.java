package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcMeleeHeightMigrationTest {
    @Test
    void aSavedOldDefaultGetsTheReducedHeightReach() {
        assertEquals(0.5, XenoServerConfig.migrateNpcMeleeHeightReach(24, 1.5));
        assertEquals(0.5, XenoServerConfig.migrateNpcMeleeHeightReach(0, 1.5));
    }

    @Test
    void customValuesAndLaterOverridesSurvive() {
        assertEquals(0.0, XenoServerConfig.migrateNpcMeleeHeightReach(24, 0.0));
        assertEquals(0.8, XenoServerConfig.migrateNpcMeleeHeightReach(24, 0.8));
        assertEquals(1.5, XenoServerConfig.migrateNpcMeleeHeightReach(25, 1.5));
        assertEquals(1.5, XenoServerConfig.migrateNpcMeleeHeightReach(26, 1.5));
    }

    @Test
    void applyingTheMigrationAgainDoesNotChangeTheResult() {
        double migrated = XenoServerConfig.migrateNpcMeleeHeightReach(24, 1.5);
        assertEquals(migrated, XenoServerConfig.migrateNpcMeleeHeightReach(24, migrated));
    }
}
