package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of a punch a tired NPC still lands.
 *
 * <p>This replaced an all-or-nothing stamina gate that dropped an unaffordable punch to a flat
 * {@code 1.0} damage. A punch costs stamina in proportion to its own damage while the pool is set by
 * RES, so any NPC built to hit hard could never pay — and an NPC with millions of strength scratched
 * for one. The stat driving the damage stopped mattering at all, which is what "their DMZ damage
 * doesn't translate to actual stats" described.
 */
class NpcStaminaAffordabilityTest {

    @Test
    void afullPoolPaysTheWholePunch() {
        assertEquals(1.0, NpcStatMath.affordableFraction(500.0, 100.0), 1.0e-9);
        assertEquals(1.0, NpcStatMath.affordableFraction(100.0, 100.0), 1.0e-9);
    }

    /**
     * A half-funded punch lands about half.
     *
     * <p>This is the whole point: stamina still governs how hard an NPC hits, it just no longer
     * decides whether its strength counts.
     */
    @Test
    void aPartlyFundedPunchScalesWithWhatWasPaid() {
        assertEquals(0.5, NpcStatMath.affordableFraction(50.0, 100.0), 1.0e-9);
        assertEquals(0.25, NpcStatMath.affordableFraction(25.0, 100.0), 1.0e-9);
    }

    /**
     * An empty pool still lands the floor, and the floor is a share of the NPC's own damage.
     *
     * <p>Never a flat number. A strong NPC at zero stamina should still hit far harder than a weak
     * one at full, and the old fallback got that exactly backwards.
     */
    @Test
    void anEmptyPoolLandsTheFloorRatherThanNothing() {
        double fraction = NpcStatMath.affordableFraction(0.0, 100.0);
        assertEquals(NpcStatMath.MIN_TIRED_FRACTION, fraction, 1.0e-9);
        assertTrue(fraction > 0.0, "a punch that lands nothing reads as a broken NPC, not a tired one");

        double strong = 1_000_000.0 * fraction;
        double weakAtFullStrength = 50.0;
        assertTrue(strong > weakAtFullStrength,
                "an exhausted powerhouse must still out-hit a rested weakling");
    }

    /** The case that was broken: a cost far beyond anything the pool could ever hold. */
    @Test
    void anImpossibleCostStillLandsAProportionalHit() {
        double fraction = NpcStatMath.affordableFraction(20.0, 178_000_000.0);
        assertEquals(NpcStatMath.MIN_TIRED_FRACTION, fraction, 1.0e-9);
        assertTrue(1_000_000_000.0 * fraction > 1.0,
                "this is the exact case that used to collapse to 1.0 damage");
    }

    @Test
    void aFreePunchIsFullStrength() {
        assertEquals(1.0, NpcStatMath.affordableFraction(0.0, 0.0), 1.0e-9);
        assertEquals(1.0, NpcStatMath.affordableFraction(10.0, -5.0), 1.0e-9);
    }

    @Test
    void brokenNumbersDoNotProduceABrokenPunch() {
        assertEquals(1.0, NpcStatMath.affordableFraction(10.0, Double.NaN), 1.0e-9);
        assertEquals(NpcStatMath.MIN_TIRED_FRACTION,
                NpcStatMath.affordableFraction(Double.NaN, 100.0), 1.0e-9);
        for (double f : new double[] {
                NpcStatMath.affordableFraction(-50.0, 100.0),
                NpcStatMath.affordableFraction(Double.POSITIVE_INFINITY, 100.0)}) {
            assertTrue(f >= 0.0 && f <= 1.0, "fraction escaped 0..1: " + f);
        }
    }

    /**
     * Race and class now reach damage, not only health.
     *
     * <p>Two NPCs with identical strength and different scaling used to punch identically while
     * their health differed, because the scaling lookup existed for vitality alone.
     */
    @Test
    void raceAndClassScalingChangesDamage() {
        double plain = NpcStatMath.meleeDamage(1_000, 1.0, 1.0, 1.0);
        double warrior = NpcStatMath.meleeDamage(1_000, 1.0, 1.2, 1.0);
        assertTrue(warrior > plain, "a class with higher strength scaling has to hit harder");
        assertEquals(1.0 + 1_000 * 1.2, warrior, 1.0e-6);
    }

    @Test
    void scalingAppliesToStrikeAndKiToo() {
        assertTrue(NpcStatMath.strikeDamage(500, 500, 1.0, 1.0, 1.3, 1.2, 1.0)
                        > NpcStatMath.strikeDamage(500, 500, 1.0, 1.0, 1.0, 1.0, 1.0),
                "strike damage must scale with race and class");
        assertTrue(NpcStatMath.kiDamage(500, 1.0, 1.4, 1.0)
                        > NpcStatMath.kiDamage(500, 1.0, 1.0, 1.0),
                "ki damage must scale with race and class");
    }

    /** A missing or nonsensical scaling leaves the damage alone rather than erasing it. */
    @Test
    void anUnsetScalingIsNeutral() {
        double neutral = NpcStatMath.meleeDamage(1_000, 1.0, 1.0, 1.0);
        assertEquals(neutral, NpcStatMath.meleeDamage(1_000, 1.0, 0.0, 1.0), 1.0e-9);
        assertEquals(neutral, NpcStatMath.meleeDamage(1_000, 1.0, Double.NaN, 1.0), 1.0e-9);
        assertEquals(neutral, NpcStatMath.meleeDamage(1_000, 1.0, -3.0, 1.0), 1.0e-9);
    }
}
