package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3d;

/**
 * §5's live consumer of the activation ladder S1 produces: for every ship in the chart-space host, it
 * sums the pull of the bodies the ladder currently <b>simulates</b> and applies that as an additional
 * linear impulse. This is the first subsystem that reads {@code FIELD}-or-above stages rather than only
 * setting them, which is what makes §21's lazy activation pay off — a planet nobody is near contributes
 * no gravity because it never reaches {@code FIELD}.
 *
 * <p><b>Why a separate impulse, not the aero acceleration slot.</b>
 * {@link net.bullettrain.xenopixelsmod.aero.control.AeroAeroForceSystem#setAeroAcceleration} overwrites a
 * single world-space vector per ship, so writing chart gravity through it would clobber the atmosphere
 * model, and {@code ShipGravityControl}/{@code OrbitalGravitySystem} own each ship's scalar
 * {@code gravitySi} down-pull. Applying chart gravity as its own impulse on the same physics tick is what
 * makes it <b>additive</b>: physical impulses superpose, so orbital pull, atmosphere, and the local
 * down-gravity all act together, and turning {@code fieldGravity} off leaves ship behavior byte-for-byte as
 * it is today. This matches {@code PhysicalPlanetSpike}, which applies its central pull the same way.
 *
 * <p><b>Chart-space host.</b> The configured handoff host (normally {@code xenopixelsmod:voidsea}) is
 * where chart and block coordinates coincide. Managed physical Earth has its own orbital force owner
 * and is excluded from this ship field pass. Other dimensions retain their existing gravity owners.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceGravityConsumer implements SpaceTickScheduler.Pass {
    private static final SpaceGravityConsumer PASS = new SpaceGravityConsumer();

    /** Reused scratch; this pass runs only on the server thread inside the physics tick. */
    private final double[] accel = new double[3];
    private final Vector3d impulse = new Vector3d();

    private SpaceGravityConsumer() {}

    /** Gated like {@code SpaceLodDriver}: with space off this pass is never scheduled at all. */
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        if (XenoSpaceConfig.enabled()) SpaceTickScheduler.register(PASS);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SpaceTickScheduler.unregister(PASS); }

    @Override public String name() { return "field-gravity"; }

    @Override public int intervalTicks() { return XenoSpaceConfig.gravityIntervalTicks(); }

    /** Only the chart-space host has ships this pass is allowed to pull; see the class javadoc. */
    @Override
    public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && XenoSpaceConfig.handoffHost().equals(level.dimension().location());
    }

    @Override
    public int run(SubLevelPhysicsSystem system, double deltaSeconds) {
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.fieldGravity()) return 0;
        ServerLevel level = system.getLevel();

        // The step clamp must match AeroAeroForceSystem exactly — a large deltaSeconds on a lag spike
        // would otherwise turn a bounded acceleration into an impulse that flings the ship.
        double step = Math.max(0.0, Math.min(deltaSeconds, 0.1));
        if (step <= 0.0) return 0;

        MinecraftServer server = level.getServer();
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        if (chart.size() == 0) return 0;

        int touched = 0;
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel ship)) continue;
            if (net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.isManaged(ship)) continue;
            var pose = ship.logicalPose();
            var pos = pose.position();
            chart.simulatingGravityAt(pos.x(), pos.y(), pos.z(), accel);
            if (accel[0] == 0.0 && accel[1] == 0.0 && accel[2] == 0.0) continue;
            if (!Double.isFinite(accel[0]) || !Double.isFinite(accel[1]) || !Double.isFinite(accel[2])) continue;

            RigidBodyHandle handle = system.getPhysicsHandle(ship);
            if (handle == null || !handle.isValid()) continue;

            // World -> body space, then scale to an impulse of mass * accel * dt. Sable's impulse API
            // takes body-space vectors; the same transformInverse ShipGravityControl uses.
            impulse.set(accel[0], accel[1], accel[2]);
            pose.orientation().transformInverse(impulse);
            double mass = Math.max(1.0, ship.getMassTracker().getMass());
            impulse.mul(mass * step);
            if (impulse.lengthSquared() < 1.0e-10) continue;
            handle.applyLinearImpulse(impulse);
            touched++;
        }
        return touched;
    }
}
