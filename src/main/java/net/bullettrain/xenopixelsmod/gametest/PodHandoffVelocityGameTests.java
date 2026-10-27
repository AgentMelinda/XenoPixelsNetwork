package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
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

import java.util.Locale;

/**
 * Measures what unit a Sable body's linear velocity is actually stored in, by translating a body and timing it.
 *
 * <p>The pod handoff needs this before it can be wired. A body's reported speed is fed a chart orbital velocity
 * in blocks per second, while vanilla entity motion is blocks per tick, and the two differ by twenty. Repository
 * source documents the per-second reading ({@code SpaceFlightCore} parameter docs, {@code AeroControlSurfaceTorque}
 * reasoning about blocks/s against a blocks/s cap), but documentation of a getter is not a measurement of the
 * integrator behind it. This test applies one known x velocity to a free capsule and measures how far it actually
 * goes per tick, so the answer comes from the world rather than from a comment.
 *
 * <p>The verdict band is wide on purpose: the two candidates are twenty times apart, so no plausible damping,
 * drag, or rounding can move a reading from one band into the other. Anything between the bands, and any body
 * that does not translate at all, fails the test rather than passing quietly. An inconclusive measurement is
 * exactly what this probe exists to prevent being papered over.
 *
 * <p>Scope limit: each case measures one free capsule in a headless test dimension, at one magnitude along x, with no
 * controller, no thruster and no sub-level rider attached. It is one sample of the LINEAR basis, not a general
 * statement that Sable works in blocks per second: angular velocity is injected as zero and never measured, and
 * impulse, torque and damping-coefficient units are outside this test entirely. The 2026-10-20 05:39 run
 * classified the linear basis as blocks per second at a steady-window ratio of 1.02. It does not say how a
 * controlled pod's damping behaves, and it is not a live-handoff claim. Those readings were captured at the fixture's
 * origin-coordinate case's steady-window travel repeated at 0.19753-0.19754 blocks over ticks 2..4 in runs from
 * 05:49 through 06:52. Runs at GameTest-helper world X coordinates in the millions read zero X displacement despite
 * nonzero velocity; the cause is not established, so those readings do not decide the unit basis. The origin case
 * pins horizontal coordinates near zero; separate End-level cases sample X=1,000.5, 10,000.5, and 100,000.5 to test
 * whether the result changes at chart-scale coordinates without generated Overworld terrain as a confound.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodHandoffVelocityGameTests {
    /** The area's relative Y clears terrain; X/Z are pinned near zero to isolate the large-coordinate result. */
    private static final Vec3 CAPSULE_OFFSET = new Vec3(0.5, 316.5, 0.5);
    /** The applied number is deliberately unit-free; which unit it means is the quantity under test. */
    private static final double APPLIED_X = 2.0;
    private static final int WINDOW_TICKS = 4;
    /**
     * Excluded from the verdict. The first sample can carry a contact pushout from a fixture sharing the capsule's
     * space, which moves the reported pose without touching the velocity reading. See report().
     */
    private static final int STEADY_WINDOW_START = 2;
    /** A public Sable ticket type keeps the capsule resident while it translates away from its build chunk. */
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private PodHandoffVelocityGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityUnitBasisIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        Vec3 areaPosition = helper.absoluteVec(CAPSULE_OFFSET);
        runVelocityProbe(helper, helper.getLevel(), new Vec3(0.5, areaPosition.y, 0.5), "origin");
    }

    /** Samples one chart-scale coordinate; this is a point probe, not a claim about the maximum safe range. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtLargeVoidseaCoordinateIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(100_000.5, 256.5, 0.5), "the_end x=100000.5");
    }

    /** Keeps X local while placing the same body 100k blocks away on Z, separating axis from radial distance. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityWithLargeZAndLocalXIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(0.5, 256.5, 100_000.5), "the_end x=0.5 z=100000.5");
    }

    /** Brackets the reproducible 100k result without changing float exponent or test dimension. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityJustBelowLargeCoordinateIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(99_999.5, 256.5, 50.5), "the_end x=99999.5");
    }

    /** Brackets the reproducible 100k result without changing float exponent or test dimension. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityJustAboveLargeCoordinateIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(100_001.5, 256.5, 100.5), "the_end x=100001.5");
    }

    /** Tests whether a larger per-tick displacement escapes the 100k stationary result. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtLargeCoordinateWithHigherSpeedIsMeasured(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        Vec3 centre = new Vec3(100_000.5, 256.5, 150.5);
        runVelocityProbe(helper, end, centre, centre, "the_end x=100000.5 vx=20", 20.0);
    }

    /** Samples a velocity between the pinned vx=2 and moving vx=20 observations at the same X scale. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtLargeCoordinateWithIntermediateSpeedIsMeasured(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        Vec3 centre = new Vec3(100_000.5, 256.5, 350.5);
        runVelocityProbe(helper, end, centre, centre, "the_end x=100000.5 vx=5", 5.0);
    }

    /** Tests whether the same intermediate speed's motion threshold changes with X coordinate magnitude. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtTwiceLargeCoordinateWithIntermediateSpeedIsMeasured(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        Vec3 centre = new Vec3(200_000.5, 256.5, 500.5);
        runVelocityProbe(helper, end, centre, centre, "the_end x=200000.5 vx=5", 5.0);
    }

    /** Holds logical X at the failing magnitude but measures orthogonal Z translation. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyAtLargeXMeasuresOrthogonalZVelocityRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        Vec3 centre = new Vec3(100_000.5, 256.5, 250.5);
        runVelocityProbe(helper, end, centre, centre, "the_end x=100000.5 vz=2",
                new Vector3d(0.0, 0.0, 2.0), 2);
    }

    /** Samples the million-block logical positions logged by the real-space allocator. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtProductionScaleLogicalCoordinateIsMeasuredRatherThanAssumed(
            GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(10_000_000.5, 256.5, 400.5), "the_end x=10000000.5");
    }

    /** Tests whether the integer-step quantization seen at chart scale changes with a larger displacement. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtProductionScaleWithHigherSpeedIsMeasured(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        Vec3 centre = new Vec3(10_000_000.5, 256.5, 450.5);
        runVelocityProbe(helper, end, centre, centre, "the_end x=10000000.5 vx=20", 20.0);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtTenThousandBlocksIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(10_000.5, 256.5, 100.5), "the_end x=10000.5");
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bodyVelocityAtOneThousandBlocksIsMeasuredRatherThanAssumed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(1_000.5, 256.5, 200.5), "the_end x=1000.5");
    }

    /** Separates a build-at-large-position issue from a physics issue after a supported Sable teleport. */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void originBuiltBodyVelocityAfterTeleportToLargeCoordinateIsMeasured(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server has its standard End level");
        runVelocityProbe(helper, end, new Vec3(500.5, 256.5, 500.5),
                new Vec3(100_000.5, 256.5, 300.5), "the_end origin-build then x=100000.5 teleport");
    }

    private static void runVelocityProbe(GameTestHelper helper, ServerLevel level, Vec3 capsuleCentre,
                                         String scenario) {
        runVelocityProbe(helper, level, capsuleCentre, capsuleCentre, scenario);
    }

    private static void runVelocityProbe(GameTestHelper helper, ServerLevel level, Vec3 buildCentre,
                                         Vec3 capsuleCentre, String scenario) {
        runVelocityProbe(helper, level, buildCentre, capsuleCentre, scenario, APPLIED_X);
    }

    private static void runVelocityProbe(GameTestHelper helper, ServerLevel level, Vec3 buildCentre,
                                         Vec3 capsuleCentre, String scenario, double appliedX) {
        runVelocityProbe(helper, level, buildCentre, capsuleCentre, scenario,
                new Vector3d(appliedX, 0.0, 0.0), 0);
    }

    private static void runVelocityProbe(GameTestHelper helper, ServerLevel level, Vec3 buildCentre,
                                         Vec3 capsuleCentre, String scenario, Vector3d appliedVelocity,
                                         int measuredAxis) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        var buildBlock = net.minecraft.core.BlockPos.containing(buildCentre);
        var worldBlock = net.minecraft.core.BlockPos.containing(capsuleCentre);
        level.getChunk(buildBlock.getX() >> 4, buildBlock.getZ() >> 4);
        level.getChunk(worldBlock.getX() >> 4, worldBlock.getZ() >> 4);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level,
                new Vector3d(buildCentre.x, buildCentre.y, buildCentre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null,
                "the one-voxel capsule builds in " + level.dimension().location());
        ServerSubLevel body = built.body();
        RigidBodyHandle handle = RigidBodyHandle.of(body);
        helper.assertTrue(handle != null && handle.isValid(),
                "the capsule has a valid rigid body to measure (handle " + handle + ")");

        container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        if (!buildCentre.equals(capsuleCentre)) {
            handle.teleport(new Vector3d(capsuleCentre.x, capsuleCentre.y, capsuleCentre.z),
                    new org.joml.Quaterniond(body.logicalPose().orientation()));
            body.updateBoundingBox();
            body.forceUpdateGlobalBounds();
            body.updateLastPose();
        }
        var startPose = body.logicalPose().position();
        Vector3d start = new Vector3d(startPose.x(), startPose.y(), startPose.z());
        XenoPixelsMod.LOGGER.info("PodHandoffVelocity coordinates: scenario={} hostDimension={} logicalPosition={}"
                        + " holdingPlotCenterBlock={}", scenario, body.getLevel().dimension().location(), start,
                body.getPlot().getCenterBlock());
        handle.addLinearAndAngularVelocity(new Vector3d(appliedVelocity), new Vector3d());
        Vector3d applied = handle.getLinearVelocity(new Vector3d());

        // Positions and per-tick measured-axis speeds are read from the world, not computed from the applied number.
        Vector3d[] positions = new Vector3d[WINDOW_TICKS + 1];
        double[] speeds = new double[WINDOW_TICKS + 1];
        positions[0] = start;
        speeds[0] = component(applied, measuredAxis);
        sampleEachTick(helper, container, body, handle, positions, speeds, applied, scenario, measuredAxis, 1);
    }

    /** Walks one tick at a time so the decay curve is visible in the log, not just a single endpoint. */
    private static void sampleEachTick(GameTestHelper helper, ServerSubLevelContainer container, ServerSubLevel body,
                                       RigidBodyHandle handle, Vector3d[] positions, double[] speeds,
                                       Vector3d applied, String scenario, int measuredAxis, int tick) {
        helper.runAfterDelay(1, () -> {
            if (body.isRemoved()) {
                report(helper, container, body, positions, speeds, applied, scenario,
                        measuredAxis, "the capsule was removed at tick " + tick + " before the window closed");
                return;
            }
            var pose = body.logicalPose().position();
            positions[tick] = new Vector3d(pose.x(), pose.y(), pose.z());
            speeds[tick] = component(handle.getLinearVelocity(new Vector3d()), measuredAxis);
            if (tick < WINDOW_TICKS) sampleEachTick(helper, container, body, handle, positions, speeds, applied,
                    scenario, measuredAxis, tick + 1);
            else report(helper, container, body, positions, speeds, applied, scenario, measuredAxis, null);
        });
    }

    private static void report(GameTestHelper helper, ServerSubLevelContainer container, ServerSubLevel body,
                               Vector3d[] positions, double[] speeds, Vector3d applied, String scenario,
                               int measuredAxis, String earlyExit) {
        StringBuilder table = new StringBuilder();
        for (int tick = 0; tick <= WINDOW_TICKS; tick++) {
            if (positions[tick] == null) break;
            table.append(" [tick ").append(tick).append(" x=").append(fmt(positions[tick].x))
                    .append(" y=").append(fmt(positions[tick].y)).append(" z=").append(fmt(positions[tick].z))
                    .append(" v").append("XYZ".charAt(measuredAxis)).append("=").append(fmt(speeds[tick]))
                    .append(']');
        }
        double measuredVelocity = component(applied, measuredAxis);
        double perTickCandidate = measuredVelocity;
        double perSecondCandidate = measuredVelocity / 20.0;
        if (earlyExit != null) {
            XenoPixelsMod.LOGGER.warn("PodHandoffVelocity probe INCONCLUSIVE at {}: {}. applied={} readings={}",
                    scenario, earlyExit, applied, table);
            releaseAndRemove(container, body);
            helper.assertTrue(false, "velocity unit probe: " + earlyExit + "; the basis stays NOT VERIFIED");
            return;
        }

        // Retracted 2026-10-20: the first-tick excess is not a sampling transient and not a pending integrator step.
        // Across the archived runs the per-tick velocity readings agree to within 1e-5 (tick 1-4: 1.99103, 1.98210
        // or 1.98211, 1.97321 or 1.97322, 1.96436 or 1.96437) while the first-tick position delta swings both ways:
        // 0.38029 above the steady rate (05:39), 0.09989 equal to it (05:49), 0.06778 below it (05:20), and in that
        // 05:20 run y rose 64.50000 -> 64.58710 against gravity before falling back. A pending step would have
        // moved the velocity reading and would have been consistent; a contact pushout from a fixture sharing the
        // old absolute coordinates moves the reported pose and leaves the integrator alone. The steady window is
        // kept because it is conservative, not because the pushout is predictable, and both probes now sit on
        // per-area offsets so nothing shares this capsule's contact space.
        int steadyTicks = WINDOW_TICKS - STEADY_WINDOW_START;
        double travelled = component(positions[WINDOW_TICKS], measuredAxis)
                - component(positions[STEADY_WINDOW_START], measuredAxis);
        double meanPerTick = travelled / steadyTicks;
        double firstTick = component(positions[1], measuredAxis) - component(positions[0], measuredAxis);
        double wholeWindowPerTick = (component(positions[WINDOW_TICKS], measuredAxis)
                - component(positions[0], measuredAxis)) / WINDOW_TICKS;
        double ratioToPerSecond = perSecondCandidate == 0.0 ? Double.NaN : meanPerTick / perSecondCandidate;
        String verdict;
        if (Math.abs(meanPerTick) < 1.0E-6) verdict = "INCONCLUSIVE (the body did not translate)";
        else if (ratioToPerSecond >= 0.5 && ratioToPerSecond <= 2.0) verdict = "BLOCKS PER SECOND";
        else if (ratioToPerSecond >= 10.0 && ratioToPerSecond <= 40.0) verdict = "BLOCKS PER TICK";
        else verdict = "INCONCLUSIVE (ratio " + fmt(ratioToPerSecond) + " matches neither band)";

        XenoPixelsMod.LOGGER.info("PodHandoffVelocity probe at {}: applied addLinearAndAngularVelocity axis {}={} and the body"
                        + " moved {} blocks over the steady ticks {}..{} (mean {} per tick; whole-window mean {},"
                        + " first tick {}, one-tick readings{}); candidate per-tick={} candidate per-second={}"
                        + " -> SABLE BODY VELOCITY BASIS = {}",
                scenario, "XYZ".charAt(measuredAxis), fmt(measuredVelocity), fmt(travelled), STEADY_WINDOW_START,
                WINDOW_TICKS, fmt(meanPerTick), fmt(wholeWindowPerTick), fmt(firstTick), table,
                fmt(perTickCandidate), fmt(perSecondCandidate),
                verdict);

        releaseAndRemove(container, body);
        helper.assertTrue(body.isRemoved(), "probe cleanup removed the capsule");
        helper.assertTrue(!verdict.startsWith("INCONCLUSIVE"),
                "the probe at " + scenario + " must decide the unit basis, and it reported " + verdict
                        + " (applied " + fmt(measuredVelocity) + ", mean per tick " + fmt(meanPerTick) + ")");
        helper.succeed();
    }

    private static void releaseAndRemove(ServerSubLevelContainer container, ServerSubLevel body) {
        if (body == null || body.isRemoved()) return;
        container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        if (container.getSubLevel(body.getUniqueId()) == body)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static double component(Vector3d value, int axis) {
        return axis == 0 ? value.x : axis == 1 ? value.y : value.z;
    }
}
