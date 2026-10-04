package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29 owner: "when a ki attack explodes we want the punch animation to happen instead of
 * normal dmz ki blast fx". DMZ's explosion visual (KiExplosionVisualEntity) is replaced by the
 * punch impact effect at the same spot, sized to the blast.
 */
class KiImpactTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final List<EffekSender.Request> sent = new ArrayList<>();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
        XenoEffects.resetForTest();
    }

    @Test
    void theKiImpactPlaysThePunchEffect() {
        assertEquals("punch_heavy/punch_heavy", EffectSlot.KI_IMPACT.path());
        assertEquals(EffectGate.Category.KI, EffectSlot.KI_IMPACT.category());
        assertTrue(EffectSlot.KI_IMPACT.upright(), "an explosion has no facing");
    }

    @Test
    void itIsSizedToTheBlast() {
        assertEquals(1.0f, KiImpactRules.size(4.0f), 1e-6);
        assertEquals(2.5f, KiImpactRules.size(10.0f), 1e-6);
        assertEquals(0.5f, KiImpactRules.size(0.5f), 1e-6, "a tiny blast still shows");
    }

    @Test
    void itHasItsOwnSwitchAndSize() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerKiImpactScale = 2.0f;
        assertTrue(XenoEffects.play(null, EffectSlot.KI_IMPACT, Vec3.ZERO, null, 1.0f, -1));
        assertEquals(2.0f * XenoServerConfig.slotScale(EffectSlot.KI_IMPACT), sent.get(0).scale(), 1e-6,
                "category size x the effect's own size (0.5 by default)");
        XenoServerConfig.effekseerKiImpacts = false;
        assertFalse(XenoEffects.play(null, EffectSlot.KI_IMPACT, Vec3.ZERO, null, 1.0f, -1),
                "off: DMZ's own explosion visual shows");
    }

    /** 2026-09-29 owner: "fxscale_ki_impact 0.5" as the default. */
    @Test
    void theKiImpactIsHalfSizeByDefault() {
        assertEquals(0.5f, new XenoServerConfig.Data().slotScale(EffectSlot.KI_IMPACT), 1e-6);
    }
}
