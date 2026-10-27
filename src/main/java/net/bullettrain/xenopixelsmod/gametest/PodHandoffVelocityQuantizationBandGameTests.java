package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.BlockPos;
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

import java.util.Locale;

/**
 * Falsifies or firms one arithmetic prediction of the binary32 quantization model, using two fixtures that the
 * model says must land in the SAME rounding band. Assigned read-only-by-Codex on 2026-10-20 08:22 as a test-only
 * lane; this file touches no production code and no other test.
 *
 * <p>The model under test: a body's native axis coordinate is stored as binary32, and the integrator advances it
 * in substeps whose count per tick is s, so one tick moves an axis by exactly {@code s * k} ulps of that axis's own
 * coordinate, where {@code k = round(v * (0.05 / s) / ulp)}. Fitting the runs already on record gives s = 36, and a
 * scan of every integer s in 1..400 against all seven measured points leaves s = 36 as the ONLY survivor. That is
 * an arithmetic fit, not a measurement of a native type or of a solver's substep count.
 *
 * <p>The prediction being tested here was NOT used in that fit and is therefore falsifiable rather than fitted: at
 * X = 100000.5 the binary32 ulp is 2^-7 = 1/128, so a tick moves the axis by 36/128 = 0.28125 blocks whenever
 * {@code round(v / 5.625) == 1}, i.e. for every speed in [2.8125, 8.4375). vx=4 and vx=8 sit at 0.711 and 1.422 of
 * a step, on opposite sides of the rounding point, so they MUST print the identical per-tick displacement even
 * though the true speeds are in a 1:2 ratio. Two readings that differ here refute the rounding rule; two readings
 * that are equal but not 0.28125 refute the step count. The already-measured vx=5 case (0.28125 exactly, and pinned
 * at X=200000.5 where the band's ulp doubles) is what makes this band-edge check worth running: it also refutes
 * truncate-toward-zero (which predicts a pin at vx=5) and ceil (which predicts motion at vx=2).
 *
 * <p>Both capsules are built directly at their measured coordinate, as the vx=5 and vx=20 cases are, so no teleport
 * path is involved. Each fixture takes its own Z, which is the only component the observed plot-slot key uses
 * alongside X; production slot semantics are unchanged and no vertical (Y-offset) sharing is used.
 *
 * <p>Scope limit: two free capsules, one axis, headless End level, no controller, no thruster, no rider, no
 * sub-level occupancy. A match is evidence that this arithmetic describes the observed integrator over this
 * coordinate range; it is not proof of native float storage, not a statement about the solver internals, and not a
 * live-handoff claim.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodHandoffVelocityQuantizationBandGameTests {
    /** The chart-scale axis magnitude at which the observed pin appears; both fixtures share its binary32 band. */
    private static final double COORDINATE_X = 100_000.5;
    private static final double COORDINATE_Y = 256.5;
    /** Distinct plot slots, chosen so neither collides with the fixtures already in the shared velocity probe. */
    private static final double SLOT_Z_LOWER_SPEED = 650.5;
    private static final double SLOT_Z_HIGHER_SPEED = 750.5;
    private static final int WINDOW_TICKS = 6;
    /** A public Sable ticket type keeps the capsule resident while it translates away from its build chunk. */
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;
    /** binary32 ulp across [2^16, 2^17), which contains COORDINATE_X. */
    private static final double BAND_ULP = 1.0 / 128.0;
    /** The model's predicted displacement for every speed in [2.8125, 8.4375): 36 substeps times one ulp. */
    private static final double PREDICTED_PER_TICK = 36 * BAND_ULP;
    /** Doubles on this scale are exact multiples of 1/128, so a match is expected to be bit-exact. */
    private static final double MATCH_TOLERANCE = 1.0E-9;

    private PodHandoffVelocityQuantizationBandGameTests() {}

    /** Lower half of the band: 4 / 5.625 = 0.711 rounds up to one ulp per substep. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void lowerBandEdgeAtChartCoordinateMovesThePredictedStep(GameTestHelper helper) {
        runBandProbe(helper, SLOT_Z_LOWER_SPEED, 4.0);
    }

    /** Upper half of the band: 8 / 5.625 = 1.422 rounds down to one ulp per substep, so the answer must be equal. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void upperBandEdgeAtChartCoordinateMovesTheSameStep(GameTestHelper helper) {
        runBandProbe(helper, SLOT_Z_HIGHER_SPEED, 8.0);
    }

    private static void runBandProbe(GameTestHelper helper, double zCentre, double appliedX) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(end);
        Vec3 centre = new Vec3(COORDINATE_X, COORDINATE_Y, zCentre);
        BlockPos centreBlock = BlockPos.containing(centre);
        end.getChunk(centreBlock.getX() >> 4, centreBlock.getZ() >> 4);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(end,
                new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel capsule builds in " + end.dimension().location());
        ServerSubLevel body = built.body();
        RigidBodyHandle handle = RigidBodyHandle.of(body);
        helper.assertTrue(handle != null && handle.isValid(),
                "the capsule has a valid rigid body to measure (handle " + handle + ")");

        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        Vector3d startPose = body.logicalPose().position();
        Vector3d start = new Vector3d(startPose.x(), startPose.y(), startPose.z());
        XenoPixelsMod.LOGGER.info("QuantizationBand fixture: scenario={} hostDimension={} logicalPosition={} "
                        + "holdingPlotCenterBlock={} predictedPerTick={} bandUlp={}",
                scenario(appliedX), body.getLevel().dimension().location(), start,
                body.getPlot().getCenterBlock(), fmt(PREDICTED_PER_TICK), fmt(BAND_ULP));
        handle.addLinearAndAngularVelocity(new Vector3d(appliedX, 0.0, 0.0), new Vector3d());
        Vector3d applied = handle.getLinearVelocity(new Vector3d());

        Vector3d[] positions = new Vector3d[WINDOW_TICKS + 1];
        double[] speeds = new double[WINDOW_TICKS + 1];
        positions[0] = start;
        speeds[0] = applied.x;
        sampleEachTick(helper, container, body, handle, positions, speeds, applied.x, appliedX, scenario(appliedX), 1);
    }

    /** Walks one tick at a time so a mid-window change in the step size is visible instead of averaged away. */
    private static void sampleEachTick(GameTestHelper helper, ServerSubLevelContainer container, ServerSubLevel body,
                                       RigidBodyHandle handle, Vector3d[] positions, double[] speeds,
                                       double appliedX, double velocity, String scenario, int tick) {
        helper.runAfterDelay(1, () -> {
            if (body.isRemoved()) {
                report(helper, container, body, positions, speeds, appliedX, velocity, scenario,
                        "the capsule was removed at tick " + tick + " before the window closed");
                return;
            }
            var pose = body.logicalPose().position();
            positions[tick] = new Vector3d(pose.x(), pose.y(), pose.z());
            speeds[tick] = handle.getLinearVelocity(new Vector3d()).x;
            if (tick < WINDOW_TICKS) {
                sampleEachTick(helper, container, body, handle, positions, speeds, appliedX, velocity, scenario,
                        tick + 1);
            } else {
                report(helper, container, body, positions, speeds, appliedX, velocity, scenario, null);
            }
        });
    }

    private static void report(GameTestHelper helper, ServerSubLevelContainer container, ServerSubLevel body,
                               Vector3d[] positions, double[] speeds, double appliedX, double velocity,
                               String scenario, String earlyExit) {
        StringBuilder table = new StringBuilder();
        for (int tick = 0; tick <= WINDOW_TICKS; tick++) {
            if (positions[tick] == null) break;
            table.append(" [tick ").append(tick).append(" x=").append(fmt(positions[tick].x))
                    .append(" y=").append(fmt(positions[tick].y)).append(" z=").append(fmt(positions[tick].z))
                    .append(" vX=").append(fmt(speeds[tick])).append(']');
        }
        if (earlyExit != null) {
            XenoPixelsMod.LOGGER.warn("QuantizationBand probe at {}: INCONCLUSIVE - {}. readings={}",
                    scenario, earlyExit, table);
            releaseAndRemove(container, body);
            helper.assertTrue(false, "quantization band probe: " + earlyExit + "; the band stays NOT VERIFIED");
            return;
        }

        double[] steps = new double[WINDOW_TICKS];
        for (int tick = 0; tick < WINDOW_TICKS; tick++) {
            steps[tick] = positions[tick + 1].x - positions[tick].x;
        }
        boolean stepIsConstant = true;
        double sum = 0.0;
        for (double step : steps) {
            sum += step;
            if (Math.abs(step - steps[0]) > MATCH_TOLERANCE) stepIsConstant = false;
        }
        double meanPerTick = sum / WINDOW_TICKS;
        boolean matchesPrediction = Math.abs(meanPerTick - PREDICTED_PER_TICK) <= MATCH_TOLERANCE;
        String verdict;
        if (!stepIsConstant) {
            verdict = "REFUTES the quantization model (the per-tick step is not a constant, so the axis is not "
                    + "advancing by a fixed whole number of ulps)";
        } else if (matchesPrediction) {
            verdict = "MATCHES the model at " + fmt(steps[0] / BAND_ULP) + " ulps/tick";
        } else {
            verdict = "REFUTES the fitted step count: constant at " + fmt(steps[0] / BAND_ULP) + " ulps/tick = "
                    + fmt(steps[0]) + " blocks, but the model predicted " + fmt(PREDICTED_PER_TICK);
        }

        XenoPixelsMod.LOGGER.info("QuantizationBand probe at {}: applied vX={} and the body reported vX={} at tick 0;"
                        + " per-tick X steps{} = mean {} blocks ({} ulps of band ulp {}); predicted {};"
                        + " constant step across the window={} -> {}",
                scenario, fmt(velocity), fmt(appliedX), table, fmt(meanPerTick), fmt(meanPerTick / BAND_ULP),
                fmt(BAND_ULP), fmt(PREDICTED_PER_TICK), stepIsConstant, verdict);

        releaseAndRemove(container, body);
        helper.assertTrue(body.isRemoved(), "probe cleanup removed the capsule");
        helper.assertTrue(stepIsConstant && matchesPrediction,
                "the quantization model predicts exactly " + fmt(PREDICTED_PER_TICK) + " blocks/tick for vX="
                        + fmt(velocity) + " at X=" + COORDINATE_X + ", and the body measured mean "
                        + fmt(meanPerTick) + " with a " + (stepIsConstant ? "constant" : "varying") + " step; "
                        + verdict);
        helper.succeed();
    }

    private static void releaseAndRemove(ServerSubLevelContainer container, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        if (container.getSubLevel(body.getUniqueId()) == body) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }

    private static String scenario(double velocity) {
        return "the_end x=100000.5 vx=" + (int) velocity;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }
}
