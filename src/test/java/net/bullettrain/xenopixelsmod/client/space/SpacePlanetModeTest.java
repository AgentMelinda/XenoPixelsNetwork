package net.bullettrain.xenopixelsmod.client.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Planet presentation defaults to physical bodies with optional projections. No render thread needed. */
class SpacePlanetModeTest {
    @Test void oldProjectionSettingsMigrateToPhysicalBodies() {
        assertEquals(SpacePlanetMode.BODIES, SpacePlanetMode.byName("proxies"));
        assertEquals(SpacePlanetMode.BODIES, SpacePlanetMode.byName("planets"));
        assertEquals(SpacePlanetMode.BODIES, SpacePlanetMode.byName("  BODIES "));
        assertEquals(SpacePlanetMode.OFF, SpacePlanetMode.byName("off"));
        assertEquals(SpacePlanetMode.BODIES, SpacePlanetMode.byName(null));
        assertEquals(SpacePlanetMode.BODIES, SpacePlanetMode.byName("not-a-mode"));
    }

    @Test void modeIdsAreTheCommandLiterals() {
        assertEquals("bodies", SpacePlanetMode.BODIES.id);
        assertEquals("off", SpacePlanetMode.OFF.id);
        assertEquals(2, SpacePlanetMode.values().length);
    }

    @Test void chartProjectionsAreAvailableAlongsidePhysicalBodies() {
        assertTrue(SpacePlanetMode.BODIES.drawsProxies());
        assertTrue(SpacePlanetMode.BODIES.drawsBodies());
        assertFalse(SpacePlanetMode.OFF.drawsProxies());
        assertFalse(SpacePlanetMode.OFF.drawsBodies());
    }

    @Test void chartProjectionsOnlyRenderInTheCanonicalVoidsea() {
        assertTrue(SpacePlanetMode.BODIES.drawsProxiesIn("xenopixelsmod:voidsea"));
        assertFalse(SpacePlanetMode.BODIES.drawsProxiesIn("xenospace_spike:orbit"));
        assertFalse(SpacePlanetMode.BODIES.drawsProxiesIn("minecraft:overworld"));
        assertFalse(SpacePlanetMode.OFF.drawsProxiesIn("xenopixelsmod:voidsea"));
        assertFalse(SpacePlanetMode.BODIES.drawsProxiesIn(null));
    }
}
