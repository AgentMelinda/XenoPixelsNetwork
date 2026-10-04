package net.bullettrain.xenopixelsmod.compat.npc.brain.v4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatBrainV4Test {
    @Test
    void prioritizesRecoveryAtLowResources() {
        NpcCombatContextV4 context = new NpcCombatContextV4();
        context.distance = 3.0;
        context.healthFraction = 0.2;
        context.energyFraction = 0.1;
        context.canRecover = true;
        assertEquals(NpcCombatIntentV4.RECOVER, NpcCombatBrainV4.decide(context));
    }

    @Test
    void exploitsHelplessTargetsBeforeRangedOptions() {
        NpcCombatContextV4 context = new NpcCombatContextV4();
        context.distance = 3.0;
        context.targetHelpless = true;
        context.canCombo = true;
        context.canHitscan = true;
        assertEquals(NpcCombatIntentV4.COMBO, NpcCombatBrainV4.decide(context));
    }

    @Test
    void choosesRangedPressureAndApproachByBand() {
        NpcCombatContextV4 context = new NpcCombatContextV4();
        context.distance = 16.0;
        context.canHitscan = true;
        assertEquals(NpcCombatIntentV4.HITSCAN, NpcCombatBrainV4.decide(context));
        context.distance = 40.0;
        assertEquals(NpcCombatIntentV4.APPROACH, NpcCombatBrainV4.decide(context));
    }

    @Test
    void budgetResetsAndFailsClosed() {
        NpcBrainBudget budget = new NpcBrainBudget(2);
        assertTrue(budget.tryAcquire(10));
        assertTrue(budget.tryAcquire(10));
        assertFalse(budget.tryAcquire(10));
        assertTrue(budget.tryAcquire(11));
    }
}
