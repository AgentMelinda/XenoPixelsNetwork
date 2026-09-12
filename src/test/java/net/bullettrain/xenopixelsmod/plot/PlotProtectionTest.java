package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision-table tests for {@link PlotProtection#allowed}.
 *
 * <p>The event plumbing is deliberately not exercised: it needs a live server. The permission
 * decision itself is a pure predicate, so it can be pinned here — including the two cases that are
 * easiest to get wrong, an absent plot and an absent actor, both of which must allow.</p>
 */
class PlotProtectionTest {

    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static PlotArea plot(int flags) {
        return new PlotArea(DIMENSION, 0, 0, 15, 15, OWNER, flags);
    }

    @Test
    void absentPlotAllows() {
        assertTrue(PlotProtection.allowed(null, OTHER, PlotFlags.ALLOW_BUILD));
    }

    @Test
    void absentActorAllows() {
        assertTrue(PlotProtection.allowed(plot(PlotFlags.DEFAULT), null, PlotFlags.ALLOW_BUILD));
    }

    @Test
    void ownerAlwaysAllowedEvenWithFlagOff() {
        assertTrue(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OWNER, PlotFlags.ALLOW_BUILD));
        assertTrue(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OWNER, PlotFlags.ALLOW_ENTRY));
    }

    @Test
    void nonOwnerDeniedWhenFlagOff() {
        assertFalse(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OTHER, PlotFlags.ALLOW_BUILD));
        assertFalse(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OTHER, PlotFlags.ALLOW_CONTAINERS));
        assertFalse(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OTHER, PlotFlags.ALLOW_INTERACT));
        assertFalse(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OTHER, PlotFlags.ALLOW_PVP));
        assertFalse(PlotProtection.allowed(plot(PlotFlags.DEFAULT), OTHER, PlotFlags.ALLOW_ENTRY));
    }

    @Test
    void nonOwnerAllowedWhenItsOwnFlagIsOn() {
        assertTrue(PlotProtection.allowed(plot(PlotFlags.ALLOW_BUILD), OTHER, PlotFlags.ALLOW_BUILD));
        assertTrue(PlotProtection.allowed(plot(PlotFlags.ALLOW_PVP), OTHER, PlotFlags.ALLOW_PVP));
        assertTrue(PlotProtection.allowed(plot(PlotFlags.ALLOW_ENTRY), OTHER, PlotFlags.ALLOW_ENTRY));
    }

    @Test
    void oneFlagDoesNotImplyAnother() {
        PlotArea buildOnly = plot(PlotFlags.ALLOW_BUILD);
        assertTrue(PlotProtection.allowed(buildOnly, OTHER, PlotFlags.ALLOW_BUILD));
        assertFalse(PlotProtection.allowed(buildOnly, OTHER, PlotFlags.ALLOW_CONTAINERS));
        assertFalse(PlotProtection.allowed(buildOnly, OTHER, PlotFlags.ALLOW_INTERACT));
        assertFalse(PlotProtection.allowed(buildOnly, OTHER, PlotFlags.ALLOW_PVP));
        assertFalse(PlotProtection.allowed(buildOnly, OTHER, PlotFlags.ALLOW_ENTRY));
    }

    @Test
    void allFlagsOnAllowsEverything() {
        int all = PlotFlags.ALLOW_BUILD | PlotFlags.ALLOW_CONTAINERS | PlotFlags.ALLOW_INTERACT
                | PlotFlags.ALLOW_PVP | PlotFlags.ALLOW_ENTRY;
        PlotArea open = plot(all);
        assertTrue(PlotProtection.allowed(open, OTHER, PlotFlags.ALLOW_BUILD));
        assertTrue(PlotProtection.allowed(open, OTHER, PlotFlags.ALLOW_CONTAINERS));
        assertTrue(PlotProtection.allowed(open, OTHER, PlotFlags.ALLOW_INTERACT));
        assertTrue(PlotProtection.allowed(open, OTHER, PlotFlags.ALLOW_PVP));
        assertTrue(PlotProtection.allowed(open, OTHER, PlotFlags.ALLOW_ENTRY));
    }
}