package net.bullettrain.xenopixelsmod.space;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicalPlanetBindingsTest {
    @Test void roundTripsEveryBinding() {
        var packet = new PhysicalPlanetBindings(List.of(
                new PhysicalPlanetBindings.Entry("xenopixelsmod:earth", UUID.randomUUID()),
                new PhysicalPlanetBindings.Entry("xenospace_spike:moon_surface", UUID.randomUUID())), true);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        PhysicalPlanetBindings.encode(packet, buf);
        assertEquals(packet, PhysicalPlanetBindings.decode(buf));
    }

    @Test void emptyListClearsBindings() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        PhysicalPlanetBindings.encode(new PhysicalPlanetBindings(List.of()), buf);
        assertEquals(List.of(), PhysicalPlanetBindings.decode(buf).entries());
    }

    @Test void oversizedCountIsRejected() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        buf.writeVarInt(PhysicalPlanetBindings.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> PhysicalPlanetBindings.decode(buf));
    }

    @Test void oversizedBindingSnapshotsAreRejectedInsteadOfSilentlyTruncated() {
        var entries = new java.util.ArrayList<PhysicalPlanetBindings.Entry>();
        for (int i = 0; i <= PhysicalPlanetBindings.MAX_ENTRIES; i++)
            entries.add(new PhysicalPlanetBindings.Entry("xenopixelsmod:body/" + i, new UUID(i, i)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalPlanetBindings(entries, false));
    }

    @Test void roundTripsSmallPlanetBindingsBeyondTheOldChartOnlyCap() {
        var entries = new java.util.ArrayList<PhysicalPlanetBindings.Entry>();
        for (int i = 0; i < 40; i++) entries.add(new PhysicalPlanetBindings.Entry(
                "xenopixelsmod:smallplanet/" + i + "_0_0", new UUID(i, i + 1L), 10 + i % 15));
        var packet = new PhysicalPlanetBindings(entries, true);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PhysicalPlanetBindings.encode(packet, buf);
            assertEquals(packet, PhysicalPlanetBindings.decode(buf));
        } finally { buf.release(); }
    }

    @Test void rejectsGravityRadiusOutsideWalkableBodyCap() {
        assertThrows(IllegalArgumentException.class, () ->
                new PhysicalPlanetBindings.Entry("xenopixelsmod:smallplanet/0_0_0", UUID.randomUUID(), 25));
    }
}
