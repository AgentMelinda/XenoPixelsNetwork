package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29 owner:
 * - "when im holding left click to punch and pressing w with it it send a kick that flies a player
 *   to the sky i only want it to happen w + charged kick key";
 * - "make the charged kick to be able to only click once the wheel to send a kick instead of
 *   holding to charge but dont delete hold to charge";
 * - "charge punch knockback distance configurable and if parabolic path of knockback too".
 */
class Bt3KickAndPunchRulesTest {

    @Test
    void punchPlusWLaunchesOnlyWhenTheOldRouteIsOn() {
        assertFalse(Bt3KickAndPunchRules.mashLaunches(true, false, false), "W tap during a punch string: off");
        assertFalse(Bt3KickAndPunchRules.mashLaunches(false, true, false), "W held during a punch string: off");
        assertTrue(Bt3KickAndPunchRules.mashLaunches(true, false, true), "/xenobind mashlauncher on brings it back");
        assertFalse(Bt3KickAndPunchRules.mashLaunches(false, false, true));
    }

    @Test
    void aClickedKickLandsAtTheTapStrengthAndAHeldOneKeepsItsCharge() {
        assertEquals(0.5f, Bt3KickAndPunchRules.kickCharge(0.25f, 0.5f), 1e-6, "a click kicks at the tap strength");
        assertEquals(0.8f, Bt3KickAndPunchRules.kickCharge(0.8f, 0.5f), 1e-6, "holding still charges past it");
        assertEquals(1.0f, Bt3KickAndPunchRules.kickCharge(1.0f, 0.5f), 1e-6);
    }

    @Test
    void theDefaultPunchKnockbackIsUnchanged() {
        Vec3 away = new Vec3(1, 0, 0);
        Vec3 v = Bt3KickAndPunchRules.chargePunchKnockback(away, 1.0f, 1.0f, false, 1.0f);
        assertEquals(0.85 * 1.6, v.x, 1e-9);
        assertEquals(0.18 + 0.15, v.y, 1e-9);
    }

    @Test
    void distanceScalesAndTheArcIsAParabolaWhenOn() {
        Vec3 away = new Vec3(0, 0, 1);
        Vec3 far = Bt3KickAndPunchRules.chargePunchKnockback(away, 1.0f, 2.0f, false, 1.0f);
        assertEquals(2 * 0.85 * 1.6, far.z, 1e-9, "twice the distance");
        Vec3 arc = Bt3KickAndPunchRules.chargePunchKnockback(away, 1.0f, 1.0f, true, 1.5f);
        assertEquals(1.5, arc.y, 1e-9, "full charge throws at the arc height");
        assertTrue(arc.y > 0.33, "higher than the flat shove, so it flies in an arc");
        Vec3 halfArc = Bt3KickAndPunchRules.chargePunchKnockback(away, 0.25f, 1.0f, true, 1.5f);
        assertTrue(halfArc.y < arc.y, "a weaker charge arcs lower");
    }
}
