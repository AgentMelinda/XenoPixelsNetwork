package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner: Hakai like Dragon Ball Super (Beerus erasing Zamasu) - a purple veil on the
 * victim, the body vanishing slowly from the head down, flakes breaking off where it vanishes.
 */
class HakaiEffectRulesTest {

    @Test
    void theCrumbleFollowsTheBodyWipeFromHeadToFeet() {
        for (float p : new float[] {0.1f, 0.35f, 0.6f, 0.9f}) {
            float expected = 1.0f - HakaiFade.charged(p, 1.0f, 1.0f);
            assertEquals(expected, HakaiEffectRules.crumbleLine(p, 1.0f, 1.0f), 1e-6,
                    "flakes break off exactly where the client stops drawing the body, p=" + p);
        }
        assertTrue(HakaiEffectRules.crumbleLine(0.2f, 1.0f, 1.0f) > HakaiEffectRules.crumbleLine(0.7f, 1.0f, 1.0f),
                "the line travels down");
        assertEquals(0.5f, HakaiEffectRules.crumbleLine(0.25f, 2.0f, 1.0f), 1e-6, "hakaiFadeSpeed applies");
    }

    @Test
    void nothingCrumblesBeforeTheWipeStartsOrAfterItReachesTheFeet() {
        assertFalse(HakaiEffectRules.crumbling(0.0f, 1.0f, 1.0f));
        assertTrue(HakaiEffectRules.crumbling(0.3f, 1.0f, 1.0f));
        assertFalse(HakaiEffectRules.crumbling(1.0f, 1.0f, 1.0f));
        assertFalse(HakaiEffectRules.crumbling(0.6f, 2.0f, 1.0f), "speed 2: body already gone at 0.5");
    }

    @Test
    void effectsAreSizedToTheBody() {
        assertEquals(1.0f, HakaiEffectRules.bodyScale(1.8f), 1e-6, "authored for a 1.8-block body");
        assertEquals(2.0f, HakaiEffectRules.bodyScale(3.6f), 1e-6);
        assertEquals(0.4f, HakaiEffectRules.bodyScale(0.1f), 1e-6, "tiny mobs keep a visible effect");
        assertEquals(4.0f, HakaiEffectRules.bodyScale(40f), 1e-6, "giants are capped");
        assertEquals(1.0f, HakaiEffectRules.widthScale(0.6f), 1e-6, "crumble ring fits the body width");
    }

    @Test
    void thePalmFlameSitsAtTheRaisedHandInFrontOfTheCaster() {
        Vec3 feet = new Vec3(10, 64, 10);
        Vec3 look = new Vec3(0, 0, 1);
        Vec3 hand = HakaiEffectRules.palm(feet, 1.8f, look);
        assertEquals(10.0, hand.x, 1e-6);
        assertTrue(hand.z > 10.3 && hand.z < 11.0, "in front of the caster: " + hand);
        assertTrue(hand.y > 64 + 1.2 && hand.y < 64 + 1.8, "raised to about shoulder height: " + hand);
    }

    @Test
    void theCrumbleClockPulsesFasterThanTheVeil() {
        HakaiFx.PulseClock crumble = new HakaiFx.PulseClock(HakaiEffectRules.CRUMBLE_INTERVAL);
        int n = 0;
        for (int i = 0; i < 20; i++) if (crumble.due(3, 100 + 2L * i)) n++;
        assertEquals(10, n, "40 ticks of channel, a crumble every 4 ticks");
    }
}
