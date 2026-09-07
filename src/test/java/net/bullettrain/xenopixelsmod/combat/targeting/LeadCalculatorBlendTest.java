package net.bullettrain.xenopixelsmod.combat.targeting;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The intercept solve and accuracy blend NPC ki aim relies on
 * ({@code NpcKiAttackDispatcher.leadPoint}). That method itself needs a live level for the
 * velocity estimate, so the arithmetic it depends on is pinned here instead.
 */
class LeadCalculatorBlendTest {
    @Test
    void leadsAheadOfACrossingTarget() {
        Vec3 shooter = new Vec3(0.0, 0.0, 0.0);
        Vec3 target = new Vec3(20.0, 0.0, 0.0);
        Vec3 velocity = new Vec3(0.0, 0.0, 10.0);

        LeadCalculator.LeadResult lead = LeadCalculator.linear(shooter, target, velocity, 40.0);

        assertTrue(lead.solvable());
        // The aim point must sit ahead of the target along its own travel direction.
        assertTrue(lead.aimPoint().z > target.z);
        assertTrue(lead.interceptTime() > 0.0);
    }

    @Test
    void aimsStraightAtAStationaryTarget() {
        Vec3 shooter = new Vec3(0.0, 0.0, 0.0);
        Vec3 target = new Vec3(0.0, 0.0, 15.0);

        LeadCalculator.LeadResult lead = LeadCalculator.linear(shooter, target, Vec3.ZERO, 40.0);

        assertTrue(lead.solvable());
        assertEquals(target.z, lead.aimPoint().z, 1.0e-6);
    }

    @Test
    void reportsUnsolvableWhenTheTargetOutrunsTheShot() {
        // Fleeing straight away, faster than the projectile: there is no intercept, and the
        // caller must fall back to aiming at the target rather than trusting a bogus point.
        LeadCalculator.LeadResult lead = LeadCalculator.linear(
                new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 10.0),
                new Vec3(0.0, 0.0, 30.0), 5.0);

        assertFalse(lead.solvable());
    }

    @Test
    void accuracyBlendsBetweenNoLeadAndFullLead() {
        Vec3 eyes = new Vec3(20.0, 0.0, 0.0);
        Vec3 fullLead = new Vec3(20.0, 0.0, 6.0);

        // The blend NpcKiAttackDispatcher applies: eyes + (lead - eyes) * accuracy.
        assertEquals(eyes.z, blend(eyes, fullLead, 0.0f).z, 1.0e-6);
        assertEquals(fullLead.z, blend(eyes, fullLead, 1.0f).z, 1.0e-6);
        assertEquals(3.0, blend(eyes, fullLead, 0.5f).z, 1.0e-6);
    }

    private static Vec3 blend(Vec3 eyes, Vec3 lead, float accuracy) {
        return eyes.add(lead.subtract(eyes).scale(accuracy));
    }
}
