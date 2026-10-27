package net.bullettrain.xenopixelsmod.space.pod.transfer;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Rollback and ticket carry-over for {@link PodTransferTransaction} on a disposable one-voxel capsule. The failure
 * is injected through the package-private test hook, so these prove the transaction's own recovery and ticket
 * handling, not how Sable itself fails. Lives in this package because the hook is package-private.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodTransferRollbackGameTests {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String POD_HULL = "xenopixelsmod:space_pod_hull";
    private static final String POD_SCHEMA = "xenopixelsmod:space_pod_schema";
    /** Relative to each test's own area, so concurrent tests in one batch never share a body position. */
    private static final Vec3 CAPSULE_OFFSET = new Vec3(1.5, 3.5, 1.5);
    /** A public Sable ticket type stands in for a lifecycle ticket the pod holds before the transfer. */
    private static final SubLevelLoadingTicketType<Unit> LIFECYCLE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private PodTransferRollbackGameTests() {}

    /** A failure after the copy loaded rolls back to one pod that keeps its ticket and no transfer ticket. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void podTransferFailureAfterLoadRollsBackAndRestoresTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        UUID bodyId = source.getUniqueId();
        container.addForceLoadTicket(source, LIFECYCLE, Unit.INSTANCE);
        ServerSubLevel survivor = null;
        PodTransferTransaction.phaseHook = phase -> {
            if ("verify".equals(phase)) throw new IllegalStateException("injected verify failure (test hook)");
        };
        try {
            PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, level, source, null);
            PodTransferTransaction.phaseHook = null;
            survivor = result.destination();
            XenoPixelsMod.LOGGER.info("PodTransfer rollback GameTest: status={} phase={} reason={}",
                    result.status(), result.phase(), result.reason());
            helper.assertTrue(result.status() == PodTransferTransaction.Status.ROLLED_BACK,
                    "an injected failure after load reports ROLLED_BACK (got " + result.status() + ": " + result.reason() + ")");
            helper.assertTrue(survivor != null && !survivor.isRemoved() && container.getSubLevel(bodyId) == survivor,
                    "the reloaded source is the registered body under the original UUID");
            helper.assertTrue(countPodBodies(level, podId) == 1, "exactly one pod remains after the rollback");
            helper.assertTrue(hasLifecycleTicket(container, bodyId),
                    "the source's lifecycle ticket is back after REMOVED cleared it during the transfer");
            helper.assertTrue(!PodTransferDestination.holds(level, bodyId, podId.toString()),
                    "the rollback leaves no transfer ticket behind");
            XenoPixelsMod.LOGGER.info("PodTransfer rollback GameTest PASS: ROLLED_BACK with one pod and its ticket restored");
            helper.succeed();
        } finally {
            PodTransferTransaction.phaseHook = null;
            cleanup(level, survivor != null ? survivor : source);
        }
    }

    /** A successful transfer carries the source's tickets to the copy alongside its own transfer ticket. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void podTransferCarriesSourceTicketsToTheCopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        container.addForceLoadTicket(source, LIFECYCLE, Unit.INSTANCE);
        ServerSubLevel copy = null;
        try {
            PodTransferTransaction.Result result = PodTransferTransaction.transfer(level, level, source, null);
            copy = result.destination();
            helper.assertTrue(result.status() == PodTransferTransaction.Status.TRANSFERRED,
                    "the transfer commits (got " + result.status() + ": " + result.reason() + ")");
            helper.assertTrue(hasLifecycleTicket(container, copy.getUniqueId()),
                    "the copy keeps the lifecycle ticket the source held");
            helper.assertTrue(PodTransferDestination.holds(level, copy.getUniqueId(), podId.toString()),
                    "the copy also holds its own transfer ticket");
            PodTransferTransaction.releaseTicket(copy, podId);
            helper.assertTrue(hasLifecycleTicket(container, copy.getUniqueId()),
                    "releasing the transfer ticket leaves the carried lifecycle ticket");
            XenoPixelsMod.LOGGER.info("PodTransfer ticket-carry GameTest PASS: lifecycle and transfer tickets on the copy");
            helper.succeed();
        } finally {
            cleanup(level, copy != null ? copy : source);
        }
    }

    /** A recovery step that throws still returns a result: nothing registered means SOURCE_LOST, never an escape. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void podTransferReloadFailureDuringRecoveryReportsSourceLost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        PodTransferTransaction.Result result = null;
        PodTransferTransaction.phaseHook = phase -> {
            if ("verify".equals(phase)) throw new IllegalStateException("injected verify failure (test hook)");
        };
        PodTransferTransaction.recoveryHook = step -> {
            if ("reload-source".equals(step)) throw new IllegalStateException("injected reload failure (test hook)");
        };
        try {
            result = PodTransferTransaction.transfer(level, level, source, null);
        } finally {
            PodTransferTransaction.phaseHook = null;
            PodTransferTransaction.recoveryHook = null;
        }
        XenoPixelsMod.LOGGER.info("PodTransfer recovery-failure GameTest: status={} phase={} reason={}",
                result.status(), result.phase(), result.reason());
        helper.assertTrue(result.status() == PodTransferTransaction.Status.SOURCE_LOST && result.destination() == null,
                "a reload that throws with nothing registered is SOURCE_LOST (got " + result.status() + ")");
        helper.assertTrue("verify".equals(result.phase()), "the result names the phase that failed, not the recovery step");
        helper.assertTrue(result.reason().contains("injected verify failure") && result.reason().contains("source reload threw"),
                "the reason keeps the original failure and adds the recovery failure: " + result.reason());
        helper.assertTrue(countPodBodies(level, podId) == 0, "no untracked pod body was left behind");
        XenoPixelsMod.LOGGER.info("PodTransfer recovery-failure GameTest PASS: SOURCE_LOST returned, nothing escaped");
        helper.succeed();
    }

    /** A copy that cannot be removed during recovery is reported as the registered survivor, not reloaded over. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void podTransferCopyRemovalFailureDuringRecoveryReportsTheLiveBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID podId = UUID.randomUUID();
        ServerSubLevel source = stamp(buildBody(helper, level), podId);
        PodTransferTransaction.Result result = null;
        PodTransferTransaction.phaseHook = phase -> {
            if ("verify".equals(phase)) throw new IllegalStateException("injected verify failure (test hook)");
        };
        PodTransferTransaction.recoveryHook = step -> {
            if ("remove-copy".equals(step)) throw new IllegalStateException("injected copy removal failure (test hook)");
        };
        try {
            result = PodTransferTransaction.transfer(level, level, source, null);
            XenoPixelsMod.LOGGER.info("PodTransfer copy-removal GameTest: status={} phase={} reason={}",
                    result.status(), result.phase(), result.reason());
            helper.assertTrue(result.status() == PodTransferTransaction.Status.RECOVERY_INCOMPLETE,
                    "recovery that could not remove the copy is RECOVERY_INCOMPLETE (got " + result.status() + ")");
            helper.assertTrue(result.destination() != null && !result.destination().isRemoved(),
                    "the result names the body that is still registered");
            helper.assertTrue(countPodBodies(level, podId) == 1, "the source was not reloaded over the live copy");
            XenoPixelsMod.LOGGER.info("PodTransfer copy-removal GameTest PASS: live body reported, no duplicate");
            helper.succeed();
        } finally {
            PodTransferTransaction.phaseHook = null;
            PodTransferTransaction.recoveryHook = null;
            if (result != null) cleanup(level, result.destination());
        }
    }

    private static boolean hasLifecycleTicket(ServerSubLevelContainer container, UUID bodyId) {
        var info = container.getAllTickets().get(bodyId);
        return info != null && info.tickets().contains(new SubLevelLoadingTicket<>(LIFECYCLE, bodyId, Unit.INSTANCE));
    }

    private static ServerSubLevel buildBody(GameTestHelper helper, ServerLevel level) {
        Vec3 centre = helper.absoluteVec(CAPSULE_OFFSET);
        PlotBodyBuilder.Result result = PlotBodyBuilder.build(level, new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(result != null && result.body() != null,
                "the one-voxel Sable capsule builds in " + level.dimension().location());
        return result.body();
    }

    private static ServerSubLevel stamp(ServerSubLevel body, UUID podId) {
        CompoundTag data = body.getUserDataTag();
        if (data == null) data = new CompoundTag();
        data.putUUID(POD_ID, podId);
        data.putString(POD_HULL, "xenopixelsmod:pod/hull/classic_egg");
        data.putInt(POD_SCHEMA, 1);
        body.setUserDataTag(data);
        return body;
    }

    private static int countPodBodies(ServerLevel level, UUID podId) {
        int found = 0;
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            CompoundTag data = subLevel.getUserDataTag();
            if (data != null && data.hasUUID(POD_ID) && data.getUUID(POD_ID).equals(podId)) found++;
        }
        return found;
    }

    /** REMOVED also drops every ticket for the UUID, so this cleans tickets and body in one call. */
    private static void cleanup(ServerLevel level, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
