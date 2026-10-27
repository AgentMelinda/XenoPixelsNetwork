package net.bullettrain.xenopixelsmod.space.asteroid;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/** Manual two-process test for persistence of a mined asteroid after production proximity release. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AsteroidEditedRestartGameTests {
    private static final String FIXTURE = "xenopixels_asteroid_restart_fixture";
    private static final AsteroidField.ObjectId FIELD_ID = new AsteroidField.ObjectId(905, -2, 72, 0);

    private AsteroidEditedRestartGameTests() {}

    /** Boot A: create and mine a fixture, then detach it through the production release path and save the world. */
    @GameTest(template = "empty", timeoutTicks = 120, required = false, manualOnly = true,
            batch = "asteroid_edited_restart_seed")
    public static void seedEditedAsteroidRestartFixture(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null || !server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Boot A requires an isolated normal dedicated server with the Voidsea level");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level,
                new Vector3d(24_500.5, 1_200.5, 24_500.5), AsteroidField.MIN_RADIUS,
                (x, y, z) -> y == 0 && z == 0 && (x == 0 || x == 1)
                        ? Blocks.STONE.defaultBlockState() : null);
        helper.assertTrue(built != null && built.body() != null, "Boot A edited-asteroid fixture builds");
        if (built == null || built.body() == null) return;

        ServerSubLevel body = built.body();
        UUID bodyId = body.getUniqueId();
        GlobalSavedSubLevelPointer pointer = null;
        boolean persistedForBootB = false;
        try {
            CompoundTag asteroid = new CompoundTag();
            asteroid.putLongArray("id", new long[]{FIELD_ID.cellX(), FIELD_ID.cellY(), FIELD_ID.cellZ(), FIELD_ID.slot()});
            asteroid.putDouble("mass", body.getMassTracker().getMass());
            CompoundTag userData = new CompoundTag();
            userData.put(AsteroidPhysicalizer.TAG, asteroid);
            userData.putBoolean(FIXTURE, true);
            userData.putUUID(FIXTURE + "_uuid", bodyId);
            body.setUserDataTag(userData);
            container.addForceLoadTicket(body, AsteroidPhysicalizer.TICKET, FIELD_ID.toString());

            BlockPos mined = hostBlock(body, 0, 0, 0);
            helper.assertFalse(level.getBlockState(mined).isAir(), "Boot A mined voxel exists before edit");
            helper.assertFalse(AsteroidPhysicalizer.markTouched(level, mined),
                    "production edit gate reserves stable field identity before mining");
            helper.assertTrue(level.setBlock(mined, Blocks.AIR.defaultBlockState(), 3),
                    "Boot A removes the selected asteroid voxel");
            helper.assertTrue(AsteroidPhysicalizer.releaseForGameTest(level, FIELD_ID, body),
                    "production release detaches the touched body");
            helper.assertTrue(container.getSubLevel(bodyId) == null,
                    "production release unloads the active physics body after serializing it");
            helper.assertTrue(AsteroidSavedData.get(level).isDetached(FIELD_ID)
                            && AsteroidSavedData.get(level).isDetachedBody(bodyId),
                    "production ledger reserves both the deterministic id and the Sable UUID");
            helper.assertTrue(!body.getUserDataTag().contains(AsteroidPhysicalizer.TAG),
                    "production release removes the active field tag");
            helper.assertTrue(body.getUserDataTag().getBoolean(FIXTURE),
                    "the independent restart fixture marker survives production release");
            helper.assertTrue(level.getBlockState(mined).isAir(), "mined voxel remains absent after release");

            AsteroidSavedData.StoredBody detached = AsteroidSavedData.get(level).storedBody(FIELD_ID);
            helper.assertTrue(detached != null && detached.bodyId().equals(bodyId),
                    "Boot A production release persists the body UUID and exact Sable pointer in SavedData");
            pointer = detached == null ? null : detached.pointer();
            helper.assertTrue(pointer != null, "Boot A production release has a Sable storage pointer");
            var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(stored != null && stored.uuid().equals(bodyId),
                    "Boot A pointer reads back the same detached body UUID");
            helper.assertTrue(stored.fullTag().getCompound("user_data").getBoolean(FIXTURE),
                    "Boot A serialized body retains the independent restart marker");
            helper.assertTrue(server.saveEverything(true, true, true), "Boot A forced world save completes");

            persistedForBootB = true;
            XenoPixelsMod.LOGGER.info("ASTEROID_EDITED_RESTART_SEED PASS fieldId={} bodyUuid={} pointer={} detached=true",
                    FIELD_ID, bodyId, pointer);
            helper.succeed();
        } finally {
            if (!persistedForBootB) {
                try {
                    container.removeForceLoadTicket(body, AsteroidPhysicalizer.TICKET, FIELD_ID.toString());
                    if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    holding.processChanges();
                    if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
                } finally {
                    AsteroidSavedData.get(level).clearForGameTest(FIELD_ID, bodyId);
                }
            }
        }
    }

    /** Boot B: require the production proximity pass to restore the exact edited body from the saved world. */
    @GameTest(template = "empty", timeoutTicks = 160, required = false, manualOnly = true,
            batch = "asteroid_edited_restart_verify")
    public static void verifyEditedAsteroidRestartFixture(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null || !server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Boot B requires an isolated normal dedicated server with the Voidsea level");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        AsteroidSavedData.StoredBody storedBody = AsteroidSavedData.get(level).storedBody(FIELD_ID);
        helper.assertTrue(storedBody != null,
                "Boot B loads the persisted field ID -> UUID -> Sable pointer override");
        if (storedBody == null) return;
        var storedBeforeLoad = holding.getStorage().attemptLoadSubLevel(
                storedBody.pointer().chunkPos(), storedBody.pointer().local());
        helper.assertTrue(storedBeforeLoad != null && storedBeforeLoad.uuid().equals(storedBody.bodyId()),
                "Boot B exact Sable storage read-back resolves the persisted detached UUID");
        FakePlayer viewer = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "AsteroidReturnProbe"));
        viewer.setPos(storedBody.x(), storedBody.y(), storedBody.z());
        level.addNewPlayer(viewer);
        if (!level.players().contains(viewer)) {
            level.removePlayerImmediately(viewer, Entity.RemovalReason.DISCARDED);
            FakePlayerFactory.unloadLevel(level);
            helper.fail("the FakePlayer fixture did not enter ServerLevel.players()");
            return;
        }
        XenoPixelsMod.LOGGER.info("ASTEROID_EDITED_RESTART_PROXIMITY armed fieldId={} viewer={} pos=({}, {}, {}) registered=true",
                FIELD_ID, viewer.getGameProfile().getName(), storedBody.x(), storedBody.y(), storedBody.z());
        helper.runAfterDelay(1, () -> awaitManagedProximityRestore(helper, level, container, holding,
                viewer, storedBody.bodyId(), 0));
    }

    private static void awaitManagedProximityRestore(GameTestHelper helper, ServerLevel level,
                                                       ServerSubLevelContainer container,
                                                       SubLevelHoldingChunkMap holding, FakePlayer viewer,
                                                       UUID expectedBodyId,
                                                       int waitedTicks) {
        ServerSubLevel fixture = null;
        for (var sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || body.getUserDataTag() == null
                    || !body.getUserDataTag().getBoolean(FIXTURE)) continue;
            if (fixture != null) {
                removeViewer(level, viewer);
                helper.fail("proximity restore produced more than one body with the detached fixture marker");
                return;
            }
            fixture = body;
        }
        if (fixture == null) {
            if (waitedTicks >= 100) {
                removeViewer(level, viewer);
                XenoPixelsMod.LOGGER.error("ASTEROID_EDITED_RESTART_PROXIMITY FAIL waitedTicks={} status={}",
                        waitedTicks, AsteroidPhysicalizer.status(level.getServer()));
                helper.fail("the production asteroid proximity pass did not restore the detached body within 100 ticks");
                return;
            }
            helper.runAfterDelay(1, () -> awaitManagedProximityRestore(helper, level, container, holding,
                    viewer, expectedBodyId, waitedTicks + 1));
            return;
        }

        UUID bodyId = fixture.getUniqueId();
        try {
            helper.assertTrue(level.players().contains(viewer),
                    "the production viewer source is the actual ServerLevel.players() collection");
            helper.assertTrue(expectedBodyId.equals(bodyId), "proximity activation restores the saved Sable UUID");
            helper.assertTrue(fixture.getUserDataTag().hasUUID(FIXTURE + "_uuid")
                            && fixture.getUserDataTag().getUUID(FIXTURE + "_uuid").equals(bodyId),
                    "the edited fixture marker retained the exact body UUID");
            helper.assertTrue(AsteroidSavedData.get(level).isDetached(FIELD_ID)
                            && AsteroidSavedData.get(level).isDetachedBody(bodyId),
                    "the stable field tombstone and UUID mapping survive proximity restore");
            helper.assertTrue(AsteroidPhysicalizer.isAsteroid(fixture),
                    "the restored body is reattached to the managed asteroid lifecycle");
            CompoundTag asteroidTag = fixture.getUserDataTag().getCompound(AsteroidPhysicalizer.TAG);
            long[] restoredFieldId = asteroidTag.getLongArray("id");
            helper.assertTrue(restoredFieldId.length == 4
                            && restoredFieldId[0] == FIELD_ID.cellX()
                            && restoredFieldId[1] == FIELD_ID.cellY()
                            && restoredFieldId[2] == FIELD_ID.cellZ()
                            && restoredFieldId[3] == FIELD_ID.slot()
                            && asteroidTag.getBoolean("touched"),
                    "the proximity-restored body retains field identity and touched override");
            GlobalSavedSubLevelPointer pointer = fixture.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "the restored body retains its Sable storage pointer");
            var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(stored != null && stored.uuid().equals(bodyId),
                    "the exact Sable pointer still identifies the restored body");
            CompoundTag currentPlot = SubLevelSerializer.toData(fixture, List.of()).fullTag().getCompound("plot");
            helper.assertTrue(stored.fullTag().getCompound("plot").equals(currentPlot),
                    "the restored plot matches its exact serialized storage entry");
            XenoPixelsMod.LOGGER.info("ASTEROID_EDITED_RESTART_VERIFY PASS fieldId={} bodyUuid={} pointer={} sameBody=true route=proximity-tick waitedTicks={}",
                    FIELD_ID, bodyId, pointer, waitedTicks);
            helper.succeed();
        } finally {
            removeViewer(level, viewer);
            container.removeForceLoadTicket(fixture, AsteroidPhysicalizer.TICKET, FIELD_ID.toString());
            if (!fixture.isRemoved()) container.removeSubLevel(fixture, SubLevelRemovalReason.REMOVED);
            holding.processChanges();
            GlobalSavedSubLevelPointer pointer = fixture.getLastSerializationPointer();
            if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
            AsteroidSavedData.get(level).clearForGameTest(FIELD_ID, bodyId);
        }
    }

    private static void removeViewer(ServerLevel level, FakePlayer viewer) {
        if (!viewer.isRemoved()) level.removePlayerImmediately(viewer, Entity.RemovalReason.DISCARDED);
        FakePlayerFactory.unloadLevel(level);
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
