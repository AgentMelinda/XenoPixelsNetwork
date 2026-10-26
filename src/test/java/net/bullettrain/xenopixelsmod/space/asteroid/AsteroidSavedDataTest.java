package net.bullettrain.xenopixelsmod.space.asteroid;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;

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

    @Test void detachedStoragePointerAndReturnPositionSurviveSavedDataRoundTrip() {
        var id = new AsteroidField.ObjectId(-44, 12, 103, 0);
        var body = UUID.fromString("cd14b003-24e8-4c19-b151-5d399eedf37f");
        var pointer = new GlobalSavedSubLevelPointer(new ChunkPos(-19, 27), (short) 3, (short) 9);
        var source = new AsteroidSavedData();
        assertTrue(source.detach(id, body));
        assertTrue(source.storeBody(id, body, pointer, -7_101.25, 289.5, 39_661.0,
                0x7623a98fL, 11, true));

        var restored = AsteroidSavedData.load(source.save(new CompoundTag(), null), null);
        var stored = restored.storedBody(id);

        assertNotNull(stored);
        assertEquals(body, stored.bodyId());
        assertEquals(pointer, stored.pointer());
        assertEquals(-7_101.25, stored.x());
        assertEquals(289.5, stored.y());
        assertEquals(39_661.0, stored.z());
        assertEquals(0x7623a98fL, stored.seed());
        assertEquals(11, stored.radius());
        assertTrue(stored.icy());
    }

    @Test void detachedStoragePointerCannotBeBoundToAnotherBodyIdentity() {
        var data = new AsteroidSavedData();
        var id = new AsteroidField.ObjectId(1, -2, 3, 0);
        var body = UUID.fromString("2dd858fd-d251-4ae6-a39a-9b1c0702df07");
        var other = UUID.fromString("41909a84-a4fe-4b9a-a653-2c0cf52f69cc");
        var pointer = new GlobalSavedSubLevelPointer(new ChunkPos(2, 4), (short) 0, (short) 1);
        assertTrue(data.detach(id, body));

        assertFalse(data.storeBody(id, other, pointer, 1, 2, 3, 4, AsteroidField.MIN_RADIUS, false));
        assertNull(data.storedBody(id));
    }

    @Test void nearbyStoredRestoreQueryIsNearestFirstAndCapped() {
        var data = new AsteroidSavedData();
        for (int i = 0; i < 4; i++) {
            var id = new AsteroidField.ObjectId(i, 0, 0, 0);
            var body = UUID.nameUUIDFromBytes(("asteroid-restore-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(data.detach(id, body));
            assertTrue(data.storeBody(id, body,
                    new GlobalSavedSubLevelPointer(new ChunkPos(i, 0), (short) 0, (short) 0),
                    i * 30.0, 0, 0, i + 19, AsteroidField.MIN_RADIUS, false));
        }

        var nearby = data.nearbyStored(0, 0, 0, 200, 2);

        assertEquals(2, nearby.size());
        assertEquals(0.0, nearby.get(0).x());
        assertEquals(30.0, nearby.get(1).x());
        assertTrue(data.nearbyStored(100_000, 0, 0, 100_000, 2).isEmpty(),
                "a huge operator range must fail closed when its cell query exceeds the work bound");
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
