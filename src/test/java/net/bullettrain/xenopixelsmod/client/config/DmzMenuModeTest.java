package net.bullettrain.xenopixelsmod.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menu-mode switch is the only way back to DragonMineZ's untouched screens, and the only way to
 * pick between the two Xeno rebuilds. Every one of those routes has to survive a config round-trip,
 * so they are pinned here rather than left to whichever rebuild landed last.
 */
class DmzMenuModeTest {

    @Test
    void everyModeRoundTripsThroughItsId() {
        for (DmzMenuMode mode : DmzMenuMode.values()) {
            assertEquals(mode, DmzMenuMode.parse(mode.id(), DmzMenuMode.THEME),
                    mode + " did not survive a config round-trip");
        }
    }

    @Test
    void neonIsItsOwnMode() {
        assertEquals(DmzMenuMode.NEON, DmzMenuMode.parse("neon", DmzMenuMode.STOCK));
        assertEquals(DmzMenuMode.NEON, DmzMenuMode.parse("  NEON  ", DmzMenuMode.STOCK));
        // The two rebuilds are kept side by side; neither may collapse into the other.
        assertFalse(DmzMenuMode.NEON == DmzMenuMode.SCREEN);
        assertTrue(DmzMenuMode.usage().contains("neon"));
    }

    @Test
    void aliasesMeanTheFirstRebuild() {
        assertEquals(DmzMenuMode.SCREEN, DmzMenuMode.parse("bt3", DmzMenuMode.STOCK));
        assertEquals(DmzMenuMode.SCREEN, DmzMenuMode.parse("v3", DmzMenuMode.STOCK));
        assertEquals(DmzMenuMode.SCREEN, DmzMenuMode.parse(" V3 ", DmzMenuMode.STOCK));
    }

    @Test
    void unknownAndBlankFallBack() {
        assertEquals(DmzMenuMode.STOCK, DmzMenuMode.parse(null, DmzMenuMode.STOCK));
        assertEquals(DmzMenuMode.STOCK, DmzMenuMode.parse("   ", DmzMenuMode.STOCK));
        assertEquals(DmzMenuMode.STOCK, DmzMenuMode.parse("chrome", DmzMenuMode.STOCK));
    }

    @Test
    void themedMenusAreWhatTheModShipsWith() {
        // XenoHudConfig reads this constant for its field initialiser, its version-11 migration and
        // its parse fallback alike, so pinning it here pins all three and none of them can drift.
        // Asserted on the constant rather than on XenoHudConfig's live field, which other test
        // classes legitimately move about while checking the themed paths.
        assertEquals(DmzMenuMode.THEME, DmzMenuMode.DEFAULT);
        assertTrue(DmzMenuMode.DEFAULT.themed());
    }

    @Test
    void onlyStockLeavesDragonMineZAlone() {
        assertFalse(DmzMenuMode.STOCK.themed());
        assertTrue(DmzMenuMode.THEME.themed());
        assertTrue(DmzMenuMode.SCREEN.themed());
        // Pages the neon rebuild has not replaced still fall through to the themed DMZ screen.
        assertTrue(DmzMenuMode.NEON.themed());
    }
}
