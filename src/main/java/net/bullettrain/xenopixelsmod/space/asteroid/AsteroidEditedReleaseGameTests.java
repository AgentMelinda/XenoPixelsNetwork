package net.bullettrain.xenopixelsmod.space.asteroid;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/** Manual dedicated-server proof that production asteroid release leaves edited voxels in Sable storage. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AsteroidEditedReleaseGameTests {
    private AsteroidEditedReleaseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120, required = false, manualOnly = true,
            batch = "asteroid_edited_release")
    public static void productionReleaseStoresEditedAsteroidByStableIdentity(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) {
            helper.fail("This manual storage test requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Run this storage test on an isolated dedicated server without connected players");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        AsteroidField.ObjectId fieldId = new AsteroidField.ObjectId(904, -2, 72, 0);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(24_000.5, 1_200.5, 24_000.5),
                AsteroidField.MIN_RADIUS, (x, y, z) -> y == 0 && z == 0 && (x == 0 || x == 1)
                        ? Blocks.STONE.defaultBlockState() : null);
        helper.assertTrue(built != null && built.body() != null, "the two-voxel asteroid fixture builds");
        if (built == null || built.body() == null) return;
        ServerSubLevel body = built.body();
        UUID bodyId = body.getUniqueId();
        GlobalSavedSubLevelPointer pointer = null;
        try {
            CompoundTag identity = new CompoundTag();
            identity.putLongArray("id", new long[]{fieldId.cellX(), fieldId.cellY(), fieldId.cellZ(), fieldId.slot()});
            identity.putDouble("mass", body.getMassTracker().getMass());
            CompoundTag userData = new CompoundTag();
            userData.put(AsteroidPhysicalizer.TAG, identity);
            body.setUserDataTag(userData);
            container.addForceLoadTicket(body, AsteroidPhysicalizer.TICKET, fieldId.toString());

            BlockPos editedPosition = hostBlock(body, 0, 0, 0);
            helper.assertFalse(level.getBlockState(editedPosition).isAir(), "the edited host voxel exists before mining");
            helper.assertFalse(AsteroidPhysicalizer.markTouched(level, editedPosition),
                    "the production edit gate accepts this edit and reserves the deterministic identity");
            helper.assertTrue(AsteroidSavedData.get(level).isDetached(fieldId)
                            && AsteroidSavedData.get(level).isDetachedBody(bodyId),
                    "the persistence ledger reserves both field identity and Sable UUID before the edit");
            helper.assertTrue(level.setBlock(editedPosition, Blocks.AIR.defaultBlockState(), 3),
                    "the mining edit removes the selected voxel");

            helper.assertTrue(AsteroidPhysicalizer.releaseForGameTest(level, fieldId, body),
                    "the production release path detaches the edited body");
            helper.assertTrue(container.getSubLevel(bodyId) == null,
                    "release unloads the live physics body after its edited plot is serialized");
            helper.assertTrue(body.getUserDataTag() == null || !body.getUserDataTag().contains(AsteroidPhysicalizer.TAG),
                    "the released body no longer carries the active-field management tag");
            helper.assertTrue(level.getBlockState(editedPosition).isAir(),
                    "the mined voxel remains absent after production release");

            var detached = AsteroidSavedData.get(level).storedBody(fieldId);
            helper.assertTrue(detached != null && detached.bodyId().equals(bodyId),
                    "production release records the exact body UUID and Sable pointer for later restoration");
            pointer = detached == null ? null : detached.pointer();
            helper.assertTrue(pointer != null, "production release records a persistent Sable storage pointer");
            var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(stored != null && stored.uuid().equals(bodyId),
                    "exact Sable storage read-back returns the released body's UUID");
            helper.assertTrue(stored.fullTag().contains("plot", net.minecraft.nbt.Tag.TAG_COMPOUND),
                    "the exact stored body contains its serialized plot");
            CompoundTag currentPlot = SubLevelSerializer.toData(body, List.of()).fullTag().getCompound("plot");
            helper.assertTrue(stored.fullTag().getCompound("plot").equals(currentPlot),
                    "the stored voxel plot exactly matches the edited live Sable body");
            helper.assertTrue(!stored.fullTag().getCompound("user_data").contains(AsteroidPhysicalizer.TAG),
                    "the serialized detached body is no longer claimed as a regenerated field incarnation");

            helper.assertTrue(AsteroidSavedData.get(level).isDetached(fieldId)
                            && AsteroidSavedData.get(level).isDetachedBody(bodyId),
                    "the stable field tombstone and UUID mapping remain after Sable storage read-back");
            XenoPixelsMod.LOGGER.info("ASTEROID_EDITED_RELEASE_TEST PASS fieldId={} bodyUuid={} pointer={} storageReadBack=true",
                    fieldId, bodyId, pointer);
            helper.succeed();
        } finally {
            try {
                if (container.getSubLevel(bodyId) instanceof ServerSubLevel live && !live.isRemoved()) {
                    container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    holding.processChanges();
                }
                if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
                holding.processChanges();
            } finally {
                container.removeForceLoadTicket(body, AsteroidPhysicalizer.TICKET, fieldId.toString());
                AsteroidSavedData.get(level).clearForGameTest(fieldId, bodyId);
            }
        }
    }

    private static BlockPos hostBlock(ServerSubLevel body, int x, int y, int z) {
        var plot = body.getPlot();
        BlockPos center = plot.getCenterBlock();
        ChunkPos localCenterChunk = plot.toLocal(new ChunkPos(center));
        int localX = localCenterChunk.x * 16 + (center.getX() & 15) + x;
        int localY = center.getY() + y;
        int localZ = localCenterChunk.z * 16 + (center.getZ() & 15) + z;
        ChunkPos globalChunk = plot.toGlobal(new ChunkPos(Math.floorDiv(localX, 16), Math.floorDiv(localZ, 16)));
        return new BlockPos(globalChunk.getMinBlockX() + Math.floorMod(localX, 16), localY,
                globalChunk.getMinBlockZ() + Math.floorMod(localZ, 16));
    }
}
