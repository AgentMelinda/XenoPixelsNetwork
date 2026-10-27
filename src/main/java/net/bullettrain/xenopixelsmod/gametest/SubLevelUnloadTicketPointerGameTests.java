package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
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
 * Decides whether removing a body with {@link SubLevelRemovalReason#UNLOADED} refreshes the saved pointer that a
 * force-load ticket needs to reload it.
 *
 * <p>Bytecode of {@code SubLevelTicketLoadingSystem.onSubLevelRemoved} (pinned Sable 2.0.3, jar
 * {@code T9PomCSv-1L6XJqnY.jar}, sha256 da6c3b66238586603d1dcaa2afb012d36815fbce0a2d5938fbb2936701d42279) reads
 * {@code allTickets} with the {@code ServerSubLevel} <em>instance</em>, although that map is keyed by
 * {@code java.util.UUID} (see {@code ServerSubLevelContainer.addForceLoadTicket}, which computes
 * {@code body.getUniqueId()} for its {@code computeIfAbsent}). The lookup therefore misses, the null branch skips
 * {@code info.setPointer(body.getLastSerializationPointer())}, and the ticket keeps its old pointer. The sibling
 * REMOVED branch removes by {@code getUniqueId()} and is unaffected.
 * {@link net.bullettrain.xenopixelsmod.mixin.compat.sable.SableTicketUnloadKeyMixin} substitutes the UUID via
 * {@code @ModifyArg}, gated on {@link XenoSpaceConfig#sableTicketUnloadKeyFix()}.
 *
 * <p>This test is the runtime half of that claim. It plants a pointer on the body only, unloads the body, and
 * reports what the ticket entry holds afterwards. Both branches can fail, so neither outcome is a vacuous pass:
 * with the flag off the entry must be unchanged (the defect, characterized), with it on the entry must hold the
 * planted pointer (the fix, proven). Running the same test under each setting is the evidence that decides the
 * default. The flag is read as configured; nothing here writes config or changes a default.
 *
 * <p>Scope limit: this covers the observer's pointer write for one hand-built body in the GameTest level. It does
 * not show when a purged or refreshed pointer reaches disk (no {@code SubLevelTicketsSavedData.setDirty()} call
 * appears in either removal branch), and it is not a pod-transfer claim.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubLevelUnloadTicketPointerGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;
    /** Chosen far outside any holding-chunk region so it cannot already be the entry's pointer. */
    private static final int PLANTED_CHUNK = -999_999;

    private SubLevelUnloadTicketPointerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void unloadedRemovalEitherRefreshesOrKeepsTheTicketPointer(GameTestHelper helper) {
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
        // getAllTickets is an unmodifiable view over the live map, so this is the same entry the observer would
        // mutate rather than a snapshot of it.
        SubLevelTicketInfo info = container.getAllTickets().get(body.getUniqueId());
        helper.assertTrue(info != null,
                "addForceLoadTicket left no entry under the body UUID " + body.getUniqueId());

        GlobalSavedSubLevelPointer before = info.getPointer();
        GlobalSavedSubLevelPointer planted =
                new GlobalSavedSubLevelPointer(new ChunkPos(PLANTED_CHUNK, PLANTED_CHUNK), (short) 0, (short) 0);
        helper.assertTrue(!Objects.equals(before, planted),
                "the ticket entry already held the planted pointer " + planted + ", so a write would be invisible");
        body.setLastSerializationPointer(planted);
        helper.assertTrue(Objects.equals(body.getLastSerializationPointer(), planted),
                "setLastSerializationPointer did not stick; the body reads " + body.getLastSerializationPointer());

        container.removeSubLevel(body, SubLevelRemovalReason.UNLOADED);

        GlobalSavedSubLevelPointer after = info.getPointer();
        boolean fixActive = XenoSpaceConfig.sableTicketUnloadKeyFix();
        String observation = "flag=" + fixActive + " entry pointer before=" + before + " after=" + after
                + " planted on body=" + planted;

        if (fixActive) {
            if (!Objects.equals(after, planted)) {
                XenoPixelsMod.LOGGER.error("Unload ticket pointer test FAILED with the fix on: {}", observation);
                container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
                helper.assertTrue(false, "the mixin was on, so the UUID lookup should have hit and copied the body's"
                        + " pointer onto the ticket entry, but the entry reads " + after + " (" + observation + ")");
                return;
            }
            XenoPixelsMod.LOGGER.info("Unload ticket pointer test: the fix works, the unloaded body's ticket entry"
                    + " now carries the saved pointer. {}", observation);
        } else {
            if (Objects.equals(after, planted)) {
                XenoPixelsMod.LOGGER.error("Unload ticket pointer test: the pointer was refreshed even though"
                        + " sableTicketUnloadKeyFix is off, so the traced wrong-key lookup is not what runs: {}",
                        observation);
                container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
                helper.assertTrue(false, "the flag is off but the entry took the planted pointer, so the bytecode"
                        + " model behind this test is wrong and must be re-traced (" + observation + ")");
                return;
            }
            XenoPixelsMod.LOGGER.warn("Unload ticket pointer test: REPRODUCED the defect under the false default."
                    + " The unloaded body's force-load entry kept its old pointer instead of the pointer stored on"
                    + " the body, so a reload of this ticket would have nothing to load from. {}", observation);
        }

        // The entry itself is left in place: keeping a ticket for an unregistered body is exactly what the unload
        // path does, and the pointer read above is the observable. Only the keep-alive ticket is dropped.
        container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        helper.succeed();
    }
}
