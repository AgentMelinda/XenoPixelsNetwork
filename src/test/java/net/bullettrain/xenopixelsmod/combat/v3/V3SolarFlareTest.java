package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class V3SolarFlareTest {
    @Test void occurrenceSectionNameDoesNotOverrideTheAttackName() {
        assertFalse(V3SolarFlare.isSolarFlare(
                "xenopixelsmod:bt3_solar_flare_551_artiglio_del_drago_564", "Dragon Claw"));
        assertTrue(V3SolarFlare.isSolarFlare("addon:other", "Esplosione Solare"));
        assertTrue(V3SolarFlare.isSolarFlare("addon:other", "Solar Explosion"));
        assertTrue(V3SolarFlare.isSolarFlare("addon:solar_flare", null));
        assertTrue(V3SolarFlare.isSolarFlare("addon:esplosione_solare", "  "));
    }

    @Test void detectsSolarFlareIdsAndEnglishNames() {
        assertTrue(V3SolarFlare.isSolarFlare("xenopixelsmod:bt3_early_goku_early_esplosione_solare_69", "Solar Flare"));
        assertTrue(V3SolarFlare.isSolarFlare("xenopixelsmod:bt3_solar_explosion_esplosione_solare_1676", "Solar Flare"));
        assertTrue(V3SolarFlare.isSolarFlare("xenopixelsmod:bt3_other", "Taiyoken"));
        assertFalse(V3SolarFlare.isSolarFlare("xenopixelsmod:bt3_early_kid_goku_kamehameha_15", "Kamehameha"));
        assertFalse(V3SolarFlare.isSolarFlare(null, null));
    }
}
