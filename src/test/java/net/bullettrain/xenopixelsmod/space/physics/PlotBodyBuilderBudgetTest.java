package net.bullettrain.xenopixelsmod.space.physics;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBodyBuilderBudgetTest {
    @Test void finalizeDeferralAppliesOnlyToNoninitialNonfinalBatches() {
        assertFalse(PlotBodyBuilder.shouldDeferFinalize(false, 500, true));
        assertFalse(PlotBodyBuilder.shouldDeferFinalize(true, 0, true));
        assertTrue(PlotBodyBuilder.shouldDeferFinalize(true, 500, true));
        assertFalse(PlotBodyBuilder.shouldDeferFinalize(true, 500, false));
    }

    @Test void positiveTickBudgetBatchesPersistentAndOnDemandAtomicBodies() {
        assertTrue(PlotBodyBuilder.shouldBatchAssembly(true, 5_000));
        assertFalse(PlotBodyBuilder.shouldBatchAssembly(true, 0));
        assertFalse(PlotBodyBuilder.shouldBatchAssembly(false, 5_000));
    }

    @Test void timeSlicedWriterAlwaysStartsOneVoxelAndStopsBetweenCallbacks() {
        assertFalse(PlotBodyBuilder.shouldStopTimeSlicedStep(1.0, 4.0, 0));
        assertTrue(PlotBodyBuilder.shouldStopTimeSlicedStep(1.0, 4.0, 1));
        assertFalse(PlotBodyBuilder.shouldStopTimeSlicedStep(0.0, 100.0, 1));
        assertFalse(PlotBodyBuilder.shouldStopTimeSlicedStep(1.0, 0.5, 1));
    }

    @Test void finishWaitsUntilTheSharedTimeAllowanceRemains() {
        assertFalse(PlotBodyBuilder.shouldDeferFinish(0.0, 0.0, 100.0, 1), "disabled wall-time cap stays unbudgeted");
        assertTrue(PlotBodyBuilder.shouldDeferFinish(8.0, 0.0, 0.0, 0), "exhausted allowance defers finish");
        assertTrue(PlotBodyBuilder.shouldDeferFinish(8.0, 0.001, 0.002, 1), "voxel work that consumed the slice defers finish");
        assertFalse(PlotBodyBuilder.shouldDeferFinish(8.0, 0.5, 0.2, 1), "finish may start with time left");
        assertEquals(Double.MAX_VALUE, PlotBodyBuilder.UNBOUNDED_MILLIS_BUDGET,
                "the no-budget step overload must keep its finite sentinel");
        assertFalse(PlotBodyBuilder.shouldDeferFinish(8.0, PlotBodyBuilder.UNBOUNDED_MILLIS_BUDGET, 1_000_000.0, 1),
                "the legacy step overload's explicit unbounded sentinel must retain its synchronous finish behavior");
        assertFalse(PlotBodyBuilder.shouldStopTimeSlicedStep(PlotBodyBuilder.UNBOUNDED_MILLIS_BUDGET, 1_000_000.0, 1),
                "the legacy step overload's sentinel must not stop between voxel callbacks");
    }

    @Test void maximumChartShellSelectsExactWriterWhenItsGroupExceedsOwnerShare() {
        int radius = 160;
        var filled = PlotBodyBuilder.fill(radius,
                PlotBodyBuilder.shell(radius, Blocks.STONE.defaultBlockState()));
        int maxAtomicGroup = PlotBodyBuilder.maxGroupedVoxels(filled.grouped());
        assertTrue(maxAtomicGroup > 0);
        int ownerShare = SpaceBuildWorkBudget.ownerShareFor(2_000, 0, SpaceBuildWorkBudget.Owner.CHART_PLANET);
        assertTrue(maxAtomicGroup > ownerShare,
                "test fixture must exercise the exact-writer fallback; group=" + maxAtomicGroup + ", share=" + ownerShare);
        assertTrue(SpaceBuildWorkBudget.requiresExactWriter(maxAtomicGroup, 2_000, ownerShare),
                "radius-160 shell must avoid a plot.load group that exceeds the per-tick owner budget");
    }

    @Test void directVoxelBatchesNeverExceedBudgetAndCoverEveryVoxelOnce() {
        int totalVoxels = 13_622;
        int budget = 2_000;
        int cursor = 0;
        List<PlotBodyBuilder.VoxelRange> ranges = new ArrayList<>();

        while (cursor < totalVoxels) {
            var range = PlotBodyBuilder.nextVoxelRange(cursor, totalVoxels, budget);
            ranges.add(range);
            assertTrue(range.size() <= budget, "a chunk-column boundary must not exceed the voxel budget");
            assertEquals(cursor, range.fromInclusive(), "batches must be contiguous");
            cursor = range.toExclusive();
        }

        assertEquals(totalVoxels, cursor);
        assertEquals(7, ranges.size());
        assertEquals(1_622, ranges.getLast().size());
        assertEquals(totalVoxels, PlotBodyBuilder.nextVoxelRange(1, totalVoxels, Integer.MAX_VALUE).toExclusive(),
                "a very large later slice must not overflow a progressed cursor");
    }

    @Test void directVoxelBatchRejectsInvalidCursorAndBudget() {
        assertThrows(IllegalArgumentException.class, () -> PlotBodyBuilder.nextVoxelRange(-1, 5, 2));
        assertThrows(IllegalArgumentException.class, () -> PlotBodyBuilder.nextVoxelRange(0, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> PlotBodyBuilder.nextVoxelRange(6, 5, 1));
    }

    @Test void cancelledVoxelFillStopsBeforeEvaluatingTheNextRow() {
        AtomicInteger rowsChecked = new AtomicInteger();
        AtomicInteger voxelCalls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> PlotBodyBuilder.fill(2, (x, y, z) -> {
            voxelCalls.incrementAndGet();
            return Blocks.AIR.defaultBlockState();
        }, () -> rowsChecked.incrementAndGet() > 2));
        assertEquals(3, rowsChecked.get());
        assertEquals(10, voxelCalls.get(), "two X/Z rows complete, and the third row is never evaluated");
    }

    @Test void wholeColumnScheduleUsesExactWriterWheneverAnyBatchWouldExceedItsVoxelBudget() {
        var fits = List.of(new int[7], new int[5], new int[4]); // 6 + 4, then 3
        assertTrue(PlotBodyBuilder.atomicBatchesFit(fits, 0, 0, 10));

        var greedyOvershoot = List.of(new int[7], new int[6]); // 6 + 5 in the first greedy batch
        assertFalse(PlotBodyBuilder.atomicBatchesFit(greedyOvershoot, 0, 0, 10));

        var framePrefixOvershoot = List.of(new int[4], new int[4], new int[2]);
        assertFalse(PlotBodyBuilder.atomicBatchesFit(framePrefixOvershoot, 0, 2, 5));
        assertTrue(PlotBodyBuilder.atomicBatchesFit(framePrefixOvershoot, 0, 2, 6));
    }

    @Test void wholeColumnBudgetCheckHonoursCursorAndInvalidInput() {
        var columns = List.of(new int[8], new int[8], new int[3]);
        assertTrue(PlotBodyBuilder.atomicBatchesFit(columns, 1, 0, 7));
        assertFalse(PlotBodyBuilder.atomicBatchesFit(columns, 1, 0, 6));
        assertFalse(PlotBodyBuilder.atomicBatchesFit(columns, -1, 0, 10));
        assertTrue(PlotBodyBuilder.atomicBatchesFit(columns, 0, 0, 0)); // zero remains the explicit unbudgeted path
    }

    @Test void chunkHolderBoundsDeferralIsScopedNestedAndExceptionSafe() {
        var original = XenoSpaceConfig.snapshot();
        var enabled = XenoSpaceConfig.snapshot();
        enabled.planetBuildCoalesceChunkBounds = true;
        XenoSpaceConfig.apply(enabled);
        try {
            assertFalse(PlotBodyBuilder.deferChunkHolderBounds());
            PlotBodyBuilder.withChunkHolderBoundsDeferred(() -> {
                assertTrue(PlotBodyBuilder.deferChunkHolderBounds());
                PlotBodyBuilder.withChunkHolderBoundsDeferred(() -> assertTrue(PlotBodyBuilder.deferChunkHolderBounds()));
                assertTrue(PlotBodyBuilder.deferChunkHolderBounds());
            });
            assertFalse(PlotBodyBuilder.deferChunkHolderBounds());

            assertThrows(IllegalStateException.class, () -> PlotBodyBuilder.withChunkHolderBoundsDeferred(() -> {
                assertTrue(PlotBodyBuilder.deferChunkHolderBounds());
                throw new IllegalStateException("test");
            }));
            assertFalse(PlotBodyBuilder.deferChunkHolderBounds());
        } finally {
            XenoSpaceConfig.apply(original);
        }
    }
}
