package net.bullettrain.xenopixelsmod.combat.v2;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class V2VanishLandingTest {
    @Test
    void existingTwelveBlockConfigFollowsTheFullLockRangeByDefault() {
        var cfg = new Gson().fromJson("{\"version\":2,\"vanishRange\":12}", V2Config.Values.class);
        assertTrue(cfg.vanishUsesLockRange);
        double range = LockRules.range(128, 1, false);
        assertEquals(range + LockRules.SLACK, V2VanishLanding.range(cfg, range));
        assertTrue(Math.hypot(30, 25) < V2VanishLanding.range(cfg, range));
    }

    @Test
    void shorterReachIsStillAnExplicitServerOption() {
        var cfg = new V2Config.Values();
        cfg.vanishUsesLockRange = false;
        cfg.vanishRange = 18;
        assertEquals(18, V2VanishLanding.range(cfg, 133));
    }
}
