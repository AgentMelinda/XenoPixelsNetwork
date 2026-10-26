package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallIdentityTest {
    @Test
    void allSevenSlotIdentitiesAreDeterministicAndDistinct() {
        List<DragonBallIdentity> identities = new ArrayList<>();
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            var identity = DragonBallIdentity.forSlot(slot);
            identities.add(identity);
            assertEquals(identity, DragonBallIdentity.forSlot(slot));
            assertTrue(identity.planetKey().startsWith("xenopixelsmod:sdb/planet/"));
            assertTrue(identity.surfaceCellKey().startsWith("xenopixelsmod:planet_surfaces/sdb/"));
        }
        assertTrue(DragonBallIdentity.hasUniquePlanetIds(identities));
        assertFalse(DragonBallIdentity.hasUniquePlanetIds(List.of(identities.get(0), identities.get(0))));
    }

    @Test
    void productionPlanetKeysResolveToTheirStarCountWithoutAcceptingLookalikes() {
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++)
            assertEquals(slot, DragonBallIdentity.slotFromPlanetKey(DragonBallIdentity.forSlot(slot).planetKey()));
        assertEquals(0, DragonBallIdentity.slotFromPlanetKey(null));
        assertEquals(0, DragonBallIdentity.slotFromPlanetKey("xenopixelsmod:sdb/planet/slot-8"));
        assertEquals(0, DragonBallIdentity.slotFromPlanetKey("xenopixelsmod:sdb/planet/slot-1-extra"));
        assertEquals(0, DragonBallIdentity.slotFromPlanetKey("other:sdb/planet/slot-1"));
    }

    @Test
    void legacySavedDataMigratesIdentityMetadataWithoutChangingBallState() {
        CompoundTag legacy = new CompoundTag();
        legacy.putLong("DormantUntilDay", 0);
        legacy.putLong("Cycle", 12);
        ListTag balls = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putString("Status", "HELD");
        first.putString("Dimension", "minecraft:overworld");
        first.putInt("X", 12); first.putInt("Y", 80); first.putInt("Z", -4);
        balls.add(first);
        for (int slot = 2; slot <= CosmicDragonBalls.COUNT; slot++) balls.add(validBall(slot));
        legacy.put("Balls", balls);

        DragonBallSavedData migrated = DragonBallSavedData.load(legacy, null);
        CompoundTag saved = migrated.save(new CompoundTag(), null);
        CompoundTag savedFirst = saved.getList("Balls", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(5, saved.getInt("DataVersion"));
        assertEquals(12, saved.getLong("Cycle"));
        assertEquals("HELD", savedFirst.getString("Status"));
        assertEquals(12, savedFirst.getInt("X"));
        assertEquals(DragonBallIdentity.forSlot(1).planetId(), savedFirst.getUUID("PlanetId"));
        assertEquals(DragonBallIdentity.forSlot(1).surfaceCellKey(), savedFirst.getString("SurfaceCellKey"));
        assertEquals("", savedFirst.getString("HostChartId"));
        assertFalse(savedFirst.hasUUID("PlanetBodyUUID"));
    }

    @Test
    void unknownFutureSchemaIsPreservedWithoutDowngrading() {
        CompoundTag future = new CompoundTag();
        future.putInt("DataVersion", 999);
        ListTag balls = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putString("PlanetKey", "future:unrecognized");
        first.putString("SurfaceCellKey", "future:cell");
        balls.add(first);
        future.put("Balls", balls);

        DragonBallSavedData loaded = DragonBallSavedData.load(future, null);
        assertFalse(loaded.isGameplayStateValid());
        assertEquals(future, loaded.save(new CompoundTag(), null));
    }

    @Test
    void planetBodyUuidBindingIsIdempotentUniqueAndPersistent() {
        DragonBallSavedData saved = new DragonBallSavedData();
        UUID first = UUID.fromString("10000000-0000-0000-0000-000000000001");

        assertTrue(saved.bindPlanetBody(1, first));
        assertFalse(saved.bindPlanetBody(1, first));
        assertEquals(first, saved.planetBodyUuid(1));
        assertTrue(saved.hasUniquePlanetBodyUuids());
        assertThrows(IllegalStateException.class, () -> saved.bindPlanetBody(2, first));
        assertThrows(IllegalArgumentException.class, () -> saved.bindPlanetBody(2, null));
        assertNull(saved.planetBodyUuid(2));

        CompoundTag encoded = saved.save(new CompoundTag(), null);
        DragonBallSavedData restored = DragonBallSavedData.load(encoded, null);
        assertEquals(first, restored.planetBodyUuid(1));
        assertNull(restored.planetBodyUuid(2));
    }

    @Test
    void planetBodyClearRequiresTheExpectedIncarnation() {
        DragonBallSavedData saved = new DragonBallSavedData();
        UUID original = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID replacement = UUID.fromString("10000000-0000-0000-0000-000000000002");
        saved.bindPlanetBody(1, original);
        saved.bindPlanetBody(1, replacement);

        assertFalse(saved.clearPlanetBody(1, original));
        assertEquals(replacement, saved.planetBodyUuid(1));
        assertTrue(saved.clearPlanetBody(1, replacement));
        assertNull(saved.planetBodyUuid(1));
    }

    @Test
    void duplicateBodyUuidsInExistingSaveAreDetectedAndPreservedForManualReconciliation() {
        UUID duplicate = UUID.fromString("10000000-0000-0000-0000-000000000001");
        CompoundTag root = new CompoundTag();
        root.putLong("Cycle", 0);
        root.putLong("DormantUntilDay", 0);
        ListTag balls = new ListTag();
        for (int i = 0; i < CosmicDragonBalls.COUNT; i++) {
            CompoundTag ball = validBall(i + 1);
            if (i < 2)
            ball.putUUID("PlanetBodyUUID", duplicate);
            balls.add(ball);
        }
        root.put("Balls", balls);

        DragonBallSavedData loaded = DragonBallSavedData.load(root, null);

        assertFalse(loaded.hasUniquePlanetBodyUuids());
        assertEquals(duplicate, loaded.planetBodyUuid(1));
        assertEquals(duplicate, loaded.planetBodyUuid(2));
        assertThrows(IllegalStateException.class, () -> loaded.bindPlanetBody(3, duplicate));
    }

    private static CompoundTag validBall(int slot) {
        CompoundTag ball = new CompoundTag();
        ball.putString("Status", "SCATTERED");
        ball.putString("Dimension", "minecraft:overworld");
        ball.putInt("X", slot * 100); ball.putInt("Y", 80); ball.putInt("Z", -slot * 100);
        return ball;
    }
}
