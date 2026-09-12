package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotLeaseTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final UUID RENTER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void leaseCarriesTermsAndIdentifiesItsPlot() {
        PlotLease.Lease lease = new PlotLease.Lease(
                OVERWORLD, 4, 8, RENTER, OWNER, 12.5D, 24000, 24000L);
        assertEquals(OVERWORLD, lease.dimension());
        assertEquals(4, lease.minX());
        assertEquals(8, lease.minZ());
        assertEquals(RENTER, lease.renter());
        assertEquals(OWNER, lease.owner());
        assertEquals(12.5D, lease.pricePerPeriod());
        assertEquals(24000, lease.periodTicks());
        assertEquals(24000L, lease.nextDueTick());
    }

    @Test
    void aPeriodFallsDueAtItsTickAndNotBefore() {
        assertFalse(PlotLease.isDue(100L, 99L));
        assertTrue(PlotLease.isDue(100L, 100L));
        assertTrue(PlotLease.isDue(100L, 101L));
    }

    @Test
    void theNextDueTickIsAlwaysInTheFuture() {
        assertEquals(1L, PlotLease.nextDue(0L, 0));
        assertEquals(1L, PlotLease.nextDue(0L, -5));
        assertEquals(24000L, PlotLease.nextDue(0L, 24000));
        assertEquals(200L, PlotLease.nextDue(100L, 100));
    }

    @Test
    void anOfflineRenterLapsesForBeingOfflineRatherThanUnfunded() {
        // The two are different facts and only one is the renter's doing, so the order matters.
        assertEquals(PlotLease.Outcome.LAPSED_OFFLINE,
                PlotLease.decide(false, true, false));
        assertEquals(PlotLease.Outcome.LAPSED_OFFLINE,
                PlotLease.decide(false, true, true));
    }

    @Test
    void aMissingEconomyIsReportedAsSuchRatherThanAsPoverty() {
        assertEquals(PlotLease.Outcome.NO_ECONOMY, PlotLease.decide(true, false, false));
    }

    @Test
    void onlyAnOnlineFundedRenterWithAnEconomyRenews() {
        assertEquals(PlotLease.Outcome.RENEWED, PlotLease.decide(true, true, true));
        assertEquals(PlotLease.Outcome.LAPSED_UNFUNDED, PlotLease.decide(true, true, false));
    }

    @Test
    void everyOutcomeTheTickerProducesIsRepresentable() {
        assertNotNull(PlotLease.Outcome.valueOf("RENEWED"));
        assertNotNull(PlotLease.Outcome.valueOf("LAPSED_OFFLINE"));
        assertNotNull(PlotLease.Outcome.valueOf("LAPSED_UNFUNDED"));
        assertNotNull(PlotLease.Outcome.valueOf("NO_ECONOMY"));
    }
}