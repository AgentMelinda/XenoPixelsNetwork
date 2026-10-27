package net.bullettrain.xenopixelsmod.gametest;

import com.dragonminez.common.init.MainGameRules;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Exact Sable 2.0.3 holding-store probe for edited small-planet identity and plot round-trip.
 * This checks in-process restoration when the holding chunk is ready; it does not prove a full save/restart cycle.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlanetHoldingStorageGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;
    private static final String STARTUP_RESTART_FIXTURE_TAG = "xenopixelsmod_s31_startup_restart_fixture";
    private static final UUID STARTUP_RESTART_GHOST_RELEASE_ID =
            UUID.fromString("7d8ce2cc-a07d-4c26-89f5-cb2b4f5a9821");

    private SmallPlanetHoldingStorageGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dmzKiGriefHookKeepsUnmanagedBlocksAllowed(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(MainGameRules.canKiGrief(helper.getLevel(), position, null),
                "the Xeno hook leaves an unrelated DMZ-permitted block unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void dmzKiGriefHookDeniesUntrackedTaggedPlanet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the fixture Sable body is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag data = new CompoundTag();
        data.put(SmallPlanetPhysicalizer.TAG, new CompoundTag());
        body.setUserDataTag(data);
        BlockPos position = body.getPlot().getCenterBlock();
        try {
            helper.assertTrue(container.getPlot(new ChunkPos(position)) != null,
                    "the policy input resolves to the tagged Sable plot");
            helper.assertFalse(SmallPlanetPhysicalizer.allowKiMutation(level, position),
                    "a tagged body without an active edit reservation is denied");
            helper.assertFalse(MainGameRules.canKiGrief(level, position, null),
                    "DMZ's shared Ki-grief path enforces the Xeno planet admission");
            helper.succeed();
        } finally {
            if (container.getSubLevel(id) instanceof ServerSubLevel live && !live.isRemoved())
                container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_holding_roundtrip")
    public static void editedIdentityAndPlotSurviveReadyChunkRestore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        BlockPos hostAnchor = helper.absolutePos(new BlockPos(2, 64, 2));
        Vector3d center = new Vector3d(hostAnchor.getX() + 0.5, hostAnchor.getY() + 0.5, hostAnchor.getZ() + 0.5);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, center, 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the one-voxel Sable body is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag identity = new CompoundTag();
        identity.putString("body_id", "fixture:edited-small-world");
        identity.putBoolean("touched", true);
        CompoundTag userData = new CompoundTag();
        userData.put(SmallPlanetPhysicalizer.TAG, identity);
        body.setUserDataTag(userData);
        BlockPos editPos = BlockPos.ZERO;
        var embedded = body.getPlot().getEmbeddedLevelAccessor();
        helper.assertTrue(embedded.getBlockState(editPos).is(Blocks.STONE),
                "the one-voxel generated plot begins as stone at its local center");
        helper.assertTrue(embedded.setBlock(editPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3, 0),
                "the test changes the plot voxel to a distinct edited block state");
        CompoundTag originalPlot = SubLevelSerializer.toData(body, List.of()).fullTag().getCompound("plot").copy();
            ChunkPos holdingChunk = new ChunkPos(hostAnchor);
        ServerSubLevel restored = null;

        try {
            var savedBounds = SubLevelSerializer.toData(body, List.of()).bounds();
            int minChunkX = net.minecraft.util.Mth.floor(savedBounds.minX() - 1.0) >> 4;
            int maxChunkX = net.minecraft.util.Mth.floor(savedBounds.maxX() + 1.0) >> 4;
            int minChunkZ = net.minecraft.util.Mth.floor(savedBounds.minZ() - 1.0) >> 4;
            int maxChunkZ = net.minecraft.util.Mth.floor(savedBounds.maxZ() + 1.0) >> 4;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) level.getChunk(cx, cz);
            }
            holding.moveToUnloaded(body, holdingChunk);
            holding.updateChunkStatus(holdingChunk, true);
            helper.assertTrue(container.getSubLevel(id) == null,
                    "moveToUnloaded removes the live incarnation after putting it in holding storage");
            HoldingSubLevel stored = holding.getHoldingSubLevel(id);
            helper.assertTrue(stored != null, "the UUID is present in the in-memory holding store");
            helper.assertTrue(stored.data().fullTag().getCompound("user_data")
                            .getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"),
                    "Sable serialized the small-planet identity and touched marker");
            helper.assertTrue(stored.data().fullTag().getCompound("plot").equals(originalPlot),
                    "Sable holding data contains the exact edited plot snapshot");

            holding.processChanges();
            if (container.getSubLevel(id) instanceof ServerSubLevel loaded) restored = loaded;
            XenoPixelsMod.LOGGER.info("Small planet holding restore diagnostic: id={} restoredLive={} heldStillPresent={} holdingChunk={} currentPointer={}",
                    id, restored != null && !restored.isRemoved(), holding.getHoldingSubLevel(id) != null,
                    holdingChunk, body.getLastSerializationPointer());
            helper.assertTrue(restored != null && !restored.isRemoved(),
                    "processing changes restores the held body when its holding chunk is ready");
            helper.assertTrue(restored.getUserDataTag() != null
                            && restored.getUserDataTag().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"),
                    "the restored live body retains its small-planet identity and touched marker");
            CompoundTag restoredPlot = SubLevelSerializer.toData(restored, List.of()).fullTag().getCompound("plot");
            helper.assertTrue(restoredPlot.equals(originalPlot), "the restored body has the exact original plot snapshot");
            XenoPixelsMod.LOGGER.info("Small planet holding GameTest PASS: id={} holdingChunk={} plotKeys={} restoredByReadyChunk=true",
                    id, holdingChunk, originalPlot.getCompound("chunks").getAllKeys());
            helper.succeed();
        } finally {
            try {
                for (int cleanupPass = 0; cleanupPass < 2; cleanupPass++) {
                    ServerSubLevel live = container.getSubLevel(id) instanceof ServerSubLevel found ? found : null;
                    if (live != null && !live.isRemoved()) {
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                        holding.processChanges();
                    }
                    HoldingSubLevel held = holding.getHoldingSubLevel(id);
                    if (held != null && held.pointer() == null) {
                        holding.processChanges();
                        held = holding.getHoldingSubLevel(id);
                    }
                    if (held == null || held.pointer() == null) break;
                    holding.snatchAndLoad(held.pointer(), id);
                }
                holding.processChanges();
            } catch (RuntimeException cleanupFailure) {
                XenoPixelsMod.LOGGER.error("Small planet holding GameTest cleanup failed for fixture {}", id, cleanupFailure);
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_storage_readback")
    public static void releasedMarkerCanBeReadBackFromSerializedStorage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the storage read-back fixture builds");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag userData = new CompoundTag();
        CompoundTag smallPlanetData = new CompoundTag();
        smallPlanetData.putBoolean("touched", true);
        userData.put(SmallPlanetPhysicalizer.TAG, smallPlanetData);
        userData.putBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released", true);
        body.setUserDataTag(userData);

        try {
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            holding.saveAll();
            GlobalSavedSubLevelPointer pointer = body.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "Sable assigns a serialization pointer during saveAll");
            var data = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(data != null, "the storage file returns the serialized body");
            helper.assertTrue(data.uuid().equals(id), "the referenced storage slot contains this body's UUID");
            helper.assertTrue(data.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the referenced storage slot contains the released marker written before saveAll");

            // Sable can overwrite the same storage slot through an existing pointer. Verify the
            // updated serialized content through that pointer; pointer inequality is not a valid
            // save-success assertion.
            userData.putInt("xenopixels_test_save_generation", 2);
            body.setUserDataTag(userData);
            XenoPixelsMod.LOGGER.info("Small planet storage pre-save diagnostic: id={} bodyGeneration={} serializedGeneration={} serializedUserData={}",
                    id, body.getUserDataTag().getInt("xenopixels_test_save_generation"),
                    SubLevelSerializer.toData(body, List.of()).fullTag().getCompound("user_data")
                            .getInt("xenopixels_test_save_generation"),
                    SubLevelSerializer.toData(body, List.of()).fullTag().getCompound("user_data"));
            holding.saveAll();
            GlobalSavedSubLevelPointer updatedPointer = body.getLastSerializationPointer();
            helper.assertTrue(updatedPointer != null, "the second write retains the body serialization pointer");
            var updatedData = holding.getStorage().attemptLoadSubLevel(
                    updatedPointer.chunkPos(), updatedPointer.local());
            var heldAfterSave = holding.getHoldingSubLevel(id);
            XenoPixelsMod.LOGGER.info("Small planet storage reread diagnostic: id={} stillLive={} heldGeneration={} firstPointer={} updatedPointer={} dataPresent={} dataUuid={} expectedGeneration=2 actualGeneration={}",
                    id, container.getSubLevel(id) == body,
                    heldAfterSave == null ? -1 : heldAfterSave.data().fullTag().getCompound("user_data").getInt("xenopixels_test_save_generation"),
                    pointer, updatedPointer, updatedData != null, updatedData == null ? null : updatedData.uuid(),
                    updatedData == null ? -1 : updatedData.fullTag().getCompound("user_data").getInt("xenopixels_test_save_generation"));
            // Sable may have no held-cache entry after save, depending on this dimension's
            // holding lifecycle. Absence is not a stale snapshot; if it does retain an entry,
            // that entry must match the newest serialized generation.
            helper.assertTrue(heldAfterSave == null
                            || heldAfterSave.data().fullTag().getCompound("user_data")
                            .getInt("xenopixels_test_save_generation") == 2,
                    "a retained holding-chunk cache entry must not point to the stale first-write snapshot");
            helper.assertTrue(updatedData != null && updatedData.uuid().equals(id),
                    "the current pointer still resolves this body after the second save");
            helper.assertTrue(updatedData.fullTag().getCompound("user_data")
                            .getInt("xenopixels_test_save_generation") == 2,
                    "the exact storage slot contains the updated body state after re-serialization");
            holding.saveAll();
            var repeatedPointer = body.getLastSerializationPointer();
            var repeatedData = holding.getStorage().attemptLoadSubLevel(
                    repeatedPointer.chunkPos(), repeatedPointer.local());
            helper.assertTrue(repeatedData != null && repeatedData.uuid().equals(id)
                            && repeatedData.fullTag().getCompound("user_data")
                            .getInt("xenopixels_test_save_generation") == 2,
                    "a later full save does not overwrite the edited body's slot with the stale cached snapshot");
            helper.assertTrue(SmallPlanetPhysicalizer.auditReleasedMarkerReadbackForGameTest(level, id) == 1,
                    "the once-after-adoption startup audit prunes after reading this body's exact serialized slot");
            helper.assertTrue(net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetSavedData.get(level)
                            .releasedQuarantineCount() == 0,
                    "the verified SavedData release decision is removed");
            holding.getStorage().attemptSaveSubLevel(updatedPointer, null);
            var deletedData = holding.getStorage().attemptLoadSubLevel(
                    updatedPointer.chunkPos(), updatedPointer.local());
            helper.assertTrue(deletedData == null,
                    "the exact storage slot reads as absent after Sable's null-data deletion write");
            container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            XenoPixelsMod.LOGGER.info("Small planet release-marker storage read-back GameTest PASS: uuid={} pointer={}",
                    id, updatedPointer);
            helper.succeed();
        } finally {
            container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            holding.processChanges();
        }
    }

    /** Dedicated-server-only: saveEverything flushes every dimension and player data; use an isolated server with no players. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_host_save_event",
            required = false, manualOnly = true)
    public static void realVoidseaSaveEventPrunesVerifiedReleaseMarker(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel host = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) {
            helper.fail("This manual integration test requires a normal dedicated server with the Voidsea level; GameTestServer has no Voidsea stem");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Run this all-dimensions save test only on an isolated dedicated server with no connected players");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(host, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the Voidsea Sable fixture builds");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag userData = new CompoundTag();
        CompoundTag smallPlanetData = new CompoundTag();
        smallPlanetData.putBoolean("touched", true);
        userData.put(SmallPlanetPhysicalizer.TAG, smallPlanetData);
        userData.putBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released", true);
        body.setUserDataTag(userData);
        GlobalSavedSubLevelPointer pointer = null;

        try {
            // The vanilla forced ticket keeps this fixture independent of Xeno's adoption/reconciliation tickets.
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            holding.saveAll();
            pointer = body.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "Sable serializes the Voidsea fixture before the save event");
            var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(stored != null && stored.uuid().equals(id)
                            && stored.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the fixture's exact Sable storage slot contains its UUID and release marker");

            SmallPlanetPhysicalizer.queueReleaseMarkerForSaveEventGameTest(host, id);
            helper.assertTrue(SmallPlanetSavedData.get(host).isQuarantineReleased(id),
                    "the durable release decision is queued before the server save");
            boolean saved = host.getServer().saveEverything(true, true, true);
            helper.assertTrue(saved, "MinecraftServer.saveEverything completes");
            helper.assertTrue(!SmallPlanetSavedData.get(host).isQuarantineReleased(id),
                    "the real host LevelEvent.Save handler prunes only after reading the exact Sable UUID and marker");

            XenoPixelsMod.LOGGER.info("Small planet real Voidsea save-event GameTest PASS: uuid={} pointer={}", id, pointer);
            helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, id);
            if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
            container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            holding.processChanges();
        }
    }

    /** Missing Sable pointers must fail closed and stay queued for a later save. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_missing_pointer_save_event",
            required = false, manualOnly = true)
    public static void realVoidseaSaveEventRetainsDecisionWithoutSablePointer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel host = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) {
            helper.fail("This manual integration test requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Run this all-dimensions save test only on an isolated dedicated server with no connected players");
            return;
        }

        UUID id = UUID.randomUUID();
        try {
            SmallPlanetPhysicalizer.queueReleaseMarkerForSaveEventGameTest(host, id);
            boolean saved = server.saveEverything(true, true, true);
            helper.assertTrue(saved, "the forced save-all flush completes");
            helper.assertTrue(SmallPlanetSavedData.get(host).isQuarantineReleased(id),
                    "a release decision without any Sable pointer remains durable");
            helper.assertTrue(SmallPlanetPhysicalizer.isReleaseMarkerAuditFallbackQueuedForGameTest(host, id)
                            && !SmallPlanetPhysicalizer.isReleaseMarkerAuditPrioritizedForGameTest(host, id),
                    "a pointerless release candidate moves to fallback (one candidate, no priority entry) for a later save");
            String status = SmallPlanetPhysicalizer.status(server);
            helper.assertTrue(status.contains("releaseAuditPointerlessLast=1")
                            && status.contains("releaseAuditPointerlessSample=[" + id + "]"),
                    "small-planet status exposes the unresolved pointer count and bounded UUID sample");
            XenoPixelsMod.LOGGER.info("Small planet missing-pointer retention GameTest PASS: uuid={}", id);
            helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, id);
        }
    }

    /** A repaired live marker is retained once, serialized by the next save, then verified and pruned. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_marker_repair_save_event",
            required = false, manualOnly = true)
    public static void realVoidseaSaveEventRepairsThenPrunesSerializedMarker(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel host = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) {
            helper.fail("This manual integration test requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Run this all-dimensions save test only on an isolated dedicated server with no connected players");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        ServerSubLevel body = null;
        GlobalSavedSubLevelPointer pointer = null;
        UUID id = null;
        try {
            PlotBodyBuilder.Result built = PlotBodyBuilder.build(host, new Vector3d(96.5, 64.5, 64.5), 1,
                    (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
            helper.assertTrue(built != null && built.body() != null, "the Voidsea Sable fixture builds");
            body = built.body();
            id = body.getUniqueId();
            CompoundTag userData = new CompoundTag();
            CompoundTag smallPlanetData = new CompoundTag();
            smallPlanetData.putBoolean("touched", true);
            userData.put(SmallPlanetPhysicalizer.TAG, smallPlanetData);
            body.setUserDataTag(userData); // Deliberately omit QUARANTINE_RELEASED_TAG.
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            holding.saveAll();
            pointer = body.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "Sable serializes the unmarked fixture before the audit");
            var initial = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(initial != null && initial.uuid().equals(id)
                            && !initial.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the exact Sable slot contains this UUID without the release marker");

            SmallPlanetPhysicalizer.queueReleaseMarkerForSaveEventGameTest(host, id);
            boolean firstSave = server.saveEverything(true, true, true);
            helper.assertTrue(firstSave, "the first forced save-all flush completes");
            helper.assertTrue(SmallPlanetSavedData.get(host).isQuarantineReleased(id),
                    "the audit retains its durable decision while repairing the live marker");
            helper.assertTrue(body.getUserDataTag() != null
                            && body.getUserDataTag().getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the audit repairs the marker on the live body");
            helper.assertTrue(SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(host, id),
                    "the repaired body remains queued until its marker is serialized and read back");

            boolean secondSave = server.saveEverything(true, true, true);
            helper.assertTrue(secondSave, "the second forced save-all flush completes");
            var repaired = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(repaired != null && repaired.uuid().equals(id)
                            && repaired.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the second save serializes the repaired marker into the exact Sable slot");
            helper.assertTrue(!SmallPlanetSavedData.get(host).isQuarantineReleased(id),
                    "the durable decision is pruned only after the repaired marker reads back");
            helper.assertTrue(!SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(host, id),
                    "the verified candidate leaves the audit queue");
            XenoPixelsMod.LOGGER.info("Small planet marker repair/readback GameTest PASS: uuid={} pointer={}", id, pointer);
            helper.succeed();
        } finally {
            if (id != null) SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, id);
            if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
            if (body != null) {
                container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
                if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
            holding.processChanges();
        }
    }

    /** Boot A: persist a real marked Sable body and release decision after startup adoption, without queueing it in this process. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_startup_restart_seed",
            required = false, manualOnly = true)
    public static void seedVoidseaStartupAuditRestartFixture(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel host = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) {
            helper.fail("Boot A requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Boot A must use an isolated dedicated server with no connected players");
            return;
        }
        helper.assertTrue(SmallPlanetPhysicalizer.isStartupReleaseMarkerAuditDoneForGameTest(host),
                "Boot A runs only after this process completed startup adoption/audit");
        helper.assertTrue(SmallPlanetPhysicalizer.isReleaseMarkerAuditQueueEmptyForGameTest(host),
                "Boot A starts with no unrelated release-audit candidates");

        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        ServerSubLevel body = null;
        GlobalSavedSubLevelPointer pointer = null;
        UUID id = UUID.randomUUID();
        boolean persistedForRestart = false;
        boolean ghostReleaseRecorded = false;
        try {
            PlotBodyBuilder.Result built = PlotBodyBuilder.build(host, new Vector3d(280.5, 64.5, 64.5), 1,
                    (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
            helper.assertTrue(built != null && built.body() != null, "Boot A Sable fixture body builds");
            body = built.body();
            id = body.getUniqueId();
            CompoundTag userData = new CompoundTag();
            userData.putBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released", true);
            userData.putBoolean(STARTUP_RESTART_FIXTURE_TAG, true);
            userData.putUUID(STARTUP_RESTART_FIXTURE_TAG + "_uuid", id);
            body.setUserDataTag(userData);
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            holding.saveAll();
            pointer = body.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "Boot A serializes the marked body to a Sable pointer");
            var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(stored != null && stored.uuid().equals(body.getUniqueId())
                            && stored.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "Boot A exact Sable slot contains the fixture body UUID and release marker before SavedData seeding");

            SmallPlanetSavedData saved = SmallPlanetSavedData.get(host);
            helper.assertTrue(!saved.isQuarantineReleased(body.getUniqueId()),
                    "Boot A fixture UUID has no pre-existing release decision");
            helper.assertTrue(saved.markQuarantineReleased(body.getUniqueId()),
                    "Boot A records a new durable release decision");
            helper.assertTrue(!saved.isQuarantineReleased(STARTUP_RESTART_GHOST_RELEASE_ID),
                    "Boot A ghost UUID has no pre-existing release decision");
            helper.assertTrue(saved.markQuarantineReleased(STARTUP_RESTART_GHOST_RELEASE_ID),
                    "Boot A records a durable pointerless positive-control decision");
            ghostReleaseRecorded = true;
            helper.assertTrue(!SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(host, body.getUniqueId()),
                    "the already-adopted runtime does not receive the decision seeded after startup");
            helper.assertTrue(!SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(
                            host, STARTUP_RESTART_GHOST_RELEASE_ID),
                    "the already-adopted runtime does not receive the pointerless decision seeded after startup");

            boolean savedAll = server.saveEverything(true, true, true);
            helper.assertTrue(savedAll, "Boot A forced save-all flush completes");
            helper.assertTrue(saved.isQuarantineReleased(body.getUniqueId()),
                    "Boot A decision survives its save because this process never queued the candidate");
            helper.assertTrue(saved.isQuarantineReleased(STARTUP_RESTART_GHOST_RELEASE_ID),
                    "Boot A pointerless positive-control decision survives the forced save");
            helper.assertTrue(!SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(host, body.getUniqueId()),
                    "Boot A save-event handler did not acquire the post-adoption fixture candidate");
            var afterSave = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(afterSave != null && afterSave.uuid().equals(body.getUniqueId())
                            && afterSave.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "Boot A fixture remains in its exact serialized Sable slot after saving");
            persistedForRestart = true;
            XenoPixelsMod.LOGGER.info("Small planet startup-audit restart fixture seeded for Boot B: releaseUuid={} bodyUuid={} pointer={}",
                    body.getUniqueId(), body.getUniqueId(), pointer);
            helper.succeed();
        } finally {
            if (!persistedForRestart) {
                if (body != null) {
                    container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
                    if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    holding.processChanges();
                }
                if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
                SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, id);
            }
            if (!persistedForRestart && ghostReleaseRecorded)
                SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, STARTUP_RESTART_GHOST_RELEASE_ID);
        }
    }

    /** Boot B: after real startup adoption and first tick, the persisted fixture decision must be pruned. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_startup_restart_verify",
            required = false, manualOnly = true)
    public static void verifyVoidseaStartupAuditRestartFixture(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel host = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (host == null) {
            helper.fail("Boot B requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Boot B must use an isolated dedicated server with no connected players");
            return;
        }
        helper.assertTrue(SmallPlanetPhysicalizer.isStartupReleaseMarkerAuditDoneForGameTest(host),
                "the first-tick-after-adoption audit completed before this verification command");

        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        try {
        ServerSubLevel fixture = null;
        for (var sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || body.getUserDataTag() == null) continue;
            CompoundTag userData = body.getUserDataTag();
            if (!userData.getBoolean(STARTUP_RESTART_FIXTURE_TAG)) continue;
            helper.assertTrue(fixture == null, "exactly one Boot A restart fixture is present");
            fixture = body;
        }
        helper.assertTrue(fixture != null, "Boot B restores the persisted Sable fixture from its force-load ticket");
        UUID fixtureUuid = fixture.getUniqueId();
        CompoundTag fixtureData = fixture.getUserDataTag();
        helper.assertTrue(fixtureData.hasUUID(STARTUP_RESTART_FIXTURE_TAG + "_uuid")
                        && fixtureUuid.equals(fixtureData.getUUID(STARTUP_RESTART_FIXTURE_TAG + "_uuid")),
                "Boot B fixture identity matches its persisted UUID tag");
        helper.assertTrue(fixtureData.getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                "Boot B body user data retains the release marker");
        helper.assertTrue(!SmallPlanetSavedData.get(host).isQuarantineReleased(fixtureUuid),
                "Boot B SavedData release decision was pruned by the startup readback audit");
        helper.assertTrue(SmallPlanetSavedData.get(host).isQuarantineReleased(STARTUP_RESTART_GHOST_RELEASE_ID),
                "Boot B retains the pointerless positive-control decision after the one-candidate startup audit");
        helper.assertTrue(SmallPlanetPhysicalizer.isReleaseMarkerAuditFallbackQueuedForGameTest(
                        host, STARTUP_RESTART_GHOST_RELEASE_ID),
                "Boot B keeps the unbacked UUID queued as fallback rather than pruning it");

        var pointer = fixture.getLastSerializationPointer();
        helper.assertTrue(pointer != null, "Boot B restored fixture has its Sable storage pointer");
        var stored = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
        helper.assertTrue(stored != null && stored.uuid().equals(fixtureUuid)
                        && stored.fullTag().getCompound("user_data")
                        .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                "Boot B exact Sable storage slot still contains the fixture UUID and release marker");
        XenoPixelsMod.LOGGER.info("Small planet startup-audit restart fixture verified on Boot B: uuid={} pointer={}",
                fixtureUuid, pointer);
        XenoPixelsMod.LOGGER.info("Small planet startup-audit ghost positive control retained: releaseUuid={} fallbackQueued=true",
                STARTUP_RESTART_GHOST_RELEASE_ID);
        helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(host, STARTUP_RESTART_GHOST_RELEASE_ID);
        }
    }
}
