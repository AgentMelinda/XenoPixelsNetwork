package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DragonBallPlanetPersistenceSchemaTest {
    @Test
    void savesStableSableBodyIdentityAndChartHostAlongsideLegacyLocation() {
        UUID bodyId = UUID.fromString("55e7f903-7a67-4e25-a38a-632f469086ce");
        CompoundTag source = new CompoundTag();
        source.putInt("DataVersion", 2);
        source.putLong("Cycle", 0);
        source.putLong("DormantUntilDay", 0);
        ListTag balls = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putString("Status", "SCATTERED");
        first.putString("Dimension", "xenopixelsmod:voidsea");
        first.putInt("X", 5200);
        first.putInt("Y", 128);
        first.putInt("Z", -80);
        first.putString("HostChartId", "xenopixelsmod:jupiter");
        first.putUUID("PlanetBodyUUID", bodyId);
        balls.add(first);
        for (int slot = 2; slot <= CosmicDragonBalls.COUNT; slot++) balls.add(validBall(slot));
        source.put("Balls", balls);

        CompoundTag saved = DragonBallSavedData.load(source, null).save(new CompoundTag(), null);
        CompoundTag savedFirst = saved.getList("Balls", Tag.TAG_COMPOUND).getCompound(0);

        assertEquals("xenopixelsmod:jupiter", savedFirst.getString("HostChartId"));
        assertEquals(bodyId, savedFirst.getUUID("PlanetBodyUUID"));
        assertEquals("xenopixelsmod:voidsea", savedFirst.getString("Dimension"));
        assertEquals(5200, savedFirst.getInt("X"));
        assertEquals(128, savedFirst.getInt("Y"));
        assertEquals(-80, savedFirst.getInt("Z"));
        assertEquals(false, savedFirst.getBoolean("HasPlanetOrbit"));
    }

    @Test
    void versionThreeOrbitDataMigratesWithoutInventingAnExplicitPeriod() {
        CompoundTag source = new CompoundTag();
        source.putInt("DataVersion", 3);
        ListTag balls = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putString("Status", "SCATTERED");
        first.putString("Dimension", "minecraft:overworld");
        first.putInt("X", 900); first.putInt("Y", 80); first.putInt("Z", -900);
        first.putString("HostChartId", "xenopixelsmod:maw_9");
        first.putBoolean("HasPlanetOrbit", true);
        first.putDouble("PlanetOrbitRadiusBlocks", 900.0);
        first.putDouble("PlanetOrbitPhaseRadians", 0.75);
        balls.add(first);
        for (int slot = 2; slot <= CosmicDragonBalls.COUNT; slot++) balls.add(validBall(slot));
        source.put("Balls", balls);

        DragonBallSavedData loaded = DragonBallSavedData.load(source, null);
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        CompoundTag migrated = saved.getList("Balls", Tag.TAG_COMPOUND).getCompound(0);

        assertEquals(5, saved.getInt("DataVersion"));
        assertEquals("xenopixelsmod:maw_9", migrated.getString("HostChartId"));
        assertEquals(900.0, migrated.getDouble("PlanetOrbitRadiusBlocks"));
        assertEquals(0.75, migrated.getDouble("PlanetOrbitPhaseRadians"));
        assertEquals(false, migrated.contains("PlanetOrbitPeriodSeconds"));
    }

    @Test
    void manualOrbitOverrideSurvivesSaveLoadWithoutReplacingPackAssignment() {
        DragonBallSavedData saved = new DragonBallSavedData();
        var ball = saved.ball(1);
        ball.hostChartId = "xenopixelsmod:jupiter";
        ball.hasPlanetOrbit = true;
        ball.planetOrbitRadiusBlocks = 320.0;
        ball.planetOrbitPhaseRadians = 0.4;
        saved.setManualOrbit(1, 510.0, -1.2, 1800.0);

        CompoundTag encoded = saved.save(new CompoundTag(), null);
        DragonBallSavedData restored = DragonBallSavedData.load(encoded, null);
        var override = restored.manualOrbit(1);

        assertEquals(5, encoded.getInt("DataVersion"));
        assertNotNull(override);
        assertEquals(510.0, override.radiusBlocks());
        assertEquals(-1.2, override.phaseRadians());
        assertEquals(1800.0, override.periodSeconds());
        assertEquals("xenopixelsmod:jupiter", restored.ball(1).hostChartId);
        assertEquals(320.0, restored.ball(1).planetOrbitRadiusBlocks);
    }

    private static CompoundTag validBall(int slot) {
        CompoundTag ball = new CompoundTag();
        ball.putString("Status", "SCATTERED");
        ball.putString("Dimension", "minecraft:overworld");
        ball.putInt("X", slot * 100); ball.putInt("Y", 80); ball.putInt("Z", -slot * 100);
        return ball;
    }
}
