package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidSavedDataTest {
    @Test void detachedBodyIdentitySurvivesSavedDataRoundTrip() {
        var id = new AsteroidField.ObjectId(-12, 7, 33, 1);
        var body = UUID.fromString("2ae9009f-1904-4f3e-9022-295e55d606a1");
        var source = new AsteroidSavedData();

        assertTrue(source.detach(id, body));
        var restored = AsteroidSavedData.load(source.save(new CompoundTag(), null), null);

        assertTrue(restored.isDetached(id));
        assertTrue(restored.isDetachedBody(body));
        assertEquals(1, restored.size());
    }

    @Test void legacyDetachedIdWithoutBodyIdentityStillLoads() {
        var id = new AsteroidField.ObjectId(12, -7, 3, 1);
        var tag = new CompoundTag();
        var entries = new ListTag();
        entries.add(new LongArrayTag(new long[]{id.cellX(), id.cellY(), id.cellZ(), id.slot()}));
        tag.put("Detached", entries);

        var restored = AsteroidSavedData.load(tag, null);

        assertTrue(restored.isDetached(id));
        assertEquals(0, restored.detachedBodyCount());
    }

    @Test void aFullDetachedLedgerStillAcceptsAnExistingIdentityWithoutDeletingPlayerData() {
        var data = new AsteroidSavedData();
        for (int i = 0; i < AsteroidSavedData.MAX_DETACHED; i++)
            assertTrue(data.detach(new AsteroidField.ObjectId(i, 0, 0, 0)));

        assertEquals(AsteroidSavedData.MAX_DETACHED, data.size());
        var body = UUID.fromString("22b00d22-26b1-4310-8e90-77ae876ca9d0");
        assertTrue(data.detach(new AsteroidField.ObjectId(0, 0, 0, 0), body),
                "repeated detach must be idempotent even at capacity");
        assertTrue(data.isDetachedBody(body), "existing legacy ID may acquire its body identity");
        assertFalse(data.detach(new AsteroidField.ObjectId(AsteroidSavedData.MAX_DETACHED, 0, 0, 0)),
                "a new identity must not overflow the persisted bound");
        assertEquals(AsteroidSavedData.MAX_DETACHED, data.size());
    }

    @Test void oneStoredBodyCannotBeAssignedToTwoFieldIdentities() {
        var data = new AsteroidSavedData();
        var body = UUID.fromString("4e321011-cb1b-4c54-a21b-c8857f08c8fe");
        assertTrue(data.detach(new AsteroidField.ObjectId(1, 2, 3, 0), body));
        assertFalse(data.detach(new AsteroidField.ObjectId(1, 2, 4, 0), body));
        assertEquals(1, data.size());
    }

    @Test void dormantMappedBodiesDoNotConsumeTheLiveCapButLegacyIdsRemainConservative() {
        var data = new AsteroidSavedData();
        var loadedId = new AsteroidField.ObjectId(1, 0, 0, 0);
        var unloadedId = new AsteroidField.ObjectId(2, 0, 0, 0);
        var legacyId = new AsteroidField.ObjectId(3, 0, 0, 0);
        var loaded = UUID.fromString("22b00d22-26b1-4310-8e90-77ae876ca9d0");
        var unloaded = UUID.fromString("ec5ee7dc-205c-4faa-8c3e-9cd57926974c");
        assertTrue(data.detach(loadedId, loaded));
        assertTrue(data.detach(unloadedId, unloaded));
        assertTrue(data.detach(legacyId));

        assertEquals(1, data.unmappedDetachedCount(),
                "only the UUID-less legacy identity needs an unlocated live-cap reservation");
        assertTrue(data.isDetached(unloadedId), "dormant bodies stay tombstoned so they are not regenerated");
        assertTrue(data.isDetachedBody(unloaded), "the saved UUID remains available for Sable reload association");
    }
}
