package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Checks that removing a body with {@link SubLevelRemovalReason#REMOVED} drops its force-load ticket entry, which
 * is the assumption {@code PodTransferTransaction.unwind} rests on when it removes a half-transferred copy without
 * releasing that copy's ticket first.
 *
 * <p>Bytecode of {@code SubLevelTicketLoadingSystem.onSubLevelRemoved} (pinned Sable 2.0.3, jar
 * {@code T9PomCSv-1L6XJqnY.jar}, sha256 da6c3b66238586603d1dcaa2afb012d36815fbce0a2d5938fbb2936701d42279) shows the
 * REMOVED branch calling {@code allTickets.remove(body.getUniqueId())} and then {@code activeTickets.remove(body)}.
 * The UUID-keyed removal is what lets the rollback path remove a half-transferred copy without releasing that
 * copy's ticket first; if the entry instead survived, a dead body's ticket would outlive its plot slot and a later
 * reload could collide with whatever took that slot.
 *
 * <p>Two independent observables, both required, so the test cannot pass by re-keying rather than deleting: the
 * entry must be gone under the body's UUID, and NO entry left in the container may carry the pointer planted on the
 * body beforehand. The planted pointer also records what REMOVED gives up: unlike the UNLOADED path, a REMOVED body
 * keeps nothing for a later reload, which is correct for a deletion and is the reason the two reasons differ.
 *
 * <p>Scope limits. This is the in-memory container map only ({@code getAllTickets()} is an unmodifiable view over
 * it); neither removal branch calls {@code SubLevelTicketsSavedData.setDirty()}, so when a purge reaches disk is
 * outside this test and stays open. It is one hand-built body in the GameTest level, not a pod transfer, and it
 * launches no server and no restart. It is also independent of {@code XenoSpaceConfig.sableTicketUnloadKeyFix}:
 * that mixin rewrites the argument of the single {@code Object2ObjectMap.get} call, and this branch uses
 * {@code remove}, so one run answers it for either config value.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubLevelRemovedTicketPurgeGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;
    /** Deliberately outside any holding-chunk region, so a surviving entry is unmistakably this body's. */
    private static final int PLANTED_CHUNK = -999_998;

    private SubLevelRemovedTicketPurgeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void removedRemovalDropsTheForceLoadTicketEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Vec3 centre = helper.absoluteVec(new Vec3(0.5, 2.5, 0.5));
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel body builds in " + level.dimension().location());
        ServerSubLevel body = built.body();

        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        SubLevelTicketInfo info = container.getAllTickets().get(body.getUniqueId());
        helper.assertTrue(info != null && !info.tickets().isEmpty(),
                "addForceLoadTicket left no ticket entry to purge (entry " + info + ")");
        GlobalSavedSubLevelPointer planted =
                new GlobalSavedSubLevelPointer(new ChunkPos(PLANTED_CHUNK, PLANTED_CHUNK), (short) 0, (short) 0);
        body.setLastSerializationPointer(planted);

        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);

        String observation = "body uuid=" + body.getUniqueId() + " removed=" + body.isRemoved()
                + " entry=" + container.getAllTickets().get(body.getUniqueId())
                + " entries left=" + container.getAllTickets().size();
        if (container.getAllTickets().get(body.getUniqueId()) != null) {
            XenoPixelsMod.LOGGER.error("REMOVED ticket purge test FAILED: {}", observation);
            helper.assertTrue(false, "removing with REMOVED left the force-load entry registered, so a dead body's"
                    + " ticket outlives its plot slot and PodTransferTransaction.unwind's unguarded copy removal is"
                    + " leaking (" + observation + ")");
            return;
        }
        // The entry must be gone, not moved: nothing in the container may still carry this body's planted pointer.
        SubLevelTicketInfo carrier = null;
        for (SubLevelTicketInfo survivor : container.getAllTickets().values()) {
            if (Objects.equals(survivor.getPointer(), planted)) carrier = survivor;
        }
        if (carrier != null) {
            XenoPixelsMod.LOGGER.error("REMOVED ticket purge test FAILED: the planted pointer survived under another"
                    + " key. {}", observation);
            helper.assertTrue(false, "REMOVED dropped the UUID key but left an entry holding the removed body's"
                    + " pointer " + planted + ", which is the same leak under a different name (" + observation + ")");
            return;
        }
        XenoPixelsMod.LOGGER.info("REMOVED ticket purge test: the removed body's force-load entry and its pointer"
                + " are both gone, so removing a half-transferred copy with REMOVED drops its tickets. {}",
                observation);
        helper.succeed();
    }
}
