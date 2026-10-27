package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Cross-container pod transfer when the source's serialized plot cell is already taken in the destination: the
 * transaction must move the copy to a free cell (PlotCellRemap) and keep blocks, a block entity's contents, a
 * scheduled tick and the rider seat consistent with the shifted storage frame. Each case occupies the source's cell
 * in the End with a dummy sub-level. Own batch, so no default-batch End body can take the cells. Proves the
 * serialized remap and pose math on real Sable bodies; not a client run.
 */
@GameTestHolder("xenopixels_p15")
@PrefixGameTestTemplate(false)
public final class SpaceHandoffSlotRemapGameTests {
    private static final String BATCH = "pod_transfer_slot_remap";
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);
    private static final Vector3d TARGET = new Vector3d(80.5, 64.5, 80.5);

    private SpaceHandoffSlotRemapGameTests() {}

    /** One fixture: the source pod, the dummy that blocks its cell in the End, and the transfer result. */
    private record Moved(ServerSubLevel source, BlockPos sourceVoxel, Vector3d sourceVoxelWorld, Vector3d sourcePose,
                         SubLevel occupant, PodTransferTransaction.Result result) {}

    private static Moved transferOverOccupiedCell(GameTestHelper helper, BlockState voxel,
                                                  java.util.function.Consumer<ServerSubLevel> prepare) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End is available to the GameTest server");
        ServerSubLevel source = stamp(build(helper, level, voxel), UUID.randomUUID());
        prepare.accept(source);
        var box = source.getPlot().getBoundingBox();
        BlockPos sourceVoxel = new BlockPos(box.minX(), box.minY(), box.minZ());
        Vector3d sourceVoxelWorld = source.logicalPose().transformPosition(
                new Vector3d(sourceVoxel.getX() + 0.5, sourceVoxel.getY() + 0.5, sourceVoxel.getZ() + 0.5), new Vector3d());
        Vector3d sourcePose = new Vector3d(source.logicalPose().position());
        CompoundTag plot = SubLevelSerializer.toData(source, List.of()).fullTag().getCompound("plot");
        int cellX = plot.getInt("plot_x"), cellZ = plot.getInt("plot_z");
        ServerSubLevelContainer endContainer = SubLevelContainer.getContainer(end);
        SubLevel occupant = endContainer.getSubLevel(cellX, cellZ);
        if (occupant == null) occupant = endContainer.allocateSubLevel(UUID.randomUUID(), cellX, cellZ, new Pose3d());
        XenoPixelsMod.LOGGER.info("PodTransfer slot-remap fixture: source cell {},{} origins source {} end {}",
                cellX, cellZ, SubLevelContainer.getContainer(level).getOrigin(), endContainer.getOrigin());
        var result = PodTransferTransaction.transfer(level, end, source, null, TARGET);
        XenoPixelsMod.LOGGER.info("PodTransfer slot-remap: status={} phase={} shift={} reason={}",
                result.status(), result.phase(), result.storageShift(), result.reason());
        return new Moved(source, sourceVoxel, sourceVoxelWorld, sourcePose, occupant, result);
    }

    /** The copy's single voxel in END storage coordinates, located through its own plot bounds. */
    private static BlockPos copyVoxel(ServerSubLevel copy) {
        var box = copy.getPlot().getBoundingBox();
        return new BlockPos(box.minX(), box.minY(), box.minZ());
    }

    private static String serializedBlockEntities(CompoundTag plot) {
        CompoundTag chunks = plot.getCompound("chunks");
        StringBuilder result = new StringBuilder();
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(key);
            if (chunk.contains("block_entities", Tag.TAG_LIST)) {
                if (!result.isEmpty()) result.append(';');
                result.append(key).append('=').append(chunk.getList("block_entities", Tag.TAG_COMPOUND));
            }
        }
        return result.toString();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void occupiedSourceCellIsRemappedWithExactPlacement(GameTestHelper helper) {
        Moved moved = transferOverOccupiedCell(helper, Blocks.STONE.defaultBlockState(), body -> {});
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        ServerSubLevel copy = moved.result().status() == PodTransferTransaction.Status.TRANSFERRED
                ? moved.result().destination() : null;
        try {
            helper.assertTrue(copy != null, "the copy is TRANSFERRED into a free cell (got " + moved.result().status()
                    + ": " + moved.result().reason() + ")");
            helper.assertTrue(moved.result().storageShift().lengthSquared() > 0, "the storage frame actually moved");
            helper.assertTrue(copy != moved.occupant() && !moved.occupant().isRemoved(), "the dummy occupant is untouched");
            Vector3d expected = new Vector3d(moved.sourceVoxelWorld()).sub(moved.sourcePose()).add(TARGET);
            BlockPos voxel = copyVoxel(copy);
            Vector3d actual = copy.logicalPose().transformPosition(
                    new Vector3d(voxel.getX() + 0.5, voxel.getY() + 0.5, voxel.getZ() + 0.5), new Vector3d());
            helper.assertTrue(actual.distance(expected) < 1.0E-6,
                    "the voxel sits at the same world point relative to the pose (expected " + expected + ", got " + actual + ")");
            helper.assertTrue(end.getBlockState(voxel).is(Blocks.STONE), "the block survived the cell move");
            XenoPixelsMod.LOGGER.info("PodTransfer slot-remap GameTest PASS: free cell, exact placement, block kept");
            helper.succeed();
        } finally {
            cleanup(helper, moved, copy);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void blockEntityContentsSurviveTheCellMove(GameTestHelper helper) {
        Moved moved = transferOverOccupiedCell(helper, Blocks.STONE.defaultBlockState(), body -> {
            var box = body.getPlot().getBoundingBox();
            BlockPos pos = new BlockPos(box.minX(), box.minY(), box.minZ());
            var plot = body.getPlot();
            var sourceHolders = plot.getLoadedChunks();
            var sourceHolder = sourceHolders.stream()
                    .filter(holder -> holder.getPos().equals(new ChunkPos(pos))).findFirst().orElse(null);
            if (sourceHolder == null)
                throw new IllegalStateException("Sable has no plot chunk holder for source chest chunk " + new ChunkPos(pos));
            var sourceChunk = sourceHolder.getChunk();
            sourceChunk.setBlockState(pos, Blocks.CHEST.defaultBlockState(), false);
            var sourceEntity = sourceChunk.getBlockEntity(pos);
            if (!(sourceEntity instanceof Container chest)) {
                throw new IllegalStateException("could not create source chest block entity in Sable plot holder at " + pos
                        + " (state=" + sourceChunk.getBlockState(pos) + ")");
            }
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            var sourceNbt = sourceHolder.getChunk()
                    .getBlockEntityNbtForSaving(pos, body.getLevel().registryAccess());
            var serializedPlot = SubLevelSerializer.toData(body, List.of()).fullTag().getCompound("plot");
            XenoPixelsMod.LOGGER.info("PodTransfer chest source fixture: pos={} chunk={} state={} entity={} nbt={} chunkBEs={} plotHolders={} holderState={} holderBE={} serializedEntities={}",
                    pos, new ChunkPos(pos), sourceChunk.getBlockState(pos), sourceEntity, sourceNbt,
                    sourceHolder.getChunk().getBlockEntities().keySet(), sourceHolders.size(),
                    sourceHolder.getChunk().getBlockState(pos), sourceHolder.getChunk().getBlockEntity(pos),
                    serializedBlockEntities(serializedPlot));
        });
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        ServerSubLevel copy = moved.result().status() == PodTransferTransaction.Status.TRANSFERRED
                ? moved.result().destination() : null;
        try {
            helper.assertTrue(copy != null, "TRANSFERRED (got " + moved.result().status() + ": " + moved.result().reason() + ")");
            BlockPos voxel = copyVoxel(copy);
            var destinationHolder = copy.getPlot().getLoadedChunks().stream()
                    .filter(holder -> holder.getPos().equals(new ChunkPos(voxel))).findFirst().orElse(null);
            var destinationChunk = destinationHolder == null ? null : destinationHolder.getChunk();
            var destinationEntity = destinationChunk == null ? null : destinationChunk.getBlockEntity(voxel);
            XenoPixelsMod.LOGGER.info("PodTransfer chest destination diagnostic: pos={} holder={} holderState={} entity={} serializedEntities={}",
                    voxel, destinationHolder != null,
                    destinationChunk == null ? null : destinationChunk.getBlockState(voxel), destinationEntity,
                    serializedBlockEntities(SubLevelSerializer.toData(copy, List.of()).fullTag().getCompound("plot")));
            helper.assertTrue(destinationChunk != null, "the moved Sable plot has a loaded chunk at the shifted position");
            helper.assertTrue(destinationChunk.getBlockState(voxel).is(Blocks.CHEST),
                    "the chest block exists in the moved Sable plot");
            helper.assertTrue(destinationEntity instanceof Container,
                    "the chest block entity exists in the moved Sable plot");
            ItemStack stack = ((Container) destinationEntity).getItem(0);
            helper.assertTrue(stack.is(Items.DIAMOND) && stack.getCount() == 7, "its contents survived (got " + stack + ")");
            XenoPixelsMod.LOGGER.info("PodTransfer slot-remap GameTest PASS: chest contents kept after the cell move");
            helper.succeed();
        } finally {
            cleanup(helper, moved, copy);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void scheduledBlockTickSurvivesTheCellMove(GameTestHelper helper) {
        Moved moved = transferOverOccupiedCell(helper, Blocks.STONE.defaultBlockState(), body -> {
            var box = body.getPlot().getBoundingBox();
            ((ServerLevel) body.getLevel()).scheduleTick(new BlockPos(box.minX(), box.minY(), box.minZ()), Blocks.STONE, 100_000);
        });
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        ServerSubLevel copy = moved.result().status() == PodTransferTransaction.Status.TRANSFERRED
                ? moved.result().destination() : null;
        try {
            helper.assertTrue(copy != null, "TRANSFERRED (got " + moved.result().status() + ": " + moved.result().reason() + ")");
            BlockPos voxel = copyVoxel(copy);
            helper.assertTrue(end.getBlockTicks().hasScheduledTick(voxel, Blocks.STONE),
                    "the scheduled tick moved with the block (loadTickList drops ticks outside their chunk)");
            XenoPixelsMod.LOGGER.info("PodTransfer slot-remap GameTest PASS: scheduled tick kept after the cell move");
            helper.succeed();
        } finally {
            cleanup(helper, moved, copy);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void riderSeatIsRecomputedOnTheMovedCopy(GameTestHelper helper) {
        Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        PodRiderBinding.Captured[] seat = new PodRiderBinding.Captured[1];
        Vector3d[] seatBefore = new Vector3d[1];
        Moved moved = transferOverOccupiedCell(helper, Blocks.STONE.defaultBlockState(), body -> {
            rider.setPos(helper.absoluteVec(CAPSULE_OFFSET.add(0.0, 0.5, 0.0)));
            seat[0] = PodRiderBinding.capture(rider, body);
            seatBefore[0] = new Vector3d(seat[0].plotLocalPosition());
        });
        ServerSubLevel copy = moved.result().status() == PodTransferTransaction.Status.TRANSFERRED
                ? moved.result().destination() : null;
        try {
            helper.assertTrue(copy != null, "TRANSFERRED (got " + moved.result().status() + ": " + moved.result().reason() + ")");
            Vec3 riderWorld = seat[0].worldPosition();
            Vector3d intended = new Vector3d(riderWorld.x, riderWorld.y, riderWorld.z).sub(moved.sourcePose()).add(TARGET);
            Vec3 riderDestination = new Vec3(intended.x, intended.y, intended.z);
            Vector3d stale = copy.logicalPose().transformPosition(seat[0].plotLocalPosition(), new Vector3d());
            helper.assertTrue(stale.distance(intended) > 1.0,
                    "the SOURCE seat is wrong on the moved copy (lands " + stale.distance(intended) + " blocks off)");
            var recomputed = SpaceHandoffPass.seatOn(copy, seat[0], riderDestination);
            Vector3d landing = copy.logicalPose().transformPosition(recomputed.plotLocalPosition(), new Vector3d());
            helper.assertTrue(landing.distance(intended) < 1.0E-6, "the recomputed seat lands on the destination");
            helper.assertTrue(seat[0].plotLocalPosition().equals(seatBefore[0]), "the original capture is not mutated");
            XenoPixelsMod.LOGGER.info("PodTransfer slot-remap GameTest PASS: seat recomputed on the moved copy");
            helper.succeed();
        } finally {
            cleanup(helper, moved, copy);
        }
    }

    private static ServerSubLevel build(GameTestHelper helper, ServerLevel level, BlockState voxel) {
        Vec3 centre = helper.absoluteVec(CAPSULE_OFFSET);
        PlotBodyBuilder.Result result = PlotBodyBuilder.build(level, new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? voxel : Blocks.AIR.defaultBlockState());
        helper.assertTrue(result != null && result.body() != null, "the one-voxel Sable capsule builds");
        return result.body();
    }

    private static ServerSubLevel stamp(ServerSubLevel body, UUID podId) {
        CompoundTag data = body.getUserDataTag();
        if (data == null) data = new CompoundTag();
        data.putUUID(POD_ID, podId);
        data.putString("xenopixelsmod:space_pod_hull", "xenopixelsmod:pod/hull/classic_egg");
        data.putInt("xenopixelsmod:space_pod_schema", 1);
        body.setUserDataTag(data);
        return body;
    }

    private static void cleanup(GameTestHelper helper, Moved moved, ServerSubLevel copy) {
        ServerLevel level = helper.getLevel();
        ServerLevel end = level.getServer().getLevel(Level.END);
        removeIfRegistered(level, moved.source());
        removeIfRegistered(level, moved.result().destination());
        removeIfRegistered(end, copy);
        removeIfRegistered(end, moved.result().destination());
        if (moved.occupant() instanceof ServerSubLevel occupant && !occupant.isRemoved())
            SubLevelContainer.getContainer(end).removeSubLevel(occupant, SubLevelRemovalReason.REMOVED);
    }

    private static void removeIfRegistered(ServerLevel level, ServerSubLevel body) {
        if (level == null || body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(body.getUniqueId()) != body) return;
        var podId = PodTransferTransaction.podId(body);
        if (podId != null) PodTransferTransaction.releaseTicket(body, podId);
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
