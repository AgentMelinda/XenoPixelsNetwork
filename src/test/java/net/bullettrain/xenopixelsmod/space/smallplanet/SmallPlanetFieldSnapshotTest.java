package net.bullettrain.xenopixelsmod.space.smallplanet;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmallPlanetFieldSnapshotTest {
    @Test void snapshotRoundTripsDerivedFieldInputsAndExclusions() {
        var packet = new SmallPlanetFieldSnapshot(7, 0x1234ABCDL, true,
                List.of(new SmallPlanetField.Sphere(12.5, -30, 90, 400)),
                List.of(new AsteroidField.Belt("xenopixelsmod:main_belt", 1, 128, 2,
                        2200, 3800, 96, false)), -0.5, 1.25, 42.0);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        SmallPlanetFieldSnapshot.encode(packet, buf);
        assertEquals(packet, SmallPlanetFieldSnapshot.decode(buf));
    }

    @Test void snapshotRejectsUnboundedCollectionsAndMalformedWireCounts() {
        var spheres = java.util.Collections.nCopies(SmallPlanetFieldSnapshot.MAX_CHARTED_BODIES + 1,
                new SmallPlanetField.Sphere(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SmallPlanetFieldSnapshot(0, 1, true, spheres, List.of()));

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(0); buf.writeLong(1); buf.writeBoolean(true);
        buf.writeDouble(0); buf.writeDouble(0); buf.writeDouble(0);
        buf.writeVarInt(SmallPlanetFieldSnapshot.MAX_CHARTED_BODIES + 1);
        assertThrows(IllegalArgumentException.class, () -> SmallPlanetFieldSnapshot.decode(buf));
    }

    @Test void disabledSnapshotCarriesNoWorldSeedOrExclusions() {
        var packet = SmallPlanetFieldSnapshot.disabled(2);
        assertFalse(packet.enabled());
        assertEquals(0L, packet.fieldSeed());
        assertEquals(0.0, packet.homeStarX());
        assertEquals(0.0, packet.homeStarY());
        assertEquals(0.0, packet.homeStarZ());
        assertTrue(packet.chartedBodies().isEmpty());
        assertTrue(packet.asteroidBelts().isEmpty());
    }

    @Test void chartSphereFilterIncludesEverySphereThatCouldRejectAQueriedCandidate() {
        double reach = SmallPlanetField.MAX_NEARBY_RANGE + 2.0 * SmallPlanetField.MAX_RADIUS
                + SmallPlanetField.ORBITAL_CLEARANCE + 900.0;
        assertTrue(SmallPlanetFieldSnapshot.chartSphereMayAffectQuery(0, 0, 0,
                new SmallPlanetField.Sphere(reach - 0.01, 0, 0, 900)));
        assertFalse(SmallPlanetFieldSnapshot.chartSphereMayAffectQuery(0, 0, 0,
                new SmallPlanetField.Sphere(reach + 0.01, 0, 0, 900)));
    }
}
