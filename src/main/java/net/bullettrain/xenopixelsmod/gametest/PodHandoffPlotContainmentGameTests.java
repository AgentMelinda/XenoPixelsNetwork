package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * Measures which coordinate frame Sable's plot lookup actually resolves a seat in, at chart scale.
 *
 * <p>The pod handoff's arrival watch accepts a rider once the SERVER reports them as tracking the arrived
 * body, and that report comes from {@code getContaining(level, packet.x, packet.z)}. Pinned Sable 2.0.3 has
 * two position frames per body: the level's own storage block coordinates, where the plot's chunks live, and
 * the body's logical pose, where the body is presented. {@code PodRiderBinding.capture} stores the seat in the
 * first (through {@code transformPositionInverse}) and {@code restore} writes the second to the player with
 * {@code setPos}. A client that already tracks the sub-level inverse-transforms its outgoing position, so its
 * packets arrive in the storage frame; a client that does not track yet sends its raw {@code getX/getY/getZ},
 * which are the logical-frame values the server just wrote. The two can only be the same query if the frames
 * coincide.
 *
 * <p>Each case therefore resolves the SAME seat three ways and reports which one finds the body: the storage
 * value itself, the logical-pose transform of it, and the last-pose transform of it. The pre-committed
 * prediction, published in the channel at 2026-10-20 08:51 before this file ran: at a chart-scale fixture the
 * storage query resolves the body and the two pose queries do not, because one run measured the same body at
 * logical X=100000.5 while its holding plot center sat at X=20483080; if a pose query DOES resolve it, that
 * divergence reading is wrong instead. Only the both-frames-null case is asserted as a failure, since that is
 * the state in which no client, tracking or not, could ever confirm the arrival.
 *
 * <p>Scope limit: this is a server-side containment measurement with no player and no client, so it cannot and
 * does not show what any client sends. Whether an arrived rider's client adopts tracking from the freeze
 * packet before its first position packet remains a runtime question this test does not answer.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodHandoffPlotContainmentGameTests {
    /** Chart-scale fixture, on a Z used by no other velocity or containment fixture in this package. */
    private static final Vec3 CHART_CENTRE = new Vec3(100_000.5, 256.5, 850.5);
    /** Control fixture: same query pair at a local coordinate, to separate frame divergence from magnitude. */
    private static final Vec3 LOCAL_CENTRE = new Vec3(0.5, 316.5, 950.5);
    /** The seat is "one block above the pose origin", which is a hull-interior point for a one-voxel body. */
    private static final double SEAT_OFFSET_Y = 1.0;
    /** Alias fixtures, on Z values no other fixture in this package uses. */
    private static final Vec3 SELF_CENTRE = new Vec3(300_000.5, 256.5, 1050.5);
    private static final Vec3 CROSS_POD_CENTRE = new Vec3(300_000.5, 256.5, 1150.5);
    private static final Vec3 CROSS_OTHER_CENTRE = new Vec3(300_000.5, 256.5, 1250.5);
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private PodHandoffPlotContainmentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void chartScaleSeatResolvesItsBodyInExactlyOnePacketFrame(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runContainmentProbe(helper, end, CHART_CENTRE, "the_end x=100000.5");
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void localScaleSeatResolvesItsBodyInExactlyOnePacketFrame(GameTestHelper helper) {
        runContainmentProbe(helper, helper.getLevel(), LOCAL_CENTRE, "origin");
    }

    private static void runContainmentProbe(GameTestHelper helper, ServerLevel level, Vec3 centre,
                                            String scenario) {
        level.getChunk(net.minecraft.core.BlockPos.containing(centre));
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel body builds in " + level.dimension().location());
        ServerSubLevel body = built.body();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        helper.runAfterDelay(1, () -> probe(helper, container, level, body, centre, scenario));
    }

    private static void probe(GameTestHelper helper, ServerSubLevelContainer container, ServerLevel level,
                              ServerSubLevel body, Vec3 centre, String scenario) {
        if (body.isRemoved()) {
            XenoPixelsMod.LOGGER.warn("PlotContainment probe INCONCLUSIVE at {}: the body was removed", scenario);
            helper.assertTrue(false, "plot containment probe: the body disappeared; the frames stay NOT MEASURED");
            return;
        }
        var logical = body.logicalPose();
        var last = body.lastPose();
        Vector3d worldSeat = new Vector3d(centre.x, centre.y + SEAT_OFFSET_Y, centre.z);
        Vector3d seat = logical.transformPositionInverse(worldSeat, new Vector3d());
        Vector3d fromLogical = logical.transformPosition(seat, new Vector3d());
        Vector3d fromLast = last.transformPosition(seat, new Vector3d());
        var bySeat = Sable.HELPER.getContaining(level, seat);
        var byLogical = Sable.HELPER.getContaining(level, fromLogical);
        var byLast = Sable.HELPER.getContaining(level, fromLast);
        boolean seatResolves = bySeat == body;
        boolean logicalResolves = byLogical == body;
        boolean lastResolves = byLast == body;
        XenoPixelsMod.LOGGER.info("PlotContainment probe at {}: body={} plotCenterBlock={} logicalPose={}"
                        + " lastPose={} worldSeat={} seat={} roundTripError={} resolvedBySeat={} ({})"
                        + " resolvedByLogicalPose={} ({}) resolvedByLastPose={} ({})",
                scenario, body.getUniqueId(), body.getPlot().getCenterBlock(), logical.position(), last.position(),
                worldSeat, seat, fromLogical.distanceSquared(worldSeat),
                seatResolves, id(bySeat), logicalResolves, id(byLogical), lastResolves, id(byLast));
        if (body.getPlot().getBoundingBox().volume() <= 0) {
            releaseAndRemove(container, body);
            helper.assertTrue(false, "plot containment probe: the body's plot is empty, so containment is moot");
            return;
        }
        releaseAndRemove(container, body);
        helper.assertTrue(container.getSubLevel(body.getUniqueId()) == null,
                "the probe body and its ticket were cleaned up");
        helper.assertTrue(seatResolves || logicalResolves || lastResolves,
                "no frame resolves the body it is sitting in, so no client could ever confirm the arrival ("
                        + "seat=" + seat + " logical=" + fromLogical + ")");
        helper.succeed();
    }

    /**
     * Self-alias, the deterministic headless test Codex asked for at 2026-10-20 08:59. A body is teleported
     * so its logical pose sits INSIDE its own storage column, which is the placement a pod would have if the
     * chart ever parked it on top of its own plot cell. Prediction, published before running: the raw
     * logical position then resolves that same body, so "an untracked rider's packets resolve nothing" is a
     * placement fact of the two fixtures above, not a structural wall. If it does not resolve, the structural
     * reading stands and this test fails loudly.
     */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void logicalPoseInsideItsOwnStorageColumnResolvesThatSameBody(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(end);
        ServerSubLevel body = buildBody(helper, container, end, SELF_CENTRE, "self-alias");
        // Same settle the proven probe path uses: the handle and plot assignment are not queried on frame 0.
        helper.runAfterDelay(1, () -> {
            var cell = body.getPlot().getCenterBlock();
            teleportTo(helper, body, new Vector3d(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5));
            helper.runAfterDelay(1, () -> {
                Vector3d worldSeat = new Vector3d(body.logicalPose().position()).add(0.0, SEAT_OFFSET_Y, 0.0);
                Vector3d seat = body.logicalPose().transformPositionInverse(worldSeat, new Vector3d());
                var bySeat = Sable.HELPER.getContaining(end, seat);
                var byWorld = Sable.HELPER.getContaining(end, worldSeat);
                XenoPixelsMod.LOGGER.info("PlotContainment alias probe at self: pod={} storageCell={}"
                                + " logicalPose={} worldSeat={} seat={} resolvedBySeat={} resolvedByWorldSeat={}",
                        body.getUniqueId(), cell, body.logicalPose().position(), worldSeat, seat,
                        id(bySeat), id(byWorld));
                releaseAndRemove(container, body);
                helper.assertTrue(bySeat == body, "the storage-frame seat stopped resolving its own body");
                helper.assertTrue(byWorld == body,
                        "a rider standing at the pod's OWN logical position does not resolve the pod, so the frame"
                                + " divergence is structural after all");
                helper.succeed();
            });
        });
    }

    /**
     * Cross-alias: pod A's logical pose is parked inside ANOTHER body's storage column. Prediction: A's own
     * storage seat still resolves A, while A's raw logical position resolves B - the exact false confirmation
     * an untracked client would produce, with the returned UUID reported against the expected one.
     */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void logicalPoseInsideAnotherBodysStorageColumnResolvesTheOtherBody(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(end);
        ServerSubLevel pod = buildBody(helper, container, end, CROSS_POD_CENTRE, "cross-alias pod");
        ServerSubLevel other = buildBody(helper, container, end, CROSS_OTHER_CENTRE, "cross-alias other");
        helper.runAfterDelay(1, () -> {
            var otherCell = other.getPlot().getCenterBlock();
            teleportTo(helper, pod, new Vector3d(otherCell.getX() + 0.5, otherCell.getY() + 0.5,
                    otherCell.getZ() + 0.5));
            helper.runAfterDelay(1, () -> {
                Vector3d worldSeat = new Vector3d(pod.logicalPose().position()).add(0.0, SEAT_OFFSET_Y, 0.0);
                Vector3d seat = pod.logicalPose().transformPositionInverse(worldSeat, new Vector3d());
                var bySeat = Sable.HELPER.getContaining(end, seat);
                var byWorld = Sable.HELPER.getContaining(end, worldSeat);
                XenoPixelsMod.LOGGER.info("PlotContainment alias probe at cross: expectedPod={}"
                                + " returnedForSeat={} returnedForWorldSeat={} otherBody={} otherStorageCell={}"
                                + " podLogicalPose={} worldSeat={} seat={}", pod.getUniqueId(), id(bySeat),
                        id(byWorld), other.getUniqueId(), otherCell, pod.logicalPose().position(), worldSeat, seat);
                releaseAndRemove(container, pod);
                releaseAndRemove(container, other);
                helper.assertTrue(bySeat == pod, "pod A's storage-frame seat stopped resolving pod A");
                helper.assertTrue(byWorld == other && byWorld != pod,
                        "a rider at pod A's logical position did not resolve the body stored there (got "
                                + id(byWorld) + ", wanted " + other.getUniqueId() + ")");
                helper.succeed();
            });
        });
    }

    private static ServerSubLevel buildBody(GameTestHelper helper, ServerSubLevelContainer container,
                                            ServerLevel level, Vec3 centre, String scenario) {
        level.getChunk(net.minecraft.core.BlockPos.containing(centre));
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel body builds for " + scenario + " in " + level.dimension().location());
        ServerSubLevel body = built.body();
        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        return body;
    }

    /** Supported Sable teleport plus the same pose/bounds refresh the production placeCopy step performs. */
    private static void teleportTo(GameTestHelper helper, ServerSubLevel body, Vector3d target) {
        var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
        helper.assertTrue(handle != null && handle.isValid(), "the body has a valid handle to teleport");
        handle.teleport(target, new org.joml.Quaterniond(body.logicalPose().orientation()));
        body.updateBoundingBox();
        body.forceUpdateGlobalBounds();
        body.updateLastPose();
    }

    private static String id(dev.ryanhcode.sable.sublevel.SubLevel resolved) {
        return resolved == null ? "none" : String.valueOf(resolved.getUniqueId());
    }

    private static void releaseAndRemove(ServerSubLevelContainer container, ServerSubLevel body) {
        container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        if (container.getSubLevel(body.getUniqueId()) == body) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }
}
