package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSaleTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void listingCarriesBoundsAndPrice() {
        PlotSale.Listing listing = new PlotSale.Listing(OVERWORLD, 4, 8, 20, 30, SELLER, 12.5D);
        assertEquals(4, listing.minX());
        assertEquals(8, listing.minZ());
        assertEquals(20, listing.maxX());
        assertEquals(30, listing.maxZ());
        assertEquals(12.5D, listing.price());
        assertEquals(SELLER, listing.seller());
    }

    @Test
    void listingConvertsBackToSellerOwnedPlot() {
        PlotSale.Listing listing = new PlotSale.Listing(OVERWORLD, 4, 8, 20, 30, SELLER, 12.5D);
        PlotArea plot = listing.asPlot();
        assertEquals(OVERWORLD, plot.dimension());
        assertEquals(4, plot.minX());
        assertEquals(8, plot.minZ());
        assertEquals(20, plot.maxX());
        assertEquals(30, plot.maxZ());
        assertEquals(SELLER, plot.owner());
        // A listing is not itself a claim, so it must not smuggle in permissions.
        assertEquals(PlotFlags.DEFAULT, plot.flags());
    }

    @Test
    void resultEnumCoversEveryFailureTheDesignNames() {
        // Buy fails closed, so each reason a purchase can be refused must be representable.
        assertNotNull(PlotSale.Result.valueOf("SUCCESS"));
        assertNotNull(PlotSale.Result.valueOf("NO_ECONOMY"));
        assertNotNull(PlotSale.Result.valueOf("INSUFFICIENT_FUNDS"));
        assertNotNull(PlotSale.Result.valueOf("TRANSFER_FAILED"));
        assertNotNull(PlotSale.Result.valueOf("NOT_FOR_SALE"));
        assertNotNull(PlotSale.Result.valueOf("NO_PLOT"));
        assertNotNull(PlotSale.Result.valueOf("ALREADY_OWNER"));
        assertNotNull(PlotSale.Result.valueOf("NO_PERMISSION"));
    }

    @Test
    void buyRejectsNullPlayerAndPlotWithoutTouchingState() {
        // Both guards run before any economy call, so neither can move money.
        assertSame(PlotSale.Result.NO_PLOT, PlotSale.buy(null, null));
        assertSame(PlotSale.Result.NO_PLOT,
                PlotSale.buy(null, new PlotArea(OVERWORLD, 0, 0, 1, 1, SELLER, PlotFlags.DEFAULT)));
    }

    @Test
    void listingPriceIsNotRoundedBeforeConversion() {
        PlotSale.Listing listing = new PlotSale.Listing(OVERWORLD, 0, 0, 1, 1, SELLER, 0.01D);
        assertTrue(listing.price() > 0.0D);
    }
}