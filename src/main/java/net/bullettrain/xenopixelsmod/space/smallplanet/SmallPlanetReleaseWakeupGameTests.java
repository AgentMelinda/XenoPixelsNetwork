package net.bullettrain.xenopixelsmod.space.smallplanet;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/** Runtime falsifier for the shared Sable release-wakeup callback; startup registration is source-checked separately. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlanetReleaseWakeupGameTests {
    private SmallPlanetReleaseWakeupGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void restoredReleasedBodyWakesAdoption(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the one-voxel Sable fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        SmallPlanetPhysicalizer.ReleaseWakeupGameTestState prior =
                SmallPlanetPhysicalizer.prepareReleaseWakeupGameTest(level, id);
        ChunkPos holdingChunk = new ChunkPos(new BlockPos(64, 64, 64));

        try {
            holding.moveToUnloaded(body, holdingChunk);
            helper.assertTrue(container.getSubLevel(id) == null,
                    "the body is absent from the live container while held");
            HoldingSubLevel stored = holding.getHoldingSubLevel(id);
            helper.assertTrue(stored != null, "the body is present in the Sable holding store");
            holding.processChanges();
            helper.assertTrue(container.getSubLevel(id) instanceof ServerSubLevel restored
                            && !restored.isRemoved(),
                    "processing holding changes restores the body through Sable's loader");
            helper.assertTrue(SmallPlanetPhysicalizer.releaseWakeupDueForGameTest(level),
                    "a restored UUID with a cached durable release decision wakes adoption after transient pending cleanup");
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
                XenoPixelsMod.LOGGER.error("Small-planet release-wakeup GameTest cleanup failed for fixture {}", id,
                        cleanupFailure);
            } finally {
                SmallPlanetPhysicalizer.finishReleaseWakeupGameTest(level, id, prior);
            }
        }
    }
}
