package net.bullettrain.xenopixelsmod.combat.targeting;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadCalculatorTest {

    @Test
    void stationaryTargetNeedsNoLead() {
        LeadCalculator.LeadResult result = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(10, 0, 0), Vec3.ZERO, 30.0);
        assertTrue(result.solvable());
        assertEquals(10.0, result.aimPoint().x, 1.0e-6);
        assertEquals(0.0, result.aimPoint().y, 1.0e-6);
        assertEquals(0.0, result.aimPoint().z, 1.0e-6);
    }

    @Test
    void targetOutrunningTheProjectileIsUnsolvable() {
        // Fleeing directly away faster than the shot can ever catch up.
        LeadCalculator.LeadResult result = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(10, 0, 0), new Vec3(50, 0, 0), 20.0);
        assertFalse(result.solvable());
        // Falls back to aiming straight at the target rather than a bogus point.
        assertEquals(10.0, result.aimPoint().x, 1.0e-6);
    }

    @Test
    void crossingTargetLeadsAheadOfItsCurrentPosition() {
        // Target moving perpendicular to the shooter's line of sight; the lead point must be
        // further along its direction of travel than its current position.
        LeadCalculator.LeadResult result = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(20, 0, 0), new Vec3(0, 0, 10), 30.0);
        assertTrue(result.solvable());
        assertTrue(result.interceptTime() > 0.0);
        assertTrue(Math.abs(result.aimPoint().z) > 1.0e-6, "lead point should be offset along target motion");
    }

    @Test
    void zeroProjectileSpeedIsUnsolvable() {
        LeadCalculator.LeadResult result = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(5, 0, 0), new Vec3(1, 0, 0), 0.0);
        assertFalse(result.solvable());
    }

    @Test
    void gravityDropRaisesTheAimPointAboveTheFlatSolution() {
        LeadCalculator.LeadResult flat = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(20, 0, 0), Vec3.ZERO, 20.0);
        LeadCalculator.LeadResult dropped = LeadCalculator.withGravityDrop(
                Vec3.ZERO, new Vec3(20, 0, 0), Vec3.ZERO, 20.0, 9.8);
        assertTrue(dropped.solvable());
        // The record is the point to aim at, not the point the shot passes through: a projectile
        // that falls d over its flight must be launched d above the intercept to arrive at it.
        assertTrue(dropped.aimPoint().y > flat.aimPoint().y,
                "a lobbed shot must be aimed above the flat-trajectory solution to compensate for drop");
        double flightTime = flat.interceptTime();
        assertEquals(0.5 * 9.8 * flightTime * flightTime,
                dropped.aimPoint().y - flat.aimPoint().y, 1.0e-6);
        // Same horizontal solution, only Y changes.
        assertEquals(flat.aimPoint().x, dropped.aimPoint().x, 1.0e-6);
    }

    @Test
    void noGravityLeavesTheAimPointUnchanged() {
        LeadCalculator.LeadResult flat = LeadCalculator.linear(
                Vec3.ZERO, new Vec3(15, 3, 0), Vec3.ZERO, 25.0);
        LeadCalculator.LeadResult dropped = LeadCalculator.withGravityDrop(
                Vec3.ZERO, new Vec3(15, 3, 0), Vec3.ZERO, 25.0, 0.0);
        assertEquals(flat.aimPoint().y, dropped.aimPoint().y, 1.0e-9);
    }
}
