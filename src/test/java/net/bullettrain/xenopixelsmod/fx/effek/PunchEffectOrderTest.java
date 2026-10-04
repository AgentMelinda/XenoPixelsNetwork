package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final review 2026-09-28: combo, charge and guard code calls hurt() (which fires the damage hook)
 * before its own CombatFx.impact. The hook must not win: it queues, and only plays when nothing
 * explicit drew an effect on that target that tick - and never draws vanilla particles or cues.
 */
class PunchEffectOrderTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final List<EffekSender.Request> sent = new ArrayList<>();
    private static final Vec3 AT = new Vec3(1, 65, 1);
    private static final Vec3 DIR = new Vec3(1, 0, 0);

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
        XenoEffects.resetForTest();
        PunchEffectEvents.clearForTest();
    }

    @Test
    void hookThenExplicitHeavyPlaysOnlyTheHeavyEffect() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        PunchEffectEvents.queue(null, 5, AT, DIR);
        assertEquals(XenoEffects.Outcome.PLAYED,
                XenoEffects.attempt(null, EffectSlot.PUNCH_HEAVY, AT, DIR, 1f, 5));
        assertEquals(0, PunchEffectEvents.flush(), "the explicit heavy effect already covered that hit");
        assertEquals(1, sent.size());
        assertEquals("xenopixelsmod:punch_heavy/punch_heavy", sent.get(0).id().toString());
    }

    @Test
    void aSecondEffectOnTheSameTargetIsADuplicateNotAFallback() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertEquals(XenoEffects.Outcome.PLAYED, XenoEffects.attempt(null, EffectSlot.PUNCH_GUARD, AT, DIR, 1f, 8));
        assertEquals(XenoEffects.Outcome.DUPLICATE, XenoEffects.attempt(null, EffectSlot.PUNCH_IMPACT, AT, DIR, 1f, 8));
        assertEquals(XenoEffects.Outcome.UNAVAILABLE, outcomeWithEffectsOff());
    }

    private XenoEffects.Outcome outcomeWithEffectsOff() {
        XenoServerConfig.effekseerEnabled = false;
        return XenoEffects.attempt(null, EffectSlot.PUNCH_IMPACT, AT, DIR, 1f, 9);
    }

    @Test
    void aBasicPunchAlonePlaysTheLightEffect() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        PunchEffectEvents.queue(null, 6, AT, DIR);
        PunchEffectEvents.queue(null, 6, AT, DIR);
        assertEquals(1, PunchEffectEvents.flush(), "one effect per target per tick");
        assertEquals("xenopixelsmod:punch_impact/punch_impact", sent.get(0).id().toString());
    }

    @Test
    void withEffectsOffTheHookAddsNothing() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerEnabled = false;
        PunchEffectEvents.queue(null, 7, AT, DIR);
        assertEquals(0, PunchEffectEvents.flush());
        assertTrue(sent.isEmpty());
    }

    @Test
    void aFailedSendDoesNotUseUpTheTargetsSlot() {
        XenoEffects.useSender((level, r) -> { throw new IllegalStateException("boom"); });
        assertEquals(XenoEffects.Outcome.UNAVAILABLE, XenoEffects.attempt(null, EffectSlot.PUNCH_IMPACT, AT, DIR, 1f, 3));
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertEquals(XenoEffects.Outcome.PLAYED, XenoEffects.attempt(null, EffectSlot.PUNCH_IMPACT, AT, DIR, 1f, 3));
    }
}
