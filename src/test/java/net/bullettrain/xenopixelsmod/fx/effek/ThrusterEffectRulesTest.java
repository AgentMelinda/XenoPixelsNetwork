package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-29 owner: a realistic reddish plume with smoke from ship thrusters, not vanilla flames. */
class ThrusterEffectRulesTest {

    @Test
    void thePlumeStartsJustOutsideTheExhaustFace() {
        Vec3 nozzle = ThrusterEffectRules.nozzle(new Vec3(10.5, 64.5, 10.5), new Vec3(0, 0, -1));
        assertEquals(10.5, nozzle.x, 1e-9);
        assertEquals(64.5, nozzle.y, 1e-9);
        assertEquals(9.95, nozzle.z, 1e-9);
    }

    @Test
    void itGrowsWithThrottle() {
        assertEquals(0.4f, ThrusterEffectRules.scale(0.0), 1e-6, "idle still shows a small plume");
        assertEquals(0.7f, ThrusterEffectRules.scale(0.5), 1e-6);
        assertEquals(1.0f, ThrusterEffectRules.scale(1.0), 1e-6);
        assertEquals(1.0f, ThrusterEffectRules.scale(7.0), 1e-6, "over-driven power is capped");
    }

    @Test
    void itPulsesEveryEightTicksOnlyWhileThrusting() {
        int n = 0;
        for (long t = 0; t < 80; t++) if (ThrusterEffectRules.pulseDue(t, 0.5)) n++;
        assertEquals(10, n);
        for (long t = 0; t < 80; t++) assertFalse(ThrusterEffectRules.pulseDue(t, 0.04), "below the lit threshold");
    }

    @Test
    void theVanillaPlumeIsOnlyTheFallback() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        assertFalse(ThrusterEffectRules.clientDrawsVanilla(d), "Effekseer plume by default");
        d.effekseerShipThrusters = false;
        assertTrue(ThrusterEffectRules.clientDrawsVanilla(d));
        d.effekseerShipThrusters = true;
        d.effekseerEnabled = false;
        assertTrue(ThrusterEffectRules.clientDrawsVanilla(d));
        assertFalse(ThrusterEffectRules.clientDrawsVanilla(null), "before the first sync, like single player");
    }
}
