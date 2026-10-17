package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceActivationBudgetReasonTest {
    @Test void separatesRateLimitDenialsFromFairnessArbitration() {
        var budget = new SpaceActivationBudget();

        assertTrue(budget.tryAcquire(SpaceActivationBudget.Owner.CHART_PLANET, 0));
        assertFalse(budget.tryAcquire(SpaceActivationBudget.Owner.ASTEROID, 9));
        assertEquals(1, budget.deniedRequests(SpaceActivationBudget.Owner.ASTEROID));
        assertEquals(1, budget.rateLimitDenials(SpaceActivationBudget.Owner.ASTEROID));
        assertEquals(0, budget.arbitrationDenials(SpaceActivationBudget.Owner.ASTEROID));

        assertTrue(budget.tryAcquire(SpaceActivationBudget.Owner.ASTEROID, 10));
        assertFalse(budget.tryAcquire(SpaceActivationBudget.Owner.CHART_PLANET, 20));
        assertEquals(1, budget.deniedRequests(SpaceActivationBudget.Owner.CHART_PLANET));
        assertEquals(0, budget.rateLimitDenials(SpaceActivationBudget.Owner.CHART_PLANET));
        assertEquals(1, budget.arbitrationDenials(SpaceActivationBudget.Owner.CHART_PLANET));

        assertTrue(budget.tryAcquire(SpaceActivationBudget.Owner.CHART_PLANET, 21));
        assertEquals(1, budget.deniedRequests(SpaceActivationBudget.Owner.CHART_PLANET));
        assertEquals(1, budget.arbitrationDenials(SpaceActivationBudget.Owner.CHART_PLANET));
    }
}
