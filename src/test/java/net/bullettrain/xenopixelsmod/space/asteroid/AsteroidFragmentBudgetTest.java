package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFragmentBudgetTest {
    @Test void predictedChildrenMatchesSableRetainLargestBranches() {
        assertEquals(0, AsteroidFragmentBudget.predictedChildren(1, 15, 15));
        assertEquals(2, AsteroidFragmentBudget.predictedChildren(3, 15, 15));
        assertEquals(3, AsteroidFragmentBudget.predictedChildren(3, 14, 15));
    }

    @Test void invalidOrInconsistentSplitCountsFailClosed() {
        assertEquals(-1, AsteroidFragmentBudget.predictedChildren(-1, 0, 0));
        assertEquals(-1, AsteroidFragmentBudget.predictedChildren(0, 1, 1));
        assertEquals(-1, AsteroidFragmentBudget.predictedChildren(1, 2, 1));
    }

    @Test void capsAllowExactBoundaryButRejectEitherOverflowAndIntegerOverflow() {
        assertTrue(AsteroidFragmentBudget.fits(6, 126, 2, 8, 128));
        assertFalse(AsteroidFragmentBudget.fits(7, 126, 2, 8, 128));
        assertFalse(AsteroidFragmentBudget.fits(6, 127, 2, 8, 128));
        assertFalse(AsteroidFragmentBudget.fits(Integer.MAX_VALUE, Integer.MAX_VALUE, 1,
                Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test void zeroCapBlocksNewFragmentsButAllowsNoOpSplit() {
        assertTrue(AsteroidFragmentBudget.fits(0, 0, 0, 0, 0));
        assertFalse(AsteroidFragmentBudget.fits(0, 0, 1, 0, 0));
    }
}
