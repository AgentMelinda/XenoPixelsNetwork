package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffekseerConfigTest {
    @Test
    void defaultsRoundTripAndClampThroughTheSavedData() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            XenoServerConfig.Data d = new XenoServerConfig.Data();
            assertTrue(d.effekseerEnabled && d.effekseerPunches && d.effekseerHakai && d.effekseerMissiles);
            assertEquals(64, d.effekseerRange);
            assertEquals(256, d.effekseerMissileRange);
            assertEquals(24, d.effekseerPunchesPerTick);
            d.effekseerHakai = false;
            d.effekseerRange = -5;
            d.effekseerMissileRange = 99_999;
            d.effekseerPunchesPerTick = 0;
            XenoServerConfig.apply(d);
            assertFalse(XenoServerConfig.effekseerHakai);
            assertEquals(8, XenoServerConfig.effekseerRange, "clamped to at least 8 blocks");
            assertEquals(2048, XenoServerConfig.effekseerMissileRange, "clamped to at most 2048 blocks");
            assertEquals(1, XenoServerConfig.effekseerPunchesPerTick, "at least one per tick");
            assertFalse(XenoServerConfig.snapshot().effekseerHakai);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }
}
