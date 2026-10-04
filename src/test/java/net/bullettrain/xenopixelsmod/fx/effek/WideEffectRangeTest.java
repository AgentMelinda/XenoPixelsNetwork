package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: the area Hakai effect was "not always running". Its veil sits a radius below
 * the aim point, so for a radius-70 Hakai it was past the 64-block send range and was not sent.
 * A wide effect's range grows with its size.
 */
class WideEffectRangeTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final List<EffekSender.Request> sent = new ArrayList<>();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
        XenoEffects.resetForTest();
    }

    @Test
    void aWideEffectIsSentFurther() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertTrue(XenoEffects.playWide(null, EffectSlot.HAKAI_CHANNEL, Vec3.ZERO, null, 1.0f, 140.0));
        assertEquals(XenoServerConfig.effekseerRange + 140.0, sent.get(0).range(), 1e-9);
        assertTrue(XenoEffects.play(null, EffectSlot.HAKAI_CHANNEL, Vec3.ZERO, null, 1.0f, -1));
        assertEquals(XenoServerConfig.effekseerRange, sent.get(1).range(), 1e-9, "ordinary effects unchanged");
    }
}
