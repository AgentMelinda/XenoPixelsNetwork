package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.physics.SubLevelDimensionDataMapper;
import net.bullettrain.xenopixelsmod.space.pod.ClassicEggPodHull;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/** Disposable runtime probe for Sable serialization across dimension height layouts; not a rider-transfer proof. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceSubLevelCopyGameTests {
    private static final ResourceKey<net.minecraft.world.level.Level> VOIDSEA = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea"));
    private static final ResourceKey<net.minecraft.world.level.Level> END = net.minecraft.world.level.Level.END;

    private SpaceSubLevelCopyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void sableBodyCanBeLoadedIntoVoidseaWithHeightRemapping(GameTestHelper helper) {
        run(helper, VOIDSEA);
    }

    /** Isolates Sable's cross-parent-dimension load path from the production Voidsea bootstrap. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void sableBodyCanBeLoadedIntoAnotherDimensionWithHeightRemapping(GameTestHelper helper) {
        run(helper, END);
    }

    /** Builds the actual fixed P15 profile in a dimension with a different height layout and checks stable identity. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void classicEggHullBuildsWithStableIdentityInEnd(GameTestHelper helper) {
        ServerLevel destination = helper.getLevel().getServer().getLevel(END);
        helper.assertTrue(destination != null, "End dimension is available for the P15 hull build test");
        UUID podId = UUID.randomUUID();
        ClassicEggPodHull.BuildJob job = ClassicEggPodHull.start(destination,
                new Vector3d(64.5, 64.5, 64.5), podId);
        int attempts = 0;
        while (!job.done() && !job.failed() && attempts++ < 20_000) {
            // This is an explicit same-callback handoff transaction, not a shared-budget tick slice.
            // The two-argument overload interprets 0 ms as exhausted and intentionally defers finalization.
            if (!job.step(128)) Thread.yield();
        }
        ServerSubLevel body = job.body();
        helper.assertTrue(job.done() && !job.failed() && body != null,
                "fixed pod hull completion state: done=" + job.done() + ", failed=" + job.failed()
                        + ", attempts=" + attempts + ", installed=" + job.installed()
                        + ", body=" + (body == null ? "null" : body.getUniqueId() + "/removed=" + body.isRemoved()));
        helper.assertTrue(body.getPlot().getBoundingBox().volume() > 0,
                "the fixed hull created a non-empty Sable plot");
        var data = body.getUserDataTag();
        helper.assertTrue(data != null && data.hasUUID("xenopixelsmod:space_pod_id")
                        && data.getUUID("xenopixelsmod:space_pod_id").equals(podId),
                "stable pod identity is stored with the Sable body");
        helper.assertTrue("xenopixelsmod:pod/hull/classic_egg".equals(data.getString("xenopixelsmod:space_pod_hull"))
                        && data.getInt("xenopixelsmod:space_pod_schema") == 1
                        && data.getBoolean("xenopixelsmod:space_pod_fixed_hull"),
                "the typed hull ID, schema version, and immutable-v1 marker are stored");
        helper.assertTrue("xenopixelsmod:pod/cockpit/bubble".equals(data.getString("xenopixelsmod:space_pod_cockpit"))
                        && "xenopixelsmod:pod/thruster/triple_jet".equals(data.getString("xenopixelsmod:space_pod_thruster_skirt"))
                        && "xenopixelsmod:pod/trim/none".equals(data.getString("xenopixelsmod:space_pod_trim")),
                "the selected pod slot IDs are persisted with the body");
        helper.assertTrue(data.getInt("xenopixelsmod:space_pod_primary_argb")
                        == (0xFF000000 | net.bullettrain.xenopixelsmod.fx.aura.AuraPalette.nearest(0xFFFFFF))
                        && data.getInt("xenopixelsmod:space_pod_secondary_argb")
                        == (0xFF000000 | net.bullettrain.xenopixelsmod.fx.aura.AuraPalette.nearest(0xB8C5D6))
                        && data.getInt("xenopixelsmod:space_pod_accent_argb")
                        == (0xFF000000 | net.bullettrain.xenopixelsmod.fx.aura.AuraPalette.nearest(0x4ED7FF)),
                "the selected pod paint colors are persisted with the body");
        helper.runAfterDelay(2, () -> {
            try {
                var container = SubLevelContainer.getContainer(destination);
                helper.assertTrue(!body.isRemoved() && container.getSubLevel(body.getUniqueId()) == body,
                        "temporary handoff force-load ticket keeps the completed hull through a tick boundary");
                XenoPixelsMod.LOGGER.info("P15 Classic Egg GameTest PASS: ticketed Sable hull survived a tick and retained pod id {}", podId);
                helper.succeed();
            } finally {
                job.releaseHandoffTicket();
                var container = SubLevelContainer.getContainer(destination);
                if (!body.isRemoved() && container.getSubLevel(body.getUniqueId()) == body)
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
        });
    }

    private static void run(GameTestHelper helper, ResourceKey<net.minecraft.world.level.Level> destinationKey) {
        // /test run on a normal server reports only to its command source, so a closed RCON session loses the
        // result; these lines put the outcome and the phase it stopped in into latest.log either way.
        try {
            runCopy(helper, destinationKey);
        } catch (RuntimeException | Error failure) {
            XenoPixelsMod.LOGGER.error("SpaceSubLevelCopy GameTest FAIL: phase={}", CURRENT_PHASE.get(), failure);
            throw failure;
        } finally {
            CURRENT_PHASE.remove();
        }
        XenoPixelsMod.LOGGER.info("SpaceSubLevelCopy GameTest PASS: one-voxel cap-enabled build, serialize, destination fullyLoad and source removal ({})", destinationKey.location());
        helper.succeed();
    }

    /** Last phase entered by the running test, read by the failure log. Server thread only. */
    private static final ThreadLocal<String> CURRENT_PHASE = new ThreadLocal<>();

    private static void enter(String phase) {
        CURRENT_PHASE.set(phase);
        XenoPixelsMod.LOGGER.info("SpaceSubLevelCopy GameTest phase: {}", phase);
    }

    private static void runCopy(GameTestHelper helper, ResourceKey<net.minecraft.world.level.Level> destinationKey) {
        enter("destination-lookup");
        ServerLevel sourceLevel = helper.getLevel();
        ServerLevel destinationLevel = sourceLevel.getServer().getLevel(destinationKey);
        helper.assertTrue(destinationLevel != null, "destination level " + destinationKey.location() + " is available to the GameTest server");

        enter("build");
        var originalConfig = XenoSpaceConfig.snapshot();
        var cappedConfig = XenoSpaceConfig.snapshot();
        cappedConfig.generatedBodyBuildBudgetEnabled = true;
        cappedConfig.generatedBodyBuildMillisPerTick = 8.0;
        XenoSpaceConfig.apply(cappedConfig);
        PlotBodyBuilder.Result sourceBuild;
        try {
            helper.assertTrue(XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0,
                    "the synchronous builder regression runs with the wall-time cap enabled");
            double plotX = destinationKey.equals(END) ? 32.5 : 0.5;
            sourceBuild = PlotBodyBuilder.build(sourceLevel,
                    new Vector3d(plotX, 64.5, 0.5), 1,
                    (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
        } finally {
            XenoSpaceConfig.apply(originalConfig);
        }
        helper.assertTrue(sourceBuild != null && sourceBuild.body() != null,
                "the one-voxel Sable source body builds");

        ServerSubLevel source = sourceBuild.body();
        var sourceContainer = SubLevelContainer.getContainer(sourceLevel);
        var destinationContainer = SubLevelContainer.getContainer(destinationLevel);
        ServerSubLevel copy = null;
        try {
            // Capture the full tag while the source plot and its chunks are still attached.
            enter("serialize");
            SubLevelData data = SubLevelSerializer.toData(source, List.of());
            var serializedChunks = data.fullTag().getCompound("plot").getCompound("chunks");
            List<String> serializedSectionKeys = serializedChunks.getAllKeys().stream()
                    .flatMap(chunkKey -> serializedChunks.getCompound(chunkKey)
                            .getCompound("sections").getAllKeys().stream())
                    .toList();
            XenoPixelsMod.LOGGER.info("SpaceSubLevelCopy dimensions: source={} minY={} maxY={} sectionCount={}; " +
                            "destination={} minY={} maxY={} sectionCount={}; serializedSectionKeys={}",
                    sourceLevel.dimension().location(), sourceLevel.getMinBuildHeight(), sourceLevel.getMaxBuildHeight(),
                    sourceLevel.getSectionsCount(), destinationLevel.dimension().location(),
                    destinationLevel.getMinBuildHeight(), destinationLevel.getMaxBuildHeight(),
                    destinationLevel.getSectionsCount(), serializedSectionKeys);
            enter("map-sections");
            SubLevelData destinationData = SubLevelDimensionDataMapper.mapToDestination(data, sourceLevel, destinationLevel);
            helper.assertTrue(sourceContainer.getSubLevel(source.getUniqueId()) == source,
                    "source stays registered until destination load succeeds");
            enter("fully-load");
            copy = SubLevelSerializer.fullyLoad(destinationLevel, destinationData);
            enter("assert-copy");
            helper.assertTrue(copy != null, "Sable fullyLoad returns the destination body");
            helper.assertTrue(copy.getUniqueId().equals(source.getUniqueId()),
                    "destination load retains the serialized body identity");
            helper.assertTrue(destinationContainer.getSubLevel(copy.getUniqueId()) == copy,
                    "the destination container resolves the loaded body by its UUID");
            helper.assertTrue(copy.getPlot().getBoundingBox().volume() > 0,
                    "the destination plot has non-empty bounds after load");
            enter("remove-source");
            sourceContainer.removeSubLevel(source, SubLevelRemovalReason.REMOVED);
            helper.assertTrue(sourceContainer.getSubLevel(source.getUniqueId()) == null,
                    "source is removed only after destination load succeeds");
        } finally {
            // Not a phase: a failure above must still be reported with the phase that raised it.
            if (copy != null && !copy.isRemoved()
                    && destinationContainer.getSubLevel(copy.getUniqueId()) == copy)
                destinationContainer.removeSubLevel(copy, SubLevelRemovalReason.REMOVED);
            if (!source.isRemoved() && sourceContainer.getSubLevel(source.getUniqueId()) == source)
                sourceContainer.removeSubLevel(source, SubLevelRemovalReason.REMOVED);
        }
    }
}
