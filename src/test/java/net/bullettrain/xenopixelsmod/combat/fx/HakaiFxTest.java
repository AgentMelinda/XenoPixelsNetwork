package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HakaiFxTest {
    @Test
    void rgbUnpacksPackedColor() {
        Vector3f body = HakaiFx.rgb(0xF233F2);
        assertEquals(0xF2 / 255.0f, body.x, 1.0e-5f);
        assertEquals(0x33 / 255.0f, body.y, 1.0e-5f);
        assertEquals(0xF2 / 255.0f, body.z, 1.0e-5f);
    }

    @Test
    void coreIsSeventyFivePercentTowardBlack() {
        Vector3f core = HakaiFx.coreColor(new Vector3f(1.0f, 0.40f, 0.80f));
        assertEquals(0.75f, core.x, 1.0e-5f);
        assertEquals(0.30f, core.y, 1.0e-5f);
        assertEquals(0.60f, core.z, 1.0e-5f);
    }

    @Test
    void masterFxOffKillsDustAndSilhouette() {
        boolean fx = XenoServerConfig.hakaiFxEnabled;
        boolean dust = XenoServerConfig.hakaiDustEnabled;
        boolean sil = XenoServerConfig.hakaiSilhouetteEnabled;
        try {
            XenoServerConfig.hakaiFxEnabled = true;
            XenoServerConfig.hakaiDustEnabled = true;
            XenoServerConfig.hakaiSilhouetteEnabled = true;
            assertTrue(HakaiFx.dustEnabled());
            assertTrue(HakaiFx.silhouetteEnabled());

            XenoServerConfig.hakaiFxEnabled = false;
            assertFalse(HakaiFx.dustEnabled());
            assertFalse(HakaiFx.silhouetteEnabled());

            XenoServerConfig.hakaiFxEnabled = true;
            XenoServerConfig.hakaiDustEnabled = false;
            XenoServerConfig.hakaiSilhouetteEnabled = false;
            assertFalse(HakaiFx.dustEnabled());
            assertFalse(HakaiFx.silhouetteEnabled());
        } finally {
            XenoServerConfig.hakaiFxEnabled = fx;
            XenoServerConfig.hakaiDustEnabled = dust;
            XenoServerConfig.hakaiSilhouetteEnabled = sil;
        }
    }
}
