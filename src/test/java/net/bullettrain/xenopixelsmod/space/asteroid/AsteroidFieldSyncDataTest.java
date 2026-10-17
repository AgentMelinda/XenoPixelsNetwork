package net.bullettrain.xenopixelsmod.space.asteroid;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidFieldSyncDataTest {
    private static BeltDefinition parse(String json) {
        return BeltDefinition.parse("xenopixelsmod:test_belt", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test void beltDefinitionParsesAndPlacesOnParent() {
        var definition = parse("{\"parent\":\"xenopixelsmod:sol\",\"inner_radius\":2200,\"outer_radius\":3800,\"half_thickness\":96}");
        assertEquals(BeltDefinition.DEFAULT_PLANE_Y, definition.planeY());
        assertFalse(definition.icy());
        var belt = definition.at(10, -20);
        assertEquals(10, belt.centerX());
        assertEquals(128, belt.centerY());
        assertEquals(-20, belt.centerZ());
        assertTrue(belt.contains(10 + 3000, 128, -20));
    }

    @Test void beltDefinitionRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"inner_radius\":1,\"outer_radius\":2,\"half_thickness\":1}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"parent\":\"xenopixelsmod:sol\",\"inner_radius\":5,\"outer_radius\":2,\"half_thickness\":1}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"parent\":\"xenopixelsmod:sol\",\"inner_radius\":1,\"outer_radius\":2,\"half_thickness\":10,\"plane_y\":40000000}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"schema_version\":2,\"parent\":\"xenopixelsmod:sol\",\"inner_radius\":1,\"outer_radius\":2,\"half_thickness\":1}"));
    }

    @Test void snapshotRoundTripsAndSeedIsNotTheWorldSeed() {
        long seed = AsteroidFieldSnapshot.fieldSeed(42L);
        assertNotEquals(42L, seed);
        assertEquals(seed, AsteroidFieldSnapshot.fieldSeed(42L));
        var snapshot = new AsteroidFieldSnapshot(3, seed, List.of(new AsteroidField.Belt("xenopixelsmod:main_belt", 0, 128, 0, 2200, 3800, 160, false)));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        AsteroidFieldSnapshot.encode(snapshot, buf);
        assertEquals(snapshot, AsteroidFieldSnapshot.decode(buf));
    }

    @Test void presenceRoundTripsNegativeCells() {
        var packet = new AsteroidPresence(List.of(new AsteroidPresence.Entry(new AsteroidField.ObjectId(-7, 0, 12, 1), UUID.randomUUID())));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        AsteroidPresence.encode(packet, buf);
        assertEquals(packet, AsteroidPresence.decode(buf));
    }
}
