package net.bullettrain.xenopixelsmod.space.dragonball;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.physics.PlanetOrbitHold;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps loaded, chart-assigned Super Dragon Ball bodies on their host-relative Kepler tracks. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonBallPlanetOrbitPass implements SpaceTickScheduler.Pass {
    private static final DragonBallPlanetOrbitPass PASS = new DragonBallPlanetOrbitPass();
    private static final Map<MinecraftServer, Map<UUID, Track>> TRACKS = new IdentityHashMap<>();

    private static final class Track {
        final int slot;
        final DragonBallPlanetDefinition definition;
        final DragonBallPlanetOrbit orbit;
        final double[] position = new double[3], velocity = new double[3], previous = new double[3];
        final double[] previousVelocity = new double[3], measured = new double[3];
        final double[] target = new double[3], targetVelocity = new double[3], feedForward = new double[3];
        final double[] command = new double[3], previousCommand = new double[3], gravity = new double[3];
        final double[] hostPosition = new double[3], hostVelocity = new double[3];
        final Vector3d gravityVector = new Vector3d();
        final Vector3d impulse = new Vector3d();
        final PlanetOrbitHold.ReleaseDetector release = new PlanetOrbitHold.ReleaseDetector();
        long previousTick = Long.MIN_VALUE;
        boolean hasVelocity, held;

        Track(int slot, DragonBallPlanetDefinition definition, DragonBallPlanetOrbit orbit) {
            this.slot = slot;
            this.definition = definition;
            this.orbit = orbit;
        }
    }

    private DragonBallPlanetOrbitPass() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) { SpaceTickScheduler.register(PASS); }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SpaceTickScheduler.unregister(PASS);
        TRACKS.remove(event.getServer());
    }

    /** Refreshes at most seven cached orbit records from the manager's safe identity census. */
    static void sync(MinecraftServer server, Map<Integer, UUID> identities,
                     Map<Integer, DragonBallPlanetDefinition> assignments) {
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        Map<UUID, Track> previous = TRACKS.getOrDefault(server, Map.of());
        Map<UUID, Track> next = new HashMap<>();
        if (identities != null && assignments != null) for (var entry : identities.entrySet()) {
            int slot = entry.getKey();
            UUID bodyId = entry.getValue();
            DragonBallPlanetDefinition authored = assignments.get(slot);
            if (bodyId == null || authored == null || authored.slot() != slot) continue;
            int hostSlot = chart.slotOf(authored.hostChartId());
            if (hostSlot < 0) continue;
            DragonBallPlanetDefinition definition = DragonBallPlanetAssignments.effective(authored,
                    saved.manualOrbit(slot), chart.body(hostSlot).radiusBlocks(),
                    DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS);
            Track old = previous.get(bodyId);
            if (old != null && old.definition.equals(definition)) {
                next.put(bodyId, old);
                continue;
            }
            double mu = chart.body(hostSlot).mu();
            try {
                DragonBallPlanetOrbit orbit = definition.orbitPeriodSeconds() > 0.0
                        ? DragonBallPlanetOrbit.createWithPeriod(definition.orbitRadiusBlocks(),
                                definition.orbitPhaseRadians(), definition.orbitPeriodSeconds())
                        : DragonBallPlanetOrbit.create(definition.orbitRadiusBlocks(),
                                definition.orbitPhaseRadians(), mu);
                next.put(bodyId, new Track(slot, definition, orbit));
            } catch (IllegalArgumentException invalidOrbit) {
                XenoPixelsMod.LOGGER.warn("SDB slot {} orbit pass skipped: {}", slot, invalidOrbit.getMessage());
            }
        }
        TRACKS.put(server, next);
    }

    /** Rebuild the small cached controller set immediately after a server-side orbit edit. */
    public static void refreshFromSaved(MinecraftServer server) {
        if (server == null) return;
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        Map<Integer, UUID> identities = new HashMap<>();
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            UUID id = saved.planetBodyUuid(slot);
            if (id != null) identities.put(slot, id);
        }
        sync(server, identities, SpaceChartService.state(server).dragonBallPlanets);
    }

    @Override public String name() { return "super-dragon-ball-orbits"; }

    @Override public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && PhysicalEarth.HOST.equals(level.dimension().location());
    }

    @Override public int run(SubLevelPhysicsSystem system, double seconds) {
        if (!XenoSpaceConfig.enabled()) return 0;
        ServerLevel level = system.getLevel();
        if (level == null) return 0;
        MinecraftServer server = level.getServer();
        Map<UUID, Track> tracks = TRACKS.get(server);
        if (tracks == null || tracks.isEmpty()) return 0;
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        long tick = level.getGameTime();
        double dt = Math.max(0.05, seconds);
        var container = SubLevelContainer.getContainer(level);
        int worked = 0;
        for (var entry : tracks.entrySet()) {
            ServerSubLevel body = container.getSubLevel(entry.getKey()) instanceof ServerSubLevel loaded
                    ? loaded : null;
            if (body == null || body.isRemoved()) continue;
            var handle = system.getPhysicsHandle(body);
            double mass = body.getMassTracker().getMass();
            if (handle == null || !handle.isValid() || !(mass > 0.0) || !Double.isFinite(mass)) continue;
            Track track = entry.getValue();
            if (!target(chart, track, tick / 20.0 + XenoSpaceConfig.orbitEpochOffsetSeconds())) continue;
            var pose = body.logicalPose();
            var position = pose.position();
            track.position[0] = position.x(); track.position[1] = position.y(); track.position[2] = position.z();
            if (track.previousTick != Long.MIN_VALUE && tick > track.previousTick) {
                int elapsedTicks = (int) Math.min(Integer.MAX_VALUE, tick - track.previousTick);
                double elapsed = elapsedTicks / 20.0;
                System.arraycopy(track.velocity, 0, track.previousVelocity, 0, 3);
                for (int axis = 0; axis < 3; axis++)
                    track.velocity[axis] = (track.position[axis] - track.previous[axis]) / elapsed;
                if (track.hasVelocity) {
                    for (int axis = 0; axis < 3; axis++)
                        track.measured[axis] = (track.velocity[axis] - track.previousVelocity[axis]) / elapsed;
                    track.held = track.release.update(track.measured, track.command, track.previousCommand,
                            XenoSpaceConfig.orbitHeldThreshold(), XenoSpaceConfig.orbitReleaseTicks(), elapsedTicks);
                }
                track.hasVelocity = true;
            }
            System.arraycopy(track.position, 0, track.previous, 0, 3);
            track.previousTick = tick;
            DimensionPhysicsData.getGravity(level, position, track.gravityVector);
            track.gravity[0] = track.gravityVector.x;
            track.gravity[1] = track.gravityVector.y;
            track.gravity[2] = track.gravityVector.z;
            System.arraycopy(track.command, 0, track.previousCommand, 0, 3);
            java.util.Arrays.fill(track.command, 0.0);
            track.impulse.set(-track.gravity[0], -track.gravity[1], -track.gravity[2]);
            if (!track.held) {
                double correction = XenoSpaceConfig.orbitReturnBoost()
                        ? PlanetOrbitHold.returnAcceleration(track.position, track.velocity,
                            track.target, track.targetVelocity, track.feedForward,
                            XenoSpaceConfig.orbitReturnStiffness(), XenoSpaceConfig.orbitReturnDamping(),
                            XenoSpaceConfig.orbitReturnMaxAccel(), XenoSpaceConfig.orbitReturnMaxSpeed(), track.command)
                        : PlanetOrbitHold.acceleration(track.position, track.velocity,
                            track.target, track.targetVelocity, track.feedForward,
                            XenoSpaceConfig.orbitHoldStiffness(), XenoSpaceConfig.orbitHoldDamping(),
                            XenoSpaceConfig.orbitHoldMaxAccel(), track.command);
                if (!Double.isFinite(correction)) continue;
                track.impulse.add(track.command[0], track.command[1], track.command[2]);
            }
            track.impulse.mul(mass * dt);
            pose.orientation().transformInverse(track.impulse);
            if (track.impulse.isFinite() && track.impulse.lengthSquared() > 1.0e-12)
                handle.applyLinearImpulse(track.impulse);
            worked++;
        }
        return worked;
    }

    private static boolean target(ChartRegistry chart, Track track, double timeSeconds) {
        return targetPosition(chart, track.definition, track.orbit, timeSeconds,
                track.hostPosition, track.hostVelocity, track.target, track.targetVelocity, track.feedForward);
    }

    static boolean targetPosition(ChartRegistry chart, DragonBallPlanetDefinition definition,
                                  DragonBallPlanetOrbit orbit, double timeSeconds,
                                  double[] hostPosition, double[] hostVelocity,
                                  double[] position, double[] velocity, double[] acceleration) {
        int hostSlot = chart.slotOf(definition.hostChartId());
        if (hostSlot < 0) return false;
        hostPosition[0] = chart.x(hostSlot);
        hostPosition[1] = chart.y(hostSlot);
        hostPosition[2] = chart.z(hostSlot);
        hostVelocity[0] = chart.velocityX(hostSlot);
        hostVelocity[1] = chart.velocityY(hostSlot);
        hostVelocity[2] = chart.velocityZ(hostSlot);
        orbit.evaluate(hostPosition, hostVelocity, ZERO, timeSeconds, position, velocity, acceleration);
        return true;
    }

    private static final double[] ZERO = new double[3];
}
