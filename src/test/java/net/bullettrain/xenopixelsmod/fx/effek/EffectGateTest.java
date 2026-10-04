package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectGateTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final EffectGate gate = new EffectGate();

    @AfterEach
    void restore() { XenoServerConfig.apply(saved); }

    @Test
    void slotsNameTheirFolders() {
        assertEquals("punch_heavy/punch_heavy", EffectSlot.PUNCH_HEAVY.path());
        assertEquals(EffectGate.Category.MISSILE, EffectSlot.MISSILE_THRUSTER.category());
        assertEquals(1.6f, EffectSlot.PUNCH_HEAVY.defaultScale(), 1e-6);
    }

    @Test
    void configSwitchesEachCategoryAndTheWhole() {
        XenoServerConfig.effekseerHakai = false;
        assertFalse(gate.allow(EffectSlot.HAKAI_CHANNEL, 1, 7));
        assertTrue(gate.allow(EffectSlot.MISSILE_EXPLOSION, 1, -1));
        XenoServerConfig.effekseerEnabled = false;
        assertFalse(gate.allow(EffectSlot.MISSILE_EXPLOSION, 2, -1));
    }

    @Test
    void oneTargetGetsOnePunchPerTick() {
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 10, 42));
        assertFalse(gate.allow(EffectSlot.PUNCH_HEAVY, 10, 42), "same target, same tick");
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 10, 43));
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 11, 42), "next tick is fine");
    }

    @Test
    void punchesStopAtThePerTickCap() {
        XenoServerConfig.effekseerPunchesPerTick = 3;
        int played = 0;
        for (int target = 0; target < 50; target++) if (gate.allow(EffectSlot.PUNCH_IMPACT, 5, target)) played++;
        assertEquals(3, played);
        assertTrue(gate.allow(EffectSlot.MISSILE_EXPLOSION, 5, -1), "the cap is for punches only");
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 6, 0), "a new tick resets it");
    }

    @Test
    void rangesComeFromConfig() {
        assertEquals(64, gate.range(EffectSlot.PUNCH_IMPACT));
        assertEquals(64, gate.range(EffectSlot.HAKAI_ERASE));
        assertEquals(256, gate.range(EffectSlot.MISSILE_EXPLOSION));
    }
}
