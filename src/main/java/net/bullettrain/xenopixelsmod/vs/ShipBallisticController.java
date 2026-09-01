package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.missile.BallisticTrajectory;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ship-as-missile with mass/CoM-aware thrust, SAS, and Y-height corridor guidance.
 *
 * <h3>Flight plan</h3>
 * <ol>
 *   <li><b>EJECT</b> — vertical clear of pad</li>
 *   <li><b>BOOST</b> — climb until <b>loft Y</b> (peak)</li>
 *   <li><b>COAST</b> — pitch to <b>cruise Y</b> if lower, then level straight to target</li>
 *   <li><b>TERMINAL</b> — dive to target XYZ with velocity cancel</li>
 * </ol>
 *
 * <h3>SAS</h3>
 * High angular damping + nose align to flight vector. Forces applied at CoM (body origin)
 * so offset thrusters don't tumble the hull. CoM cannot be rewritten by API — we
 * <b>read</b> mass/CoM and thrust through the CoM.
 */
public final class ShipBallisticController {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final Map<UUID, ShipBallisticController> CONTROLLERS = new ConcurrentHashMap<>();

    private static final Set<Long> ACTIVE_FLIGHTS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentHashMap<Long,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> ACTIVE_FLIGHT_DIMS =
            new ConcurrentHashMap<>();

    private static final int EJECT_TICKS = 18;
    private static final double HIT_RANGE = 5.0;
    /** Minimum timeout; each launch expands this from its planned ETA. */
    private static final int MIN_FLIGHT_TIMEOUT_TICKS = 20 * 60 * 30;

    /** Convert game-style boost accel → SI force scale (F = m · a · this). Keep modest. */
    private static final double ACCEL_TO_SI = 55.0;
    /** Approximate gravity already applied by the VS2 world. */
    private static final double VS_WORLD_GRAVITY_SI = 10.0;
    /** Physics stops trusting a game-thread thrust command after this long. */
    private static final long GUIDANCE_STALE_NANOS = 300_000_000L;

    /**
     * SAS: rate damp + nose toward loft aim (apex / glide / terminal).
     * Caps prevent death-spins; gains scale by phase.
     */
    private static final double SAS_OMEGA_GAIN = 3.0;
    private static final double SAS_ALIGN_BASE = 0.34;
    /** Max commanded angular acceleration. The live inertia tensor converts this to torque. */
    private static final double SAS_MAX_ANGULAR_ACCEL = 4.0;
    /** Maximum nose-command change per server guidance tick. */
    private static final double ATTITUDE_SLEW_RADIANS = Math.toRadians(4.0);
    /** Terminal needs to acquire a steep dive quickly without an instantaneous flip. */
    private static final double TERMINAL_ATTITUDE_SLEW_RADIANS = Math.toRadians(8.0);

    // Guidance gains (linear only; no extra phase torque)
    private static final double MIDCOURSE_GAIN = 0.55;
    private static final double TERMINAL_GAIN = 1.1;
    private static final double ALT_P = 0.85;
    private static final double ALT_D = 0.35;

    // --- flight program (game write / phys read) ---
    private volatile boolean flying;
    private volatile MissilePhase phase = MissilePhase.DEAD;
    private volatile double targetX, targetY, targetZ;
    private volatile double launchX, launchY, launchZ;
    private volatile double loftX = 0, loftY = 1, loftZ = 0;
    private volatile double boostAccel = 0.35;
    private volatile double gravitySi = net.bullettrain.xenopixelsmod.missile.BallisticCalculator.EARTH_GRAVITY;
    private volatile double dragCoefficient = net.bullettrain.xenopixelsmod.missile.BallisticCalculator.DEFAULT_DRAG;
    private volatile int boostTicksLeft = 40;
    /** Fuel in real powered-physics seconds; game-thread stalls do not grant free burn. */
    private volatile double boostSecondsLeft = 2.0;
    /** Fixed-angle calculator cutoff; infinity means normal duration-based burn. */
    private volatile double plannedCutoffSpeedMps = Double.POSITIVE_INFINITY;
    /** Optional one-shot pitch program: activate when world Y reaches this altitude. */
    private volatile double angleCommandY;
    private volatile double angleCommandDeg;
    private volatile boolean angleCommandExecuted;
    private volatile boolean terminalEnabled = true;
    private volatile float warheadYield = 8.0f;
    private volatile int thrusterBonus;
    private volatile int phaseAge;
    private volatile int flightAge;
    private volatile int maxFlightTicks = MIN_FLIGHT_TIMEOUT_TICKS;
    private volatile boolean impactRequested;
    private volatile String status = "idle";

    /**
     * How long after a {@link #getStatus()} call we keep formatting the detailed status text.
     *
     * <p>The detail lines are built with {@code String.format} inside the Sable physics tick, once
     * per ten ticks per missile in flight. That is rate-limited but still pure waste when nothing
     * is reading — the common case, since a status readout only exists while a player has the
     * guidance screen open or a computer is polling the peripheral. Three seconds is long enough
     * that a reader polling at any sane rate never sees the text go stale.
     */
    private static final long STATUS_READER_WINDOW_NANOS = 3_000_000_000L;

    private volatile long lastStatusReadNanos;
    private volatile boolean statusEverRead;
    /** Phase at the last detailed format, so a transition always produces one line. */
    private MissilePhase lastStatusPhase;

    /** Loft peak world Y — climb here first. */
    private volatile double apexY;
    /** Level-cruise world Y — fly straight to target at this altitude after loft. */
    private volatile double cruiseY;
    /** Apex waypoint on ground track (world XZ) — climb aims here. */
    private volatile double apexX, apexZ;
    /** Ground-track fraction at apex (start cruise) and at terminal open. */
    private volatile double peakGroundFrac = 0.35;
    private volatile double terminalGroundFrac = 0.72;
    private volatile double rangeHorizontal;
    private volatile double terminalRange = 64.0;
    private volatile double pitchDeg;
    private volatile boolean pastApex;
    private volatile boolean autoStable = true;
    private volatile boolean userLoftY;
    private volatile boolean userCruiseY;
    private volatile BallisticFlightPlan.FlightMode advancedMode =
            BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE;
    private volatile List<Vec3> routeWaypoints = List.of();
    private volatile int routeWaypointIndex;
    private volatile boolean advancedAuto = true;
    /** Previous game-tick COM for swept collision; prevents high-speed tunnelling. */
    private double previousX, previousY, previousZ;
    private boolean hasPreviousPosition;
    private double closestTargetDistance = Double.POSITIVE_INFINITY;
    /** Physics-thread sweep state; independent of the potentially stalled game thread. */
    private double physPreviousX, physPreviousY, physPreviousZ;
    private boolean hasPhysPreviousPosition;
    private double physClosestTargetDistance = Double.POSITIVE_INFINITY;
    /** Monotonic heartbeat written by gameTick and read by physTick. */
    private volatile long lastGuidanceNanos;
    private long lastPhysicsNanos;
    private double physicsStepSeconds = 0.05;

    /** Desired world nose and roll-up directions, published atomically for the physics thread. */
    private volatile AttitudeCommand attitudeCommand = new AttitudeCommand(0, 1, 0);
    /** Calibrated local/model-space nose and roll-up axes. */
    private volatile double bodyNoseX = 0, bodyNoseY = 1, bodyNoseZ = 0;
    private volatile double bodyUpX = 0, bodyUpY = 0, bodyUpZ = 1;
    /** Desired altitude (world Y) — loft during boost, cruise during COAST. */
    private volatile double desiredY;
    /**
     * Thrust direction published by gameTick for phys (avoids Vec3 alloc + re-solve on phys thread).
     */
    private volatile ThrustCommand thrustCommand = new ThrustCommand(0, 1, 0, 0);
    /** Dimension where this ship was launched (for ticker O(1) resolve). */
    private volatile net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> flightDim;
    /** Cached ship mass (kg) + CoM in model space (read-only from VS). */
    private volatile double shipMass = 1000.0;
    private volatile double comModelX, comModelY, comModelZ;
    private volatile boolean hasCom;
    /** COM-to-hull estimate used so large ships stop when their hull reaches the target. */
    private volatile double hullArrivalRadius = HIT_RANGE;
    /** User-requested COM distance; 0 keeps automatic hull-safe arrival behavior. */
    private volatile double guidanceStopDistance;

    private volatile double solvedLoftX, solvedLoftY, solvedLoftZ;
    private volatile long activeShipId = -1L;

    /** Body-space CoM for force application (always 0,0,0 in body frame). */
    private final Vector3d bodyCom = new Vector3d(0, 0, 0);
    private final Vector3d scratchForce = new Vector3d();
    private final Vector3d scratchTorque = new Vector3d();
    private final Vector3d scratchNose = new Vector3d();
    private final Vector3d scratchRight = new Vector3d();
    private final Vector3d scratchAuxTorque = new Vector3d();
    private final Quaterniond scratchCurrentRotation = new Quaterniond();
    private final Quaterniond scratchDesiredRotation = new Quaterniond();
    private final Quaterniond scratchErrorRotation = new Quaterniond();
    private final Quaterniond scratchRollRotation = new Quaterniond();

    private record ThrustCommand(double x, double y, double z, double accelSi) {
    }

    record AttitudeCommand(double x, double y, double z,
                           double upX, double upY, double upZ) {
        AttitudeCommand(double x, double y, double z) {
            this(x, y, z, 0, 0, 1);
        }
    }

    record AccelerationCommand(double x, double y, double z) {
        double magnitude() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick((system, deltaSeconds) -> {
            SubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
            for (var candidate : container.getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel subLevel)) continue;
                ShipBallisticController controller = get(subLevel);
                if (controller == null || !controller.isFlying()) continue;
                RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
                if (handle != null && handle.isValid()) {
                    controller.physTick(subLevel, handle, deltaSeconds);
                }
            }
            // No map cleanup here: this callback fires once per DIMENSION's physics system, so
            // a removeIf against the currently ticking dimension's container deleted controllers
            // for ships in every OTHER dimension — killing each flight within a tick of launch
            // (launch banner in the log, then no per-tick logs at all). Controllers remove
            // themselves when their own flight ends instead.
        });
        XenoPixelsMod.LOGGER.info("Registered Sable ballistic physics callback");
    }

    public static ShipBallisticController getOrCreate(ServerSubLevel ship) {
        ensureRegistered();
        return CONTROLLERS.computeIfAbsent(ship.getUniqueId(), ignored -> new ShipBallisticController());
    }

    public static ShipBallisticController get(ServerSubLevel ship) {
        if (ship == null) return null;
        return CONTROLLERS.get(ship.getUniqueId());
    }

    public static Set<Long> activeFlightShipIds() {
        return ACTIVE_FLIGHTS;
    }

    public static boolean anyActiveFlights() {
        return !ACTIVE_FLIGHTS.isEmpty();
    }

    public static void unregisterFlight(long shipId) {
        ACTIVE_FLIGHTS.remove(shipId);
        ACTIVE_FLIGHT_DIMS.remove(shipId);
    }

    /**
     * Drop a flight whose ship is gone (disassembled mid-flight, or its dimension unloaded).
     *
     * <p>{@link #unregisterFlight} only clears the {@link #ACTIVE_FLIGHTS}/{@link #ACTIVE_FLIGHT_DIMS}
     * bookkeeping, which is keyed by the numeric Sable ship id. But {@link #CONTROLLERS} is keyed by
     * the ship's UUID, so a controller whose ship vanished mid-flight would otherwise linger there
     * for the server's whole uptime — its flight stops being ticked, yet no {@code abort()} or
     * {@code consumeImpact()} ever runs to remove it. The ticker calls this (rather than plain
     * unregister) on every give-up path so the controller goes with the flight.
     *
     * <p>The removeIf matches on {@code activeShipId}, which is set to -1 the moment a flight ends,
     * so it can only ever match the still-live controller for that ship.
     */
    public static void forgetFlight(long shipId) {
        unregisterFlight(shipId);
        CONTROLLERS.values().removeIf(c -> c.activeShipId == shipId);
    }

    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>
    activeFlightDimension(long shipId) {
        return ACTIVE_FLIGHT_DIMS.get(shipId);
    }

    public boolean isFlying() {
        return flying && phase != MissilePhase.DEAD;
    }

    public MissilePhase getPhase() {
        return phase;
    }

    public String getStatus() {
        // Reading is what makes the detailed text worth building; see STATUS_READER_WINDOW_NANOS.
        lastStatusReadNanos = System.nanoTime();
        statusEverRead = true;
        return status;
    }

    /**
     * True when the detailed {@code String.format} status lines are worth building this tick.
     *
     * <p>Called once per physics tick from the guidance loop. Phase transitions always qualify so
     * the last thing a reader sees is never a line from the previous phase.
     */
    private boolean statusWanted() {
        if (phase != lastStatusPhase) {
            lastStatusPhase = phase;
            return true;
        }
        return statusEverRead
                && System.nanoTime() - lastStatusReadNanos < STATUS_READER_WINDOW_NANOS;
    }

    public float getWarheadYield() {
        return warheadYield;
    }

    public Vec3 getTarget() {
        return new Vec3(targetX, targetY, targetZ);
    }

    /** Loft peak Y (climb target). */
    public double getApexY() {
        return apexY;
    }

    /** Level-cruise altitude Y. */
    public double getCruiseY() {
        return cruiseY;
    }

    public double getPitchDeg() {
        return pitchDeg;
    }

    public double getShipMass() {
        return shipMass;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getFlightDim() {
        return flightDim;
    }

    public void setAutoStable(boolean autoStable) {
        this.autoStable = autoStable;
    }

    public void setGuidanceStopDistance(double blocks) {
        guidanceStopDistance = Double.isFinite(blocks)
                ? Math.max(0.0, Math.min(100_000.0, blocks)) : 0.0;
    }

    /** Bind flight to a world dimension so ticker can resolve O(1). */
    public void setFlightDim(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        this.flightDim = dim;
        if (dim != null && activeShipId >= 0 && isFlying()) {
            ACTIVE_FLIGHT_DIMS.put(activeShipId, dim);
        }
    }

    public void setBodyCalibration(BlockPos base, BlockPos center, BlockPos nose) {
        if (base == null || center == null || nose == null) return;
        // BASE and NOSE are the authoritative ends of the missile. Do not average
        // the two center segments: an off-axis center block bends the calibrated
        // direction and makes swapping BASE/NOSE non-deterministic.
        double nx = nose.getX() - base.getX();
        double ny = nose.getY() - base.getY();
        double nz = nose.getZ() - base.getZ();
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6) return;
        nx /= length; ny /= length; nz /= length;
        bodyNoseX = nx; bodyNoseY = ny; bodyNoseZ = nz;

        // Choose a deterministic model-space roll-up axis perpendicular to the nose.
        double ux = Math.abs(ny) < 0.9 ? 0.0 : 0.0;
        double uy = Math.abs(ny) < 0.9 ? 1.0 : 0.0;
        double uz = Math.abs(ny) < 0.9 ? 0.0 : 1.0;
        double projection = ux * nx + uy * ny + uz * nz;
        ux -= nx * projection; uy -= ny * projection; uz -= nz * projection;
        double upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        bodyUpX = ux / upLength; bodyUpY = uy / upLength; bodyUpZ = uz / upLength;
    }

    /**
     * Arm and launch this ship on a ballistic flight plan.
     * Samples mass + CoM from VS inertia (CoM is not writable — forces go through CoM).
     */
    public boolean launch(ServerSubLevel ship, Vec3 targetWorld, Vec3 loftHint,
                          double boostAccel, int boostTicks, boolean terminal,
                          float yield, int thrusterCount) {
        return launch(ship, targetWorld, loftHint, boostAccel, boostTicks, terminal,
                yield, thrusterCount, 0, 0);
    }

    /** Back-compat: single Y = loft peak and cruise altitude. */
    public boolean launch(ServerSubLevel ship, Vec3 targetWorld, Vec3 loftHint,
                          double boostAccel, int boostTicks, boolean terminal,
                          float yield, int thrusterCount, double desiredApexY) {
        return launch(ship, targetWorld, loftHint, boostAccel, boostTicks, terminal,
                yield, thrusterCount, desiredApexY, 0);
    }

    /**
     * @param desiredLoftY   climb peak (world Y); 0 = auto from plan
     * @param desiredCruiseY level-flight altitude after loft; 0 = same as loft peak
     */
    public boolean launch(ServerSubLevel ship, Vec3 targetWorld, Vec3 loftHint,
                          double boostAccel, int boostTicks, boolean terminal,
                          float yield, int thrusterCount,
                          double desiredLoftY, double desiredCruiseY) {
        return launch(ship, targetWorld, loftHint, boostAccel, boostTicks, terminal,
                yield, thrusterCount, desiredLoftY, desiredCruiseY,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.EARTH_GRAVITY,
                net.bullettrain.xenopixelsmod.missile.BallisticCalculator.DEFAULT_DRAG);
    }

    public boolean launch(ServerSubLevel ship, Vec3 targetWorld, Vec3 loftHint,
                          double boostAccel, int boostTicks, boolean terminal,
                          float yield, int thrusterCount,
                          double desiredLoftY, double desiredCruiseY,
                          double gravitySi, double dragCoefficient) {
        if (ship == null || targetWorld == null) return false;
        if (isFlying()) return false;

        // Prefer transform position (world CoM proxy); refine with inertia if available
        Vector3dc posWorld = VsShipHelper.worldPosition(ship);
        Vec3 launch = new Vec3(posWorld.x(), posWorld.y(), posWorld.z());
        sampleMassAndCom(ship);

        this.userLoftY = desiredLoftY > 0;
        this.userCruiseY = desiredCruiseY > 0;
        this.advancedMode = BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE;
        this.routeWaypoints = List.of();
        this.routeWaypointIndex = 0;
        // Plan uses loft peak; if only cruise is set, plan at least that high
        double planLoftArg = userLoftY ? desiredLoftY : (userCruiseY ? desiredCruiseY : 0);
        BallisticTrajectory.FlightPlan plan =
                BallisticTrajectory.plan(launch, targetWorld, boostAccel, boostTicks, planLoftArg,
                        gravitySi, dragCoefficient);

        Vec3 ejectHint = loftHint != null && loftHint.lengthSqr() > 1.0e-6
                ? loftHint.normalize()
                : new Vec3(0, 1, 0);
        Vec3 ejectDir = ejectHint.scale(0.85).add(0, 0.15, 0).normalize();
        if (ejectDir.y < 0.5) {
            ejectDir = new Vec3(ejectDir.x * 0.3, 1.0, ejectDir.z * 0.3).normalize();
        }
        this.launchX = launch.x;
        this.launchY = launch.y;
        this.launchZ = launch.z;
        this.targetX = targetWorld.x;
        this.targetY = targetWorld.y;
        this.targetZ = targetWorld.z;

        this.loftX = ejectDir.x;
        this.loftY = ejectDir.y;
        this.loftZ = ejectDir.z;
        // Plan loft aims at apex XYZ (correct X/Z + big Y)
        this.solvedLoftX = plan.loftDir().x;
        this.solvedLoftY = plan.loftDir().y;
        this.solvedLoftZ = plan.loftDir().z;

        this.apexX = plan.apexX();
        // Loft peak: user loft, else plan (already ≥ cruise if only cruise set)
        double resolvedLoft = userLoftY
                ? Math.max(desiredLoftY, launch.y + 16.0)
                : plan.apexY();
        // Cruise: user cruise, else same as loft
        double resolvedCruise = userCruiseY
                ? Math.max(desiredCruiseY, launch.y + 8.0)
                : resolvedLoft;
        // Can't cruise above loft without climbing there first
        if (resolvedCruise > resolvedLoft) {
            resolvedLoft = resolvedCruise;
        }
        // Altitude ceiling. Northstar teleports anything crossing its atmosphere height into a
        // space dimension, and a Sable hull is a sub-level rather than an entity, so no entity
        // tag can exempt it — the only reliable defence is not to fly that high. Clamped here
        // rather than in the planner so a hand-entered loft is capped too.
        double ceiling = XenoServerConfig.missileMaxApexY;
        if (ceiling > 0.0) {
            resolvedLoft = Math.min(resolvedLoft, ceiling);
            resolvedCruise = Math.min(resolvedCruise, resolvedLoft);
        }
        this.apexY = resolvedLoft;
        this.cruiseY = resolvedCruise;
        this.apexZ = plan.apexZ();
        this.peakGroundFrac = plan.peakGroundFrac();
        this.terminalGroundFrac = plan.terminalGroundFrac();
        this.desiredY = Math.max(launch.y + 8.0, Math.min(apexY, launch.y + 20.0));
        this.rangeHorizontal = plan.rangeHorizontal();
        this.terminalRange = plan.terminalAcquireRange();
        this.pitchDeg = Math.toDegrees(plan.pitchRad());
        this.pastApex = false;

        this.boostAccel = Math.max(0.05, Math.min(5.0, boostAccel));
        this.gravitySi = net.bullettrain.xenopixelsmod.missile.BallisticCalculator
                .sanitizeGravity(gravitySi);
        this.dragCoefficient = net.bullettrain.xenopixelsmod.missile.BallisticCalculator
                .sanitizeDrag(dragCoefficient);
        // Climb burn sized for loft peak (not cruise)
        // Honor the configured burn duration. The trajectory planner uses this same
        // value, so silently extending it makes both the solution and fuel display false.
        this.boostTicksLeft = Math.max(10, Math.min(3_600, boostTicks));
        this.boostSecondsLeft = this.boostTicksLeft / 20.0;
        this.plannedCutoffSpeedMps = Double.POSITIVE_INFINITY;
        this.angleCommandY = 0.0;
        this.angleCommandDeg = 0.0;
        this.angleCommandExecuted = false;
        this.terminalEnabled = terminal;
        this.warheadYield = Math.max(0f, Math.min(24f, yield));
        this.thrusterBonus = Math.max(0, thrusterCount);
        this.phaseAge = 0;
        this.flightAge = 0;
        this.previousX = launch.x;
        this.previousY = launch.y;
        this.previousZ = launch.z;
        this.hasPreviousPosition = true;
        this.closestTargetDistance = launch.distanceTo(targetWorld);
        this.physPreviousX = launch.x;
        this.physPreviousY = launch.y;
        this.physPreviousZ = launch.z;
        this.hasPhysPreviousPosition = true;
        this.physClosestTargetDistance = this.closestTargetDistance;
        this.lastGuidanceNanos = System.nanoTime();
        this.lastPhysicsNanos = this.lastGuidanceNanos;
        // Map-scale routes can take hours at low speed. Four times the planned ETA
        // leaves room for heavy-hull acceleration and controller corrections while
        // retaining a fuse for genuinely lost flights.
        long plannedEta = Math.max(0L, plan.etaTicks());
        long plannedTimeout = plannedEta * 4L + 20L * 60L * 10L;
        this.maxFlightTicks = (int) Math.min(Integer.MAX_VALUE - 1L,
                Math.max(MIN_FLIGHT_TIMEOUT_TICKS, plannedTimeout));
        this.impactRequested = false;
        this.flightDim = null;
        this.phase = MissilePhase.EJECT;
        this.flying = true;
        this.attitudeCommand = initialAttitude(ship, ejectDir.x, ejectDir.y, ejectDir.z);
        this.thrustCommand = new ThrustCommand(
                ejectDir.x, ejectDir.y, ejectDir.z, this.boostAccel * ACCEL_TO_SI);
        this.status = String.format(
                "EJECT loftY=%.0f%s cruiseY=%.0f%s range=%.0f",
                apexY, userLoftY ? "" : "(auto)",
                cruiseY, userCruiseY ? "" : "(=loft)",
                rangeHorizontal);

        long shipId = VsShipHelper.getShipId(ship);
        ACTIVE_FLIGHTS.add(shipId);
        this.activeShipId = shipId;

        XenoPixelsMod.LOGGER.info(
                "Ship {} ballistic → tgt=({},{},{}) loftY={} cruiseY={} apexXZ=({},{}) range={}",
                shipId, (int) targetX, (int) targetY, (int) targetZ,
                (int) apexY, (int) cruiseY, (int) apexX, (int) apexZ,
                String.format("%.0f", rangeHorizontal));
        return true;
    }

    public void setAdvancedPlan(BallisticFlightPlan.FlightMode mode,
                                List<Vec3> waypoints, boolean autoEnabled) {
        this.advancedMode = mode == null
                ? BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE : mode;
        this.routeWaypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        this.routeWaypointIndex = 0;
        this.advancedAuto = autoEnabled;
    }

    /** Applies the exact server preview geometry to the live VS2 controller. */
    public void setAdvancedPlan(BallisticFlightPlan.Result plan) {
        if (plan == null) return;
        setAdvancedPlan(plan.settings().mode(), plan.controllerWaypoints(), plan.settings().autoEnabled());
        if (!userLoftY) {
            apexY = Math.max(launchY + 16.0, plan.apexY());
        }
        peakGroundFrac = plan.settings().apexFraction();
        terminalGroundFrac = plan.settings().terminalFraction();
        // Explicit engine/easy-mode altitude overrides are authoritative. Previously
        // this post-launch plan application silently replaced desiredCruiseY.
        if (!userCruiseY) {
            cruiseY = plan.settings().altitudeLayerEnabled()
                    ? Math.max(targetY + 48.0, plan.settings().minimumClearanceY()) : apexY;
        }
        BallisticFlightPlan.Sample peak = null;
        for (BallisticFlightPlan.Sample sample : plan.samples()) {
            if (peak == null || sample.y() > peak.y()) peak = sample;
        }
        if (peak != null) { apexX = peak.x(); apexZ = peak.z(); }
        desiredY = Math.max(launchY + 8.0, Math.min(apexY, launchY + 20.0));
        plannedCutoffSpeedMps = plan.settings().desiredAngleDeg() > 0.0
                && Double.isFinite(plan.requiredSpeedMps())
                ? Math.max(1.0, plan.requiredSpeedMps()) : Double.POSITIVE_INFINITY;
        angleCommandY = plan.settings().angleCommandY();
        angleCommandDeg = plan.settings().angleCommandDeg();
        angleCommandExecuted = false;
    }

    /** Hot-swaps an AUTO route without resetting flight age, burn state, or fuzes. */
    public void updateAdaptivePlan(Vec3 resolvedTarget, List<Vec3> waypoints) {
        if (!flying || resolvedTarget == null || !advancedAuto) return;
        targetX = resolvedTarget.x;
        targetY = resolvedTarget.y;
        targetZ = resolvedTarget.z;
        List<Vec3> updated = waypoints == null ? List.of() : List.copyOf(waypoints);
        routeWaypointIndex = updated.isEmpty() ? 0 : Math.min(routeWaypointIndex, updated.size() - 1);
        routeWaypoints = updated;
        status = "AUTO replanned";
    }

    public void updateAdaptivePlan(BallisticFlightPlan.Result plan) {
        if (plan == null) return;
        updateAdaptivePlan(plan.resolvedTarget(), plan.controllerWaypoints());
        peakGroundFrac = plan.settings().apexFraction();
        terminalGroundFrac = plan.settings().terminalFraction();
        apexY = Math.max(apexY, plan.apexY());
    }

    /** Keeps the craft stable on the last intercept while a moving designation is unavailable. */
    public void enterSearchHold() {
        if (!flying) return;
        routeWaypoints = List.of(new Vec3(targetX, Math.max(targetY + 192.0, cruiseY), targetZ));
        routeWaypointIndex = 0;
        status = "SEARCH/HOLD — target signal lost";
    }

    /**
     * Read mass + CoM from VS inertia. CoM is <b>not settable</b> via public API —
     * we only store it for force-at-CoM and diagnostics.
     */
    private void sampleMassAndCom(ServerSubLevel ship) {
        hasCom = false;
        shipMass = 1000.0;
        hullArrivalRadius = HIT_RANGE;
        comModelX = comModelY = comModelZ = 0;
        try {
            var inertia = ship.getMassTracker();
            if (inertia != null) {
                double m = inertia.getMass();
                if (m > 1.0) shipMass = m;
                Vector3dc com = inertia.getCenterOfMass();
                if (com != null) {
                    comModelX = com.x();
                    comModelY = com.y();
                    comModelZ = com.z();
                    hasCom = true;
                }
                try {
                    var tensor = inertia.getInertiaTensor();
                    double maxMoment = Math.max(Math.abs(tensor.m00()),
                            Math.max(Math.abs(tensor.m11()), Math.abs(tensor.m22())));
                    if (Double.isFinite(maxMoment) && maxMoment > 0 && shipMass > 1) {
                        // For a box-like hull, sqrt(3 Imax / m) is a useful conservative
                        // COM-to-surface estimate without depending on implementation AABBs.
                        hullArrivalRadius = Math.max(HIT_RANGE,
                                Math.min(96.0, Math.sqrt(3.0 * maxMoment / shipMass) + 2.0));
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Abort every ship flight in progress, in every dimension.
     *
     * <p>Iterates a copy: {@link #abort()} removes the controller from {@code CONTROLLERS}, so
     * aborting while iterating the live collection would fault.
     *
     * @return how many flights were aborted
     */
    public static int abortAll() {
        int aborted = 0;
        for (ShipBallisticController controller : new java.util.ArrayList<>(CONTROLLERS.values())) {
            if (controller.isFlying()) aborted++;
            controller.abort();
        }
        // abort() clears each entry, but a controller that was never flying leaves the map
        // holding an inert instance; drop those too so a purge really is a purge.
        CONTROLLERS.clear();
        ACTIVE_FLIGHTS.clear();
        return aborted;
    }

    public void abort() {
        flying = false;
        phase = MissilePhase.DEAD;
        status = "aborted";
        if (activeShipId >= 0) {
            ACTIVE_FLIGHTS.remove(activeShipId);
            ACTIVE_FLIGHT_DIMS.remove(activeShipId);
            activeShipId = -1L;
        }
        // Sole cleanup path for the static map (see ensureRegistered). A relaunch goes
        // through getOrCreate(), so dropping this instance is safe.
        CONTROLLERS.values().remove(this);
    }

    /** Game-thread phase machine + Y-corridor update. */
    public void gameTick(ServerSubLevel ship) {
        if (!flying || phase == MissilePhase.DEAD) return;
        // Do this before any phase work so physics knows the command source is alive.
        lastGuidanceNanos = System.nanoTime();
        flightAge++;
        phaseAge++;

        if (flightAge > maxFlightTicks) {
            impactRequested = true;
            status = "timeout fuse";
            return;
        }

        // Refresh mass occasionally (assemblies can change)
        if (flightAge % 40 == 1) {
            sampleMassAndCom(ship);
        }

        Vector3dc pos = VsShipHelper.worldPosition(ship);
        Vector3dc vel = VsShipHelper.velocity(ship.getLevel(), ship);
        double dist = Math.sqrt(pos.distanceSquared(targetX, targetY, targetZ));
        double hitRange = Math.max(Math.max(HIT_RANGE, hullArrivalRadius), guidanceStopDistance);
        if (hasPreviousPosition && segmentDistanceSquared(previousX, previousY, previousZ,
                pos.x(), pos.y(), pos.z(), targetX, targetY, targetZ) <= hitRange * hitRange) {
            impactRequested = true;
            status = "arrived (swept intercept)";
            previousX = pos.x(); previousY = pos.y(); previousZ = pos.z();
            return;
        }
        previousX = pos.x(); previousY = pos.y(); previousZ = pos.z();
        hasPreviousPosition = true;
        double groundFrac = groundTrackFraction(pos.x(), pos.z());
        double horizErr = horizontalError(pos.x(), pos.z());
        double apexHoriz = Math.sqrt(
                (apexX - pos.x()) * (apexX - pos.x()) + (apexZ - pos.z()) * (apexZ - pos.z()));

        // BOOST tracks loft peak; COAST tracks cruise altitude (may be lower → pitch-over).
        if (phase == MissilePhase.EJECT || phase == MissilePhase.BOOST) {
            desiredY = Math.max(pos.y() + 2.0, Math.min(apexY, launchY + (apexY - launchY) * 0.15 + 8.0));
            desiredY = Math.min(desiredY, apexY);
        } else if (phase == MissilePhase.COAST) {
            desiredY = cruiseY; // level cruise (or glide-down toward cruise if still high)
        } else {
            // Terminal: blend from cruise Y toward target Y
            double blend = Math.min(1.0, Math.max(0.0, 1.0 - horizErr / Math.max(64.0, terminalRange * 2.0)));
            desiredY = cruiseY + (targetY - cruiseY) * blend;
        }

        // pastApex = loft peak reached (not cruise altitude)
        double heightFrac = (apexY - launchY) > 1
                ? (pos.y() - launchY) / (apexY - launchY) : 1.0;
        if (!pastApex && (pos.y() >= apexY - 3.0
                || (heightFrac >= 0.94 && vel.y() < 2.0))) {
            pastApex = true;
        }

        // Rate-limited AND demand-gated: formatting for a readout nobody has opened is pure work
        // inside the physics tick, multiplied by every missile in flight.
        boolean logStatus = (flightAge % 10 == 0) && statusWanted();

        double thrScale = 1.0 + Math.min(8, thrusterBonus) * 0.12;
        // Mass does not reduce accel (F∝m), but VS drag/assembly noise does — give heavies more climb authority.
        double heavy = Math.min(2.2, Math.max(1.0, Math.pow(shipMass / 60_000.0, 0.35)));
        double baseA = boostAccel * ACCEL_TO_SI * thrScale * heavy;
        // Extra climb punch while still well below loft Y
        double climbA = baseA * (heightFrac < 0.85 ? 1.25 : 1.0);

        // Open terminal guidance early enough to brake at the current closing speed.
        // A fixed distance alone lets fast or very heavy hulls fly through the target.
        closestTargetDistance = Math.min(closestTargetDistance, dist);
        double closingSpeed = 0.0;
        if (vel != null && dist > 1.0e-6) {
            closingSpeed = (vel.x() * (targetX - pos.x())
                    + vel.y() * (targetY - pos.y())
                    + vel.z() * (targetZ - pos.z())) / dist;
        }
        double stoppingDistance = closingSpeed > 0.0
                ? closingSpeed * closingSpeed / (2.0 * Math.max(1.0, baseA * TERMINAL_GAIN)) : 0.0;
        double adaptiveTerminalRange = Math.max(terminalRange,
                Math.min(Math.max(terminalRange, rangeHorizontal * 0.35),
                        stoppingDistance * 1.45 + hitRange * 2.0));
        boolean passedTargetPlane = groundFrac >= 1.0;
        if (terminalEnabled && phase != MissilePhase.EJECT && phase != MissilePhase.TERMINAL
                && (dist <= adaptiveTerminalRange || groundFrac >= 0.94)) {
            phase = MissilePhase.TERMINAL;
            phaseAge = 0;
            status = String.format("TERMINAL brake d=%.0f stop=%.0f", dist, stoppingDistance);
        }

        // Trigger only after launcher clearance. Once active, the commanded world-space
        // pitch is held toward the target bearing until terminal guidance takes over.
        if (!angleCommandExecuted && angleCommandY > 0.0 && phase != MissilePhase.EJECT
                && phase != MissilePhase.TERMINAL && pos.y() >= angleCommandY) {
            angleCommandExecuted = true;
            phase = MissilePhase.COAST;
            phaseAge = 0;
            status = String.format("PITCH PROGRAM %.1f° at Y %.0f", angleCommandDeg, angleCommandY);
        }

        switch (phase) {
            case EJECT -> {
                // Pure vertical clear — never lean toward target on the pad
                setAttitude(0, 1, 0);
                publishThrust(0, 1, 0, climbA * 1.4);
                desiredY = Math.max(desiredY, pos.y() + 4.0);
                if (logStatus) {
                    status = String.format("EJECT %d/%d y=%.0f → loftY %.0f cruiseY %.0f m=%.0f",
                            phaseAge, EJECT_TICKS, pos.y(), apexY, cruiseY, shipMass);
                }
                if (phaseAge >= EJECT_TICKS || pos.y() > launchY + 16.0) {
                    phase = MissilePhase.BOOST;
                    phaseAge = 0;
                    aimAtApex(pos.x(), pos.y(), pos.z());
                    setAttitude(loftX, loftY, loftZ);
                    publishThrust(loftX, loftY, loftZ, climbA);
                    status = String.format("BOOST climb loftY=%.0f (then cruise %.0f) fuel=%d",
                            apexY, cruiseY, boostTicksLeft);
                }
            }
            case BOOST -> {
                // Climb to LOFT peak Y first — not cruise, not target.
                aimAtApex(pos.x(), pos.y(), pos.z());
                setAttitude(loftX, loftY, loftZ);
                publishThrust(loftX, loftY, loftZ, climbA);
                boolean heightOk = pos.y() >= apexY - 4.0;
                double verticalStoppingDistance = vel != null && vel.y() > 0.0
                        ? vel.y() * vel.y() / (2.0 * Math.max(1.0, baseA * MIDCOURSE_GAIN))
                        : 0.0;
                boolean mustBrakeForApex = vel != null && vel.y() > 1.0
                        && pos.y() + verticalStoppingDistance >= apexY - 4.0;
                boolean heightAlmost = pos.y() >= apexY - Math.max(12.0, (apexY - launchY) * 0.06)
                        && vel.y() < 1.2 && heightFrac >= 0.92;
                boolean angleSpeedReached = vel != null && vel.length() >= plannedCutoffSpeedMps;
                boolean fuelOut = boostSecondsLeft <= 1.0e-6 || angleSpeedReached;
                boolean coastReady = heightOk || heightAlmost || mustBrakeForApex || fuelOut;

                if (logStatus) {
                    status = String.format(
                            "BOOST loft y=%.0f→%.0f (%.0f%%) then cruiseY=%.0f fuel=%d",
                            pos.y(), apexY, heightFrac * 100, cruiseY, boostTicksLeft);
                }
                if (coastReady) {
                    if (fuelOut && !heightOk && !heightAlmost) {
                        status = String.format("FUEL OUT y=%.0f/%.0f — continuing guided coast",
                                pos.y(), apexY);
                    }
                    // Fuel exhaustion is not the same as reaching the planned apex.
                    // Guided coast may continue climbing, but terminal stays locked.
                    phase = MissilePhase.COAST;
                    phaseAge = 0;
                    if (!fuelOut) {
                        status = String.format("PITCH→cruiseY=%.0f (from loft %.0f) hErr=%.0f",
                                cruiseY, apexY, horizErr);
                    }
                }
            }
            case COAST -> {
                if (angleCommandExecuted) {
                    writeAltitudeAngleThrust(pos.x(), pos.z(), angleCommandDeg,
                            baseA * MIDCOURSE_GAIN);
                    ThrustCommand command = thrustCommand;
                    setAttitude(command.x, command.y, command.z);
                    if (logStatus) {
                        status = String.format("PITCH PROGRAM %.1f° y=%.0f d=%.0f",
                                angleCommandDeg, pos.y(), dist);
                    }
                } else {
                    // Still well below loft peak → climb again
                    if (boostTicksLeft > 0 && !pastApex
                        && heightFrac < 0.88 && pos.y() < apexY - 16.0) {
                        phase = MissilePhase.BOOST;
                        phaseAge = 0;
                        aimAtApex(pos.x(), pos.y(), pos.z());
                        setAttitude(loftX, loftY, loftZ);
                        publishThrust(loftX, loftY, loftZ, climbA);
                        status = String.format("REBOOST low y=%.0f/%.0f loft", pos.y(), apexY);
                        break;
                    }
                    if (advancedMode == BallisticFlightPlan.FlightMode.PURE_BALLISTIC) {
                        // Motor burnout: gravity and drag continue on the physics thread.
                        publishThrust(0, 0, 0, 0);
                    } else if (routeWaypointIndex < routeWaypoints.size()) {
                        while (routeWaypointIndex < routeWaypoints.size() - 1) {
                            Vec3 candidate = routeWaypoints.get(routeWaypointIndex);
                            double candidateFraction = groundTrackFraction(candidate.x, candidate.z);
                            if (candidateFraction + 0.025 >= groundFrac) break;
                            routeWaypointIndex++;
                        }
                        Vec3 waypoint = routeWaypoints.get(routeWaypointIndex);
                        double waypointDistance = waypoint.distanceTo(
                                new Vec3(pos.x(), pos.y(), pos.z()));
                        if (waypointDistance <= Math.max(20.0, hullArrivalRadius)) {
                            routeWaypointIndex++;
                            waypoint = routeWaypointIndex < routeWaypoints.size()
                                    ? routeWaypoints.get(routeWaypointIndex)
                                    : new Vec3(targetX, targetY, targetZ);
                        }
                        desiredY = waypoint.y;
                        writeWaypointThrust(pos.x(), pos.y(), pos.z(), vel, waypoint,
                                baseA * MIDCOURSE_GAIN);
                    } else {
                        // After loft: pitch down to cruise Y if loft > cruise, then level straight to target
                        writeLevelCruiseThrust(pos.x(), pos.y(), pos.z(), vel, horizErr,
                                baseA * MIDCOURSE_GAIN);
                    }
                    ThrustCommand command = thrustCommand;
                    if (advancedMode == BallisticFlightPlan.FlightMode.PURE_BALLISTIC && vel != null
                            && vel.lengthSquared() > 1.0e-6) {
                        setAttitude(vel.x(), vel.y(), vel.z());
                    } else {
                        // Point along the planned course, not the velocity-correction
                        // acceleration, which may temporarily be sideways or backward.
                        setAttitude(targetX - pos.x(), desiredY - pos.y(), targetZ - pos.z());
                    }
                }
                boolean atCruise = pos.y() <= cruiseY + 20.0 && pos.y() >= cruiseY - 40.0;
                if (logStatus) {
                    if (pos.y() > cruiseY + 24.0) {
                        status = String.format("GLIDE y=%.0f→cruise %.0f hErr=%.0f d=%.0f",
                                pos.y(), cruiseY, horizErr, dist);
                    } else {
                        status = String.format("CRUISE y=%.0f hold=%.0f hErr=%.0f d=%.0f",
                                pos.y(), cruiseY, horizErr, dist);
                    }
                }
                // Terminal only after loft was reached and near cruise altitude (or near target)
                boolean loftDone = pastApex;
                boolean routeReady = routeWaypoints.isEmpty() || routeWaypointIndex >= routeWaypoints.size() - 1;
                boolean byPlan = loftDone && groundFrac >= terminalGroundFrac && (atCruise || routeReady);
                boolean byRange = loftDone && (horizErr < terminalRange || dist < terminalRange * 1.3);
                boolean diving = loftDone && atCruise && vel.y() < -1.0 && groundFrac > peakGroundFrac + 0.08;
                if (terminalEnabled && (byPlan || byRange || diving)) {
                    phase = MissilePhase.TERMINAL;
                    phaseAge = 0;
                    status = String.format("TERMINAL dive d=%.0f hErr=%.0f fromY=%.0f",
                            dist, horizErr, pos.y());
                }
                if (dist < hitRange) {
                    impactRequested = true;
                    status = "arrived";
                }
            }
            case TERMINAL -> {
                writeTerminalThrust(pos.x(), pos.y(), pos.z(), vel, baseA * TERMINAL_GAIN);
                // Braking acceleration may point behind the craft. Keep the nose on
                // the target line instead of commanding a visually violent 180° flip.
                setAttitude(targetX - pos.x(), targetY - pos.y(), targetZ - pos.z(),
                        TERMINAL_ATTITUDE_SLEW_RADIANS);
                if (logStatus) {
                    status = String.format("TERMINAL d=%.0f hErr=%.0f y=%.0f→%.0f",
                            dist, horizErr, pos.y(), targetY);
                }
                if (dist < hitRange) {
                    impactRequested = true;
                    status = "arrived";
                } else if (passedTargetPlane && closingSpeed < 0.0
                        && closestTargetDistance <= hitRange * 1.5) {
                    impactRequested = true;
                    status = "arrived (closest approach)";
                }
            }
            default -> {
            }
        }

        if (phase != MissilePhase.EJECT) {
            double tightRange = Math.max(14.0, hitRange);
            if (horizErr < Math.max(7.0, hitRange)
                    && pos.y() < targetY + tightRange && dist < tightRange) {
                impactRequested = true;
                status = "arrived tight";
            }
        }
    }

    /**
     * Climb aim — height first, never straight at the target while below loft Y.
     * <ul>
     *   <li>Below ~85% loft: nearly pure vertical (tiny apex-XZ bias only)</li>
     *   <li>Near loft Y: pitch-over through apex XZ (still not pure target vector)</li>
     *   <li>Target XYZ only after COAST/TERMINAL</li>
     * </ul>
     */
    private void aimAtApex(double x, double y, double z) {
        double heightLeft = apexY - y;
        double heightNeed = Math.max(32.0, apexY - launchY);
        double heightFrac = Math.max(0.0, Math.min(1.0, 1.0 - heightLeft / heightNeed));

        double ax;
        double ay;
        double az;
        if (heightFrac < 0.85) {
            // PURE CLIMB: almost straight up. Tiny bias toward apex XZ (not target).
            // Heavy / slow climbers stay vertical until loft Y is nearly done.
            double horizBias = 0.08 + 0.12 * heightFrac; // 0.08 → 0.20 max before pitch-over
            ax = (apexX - x) * horizBias;
            az = (apexZ - z) * horizBias;
            ay = Math.max(heightLeft, heightNeed * 0.55);
        } else {
            // Pitch-over only once high: through apex XZ, still hold altitude, NOT dive to target
            double over = Math.min(1.0, (heightFrac - 0.85) / 0.15);
            ax = (apexX - x) * (0.35 + 0.65 * over);
            az = (apexZ - z) * (0.35 + 0.65 * over);
            ay = Math.max(6.0, heightLeft + 10.0 * (1.0 - over));
        }

        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0e-6) {
            loftX = 0;
            loftY = 1;
            loftZ = 0;
        } else {
            loftX = ax / len;
            loftY = ay / len;
            loftZ = az / len;
            // Hard floor: while below loft Y, thrust must stay mostly vertical
            double minY = heightFrac < 0.85 ? 0.82 : (heightLeft > 8.0 ? 0.55 : 0.35);
            if (loftY < minY) {
                loftY = minY;
                double h = Math.sqrt(loftX * loftX + loftZ * loftZ);
                if (h > 1.0e-4) {
                    double s = Math.sqrt(Math.max(0.0, 1.0 - loftY * loftY)) / h;
                    loftX *= s;
                    loftZ *= s;
                } else {
                    loftX = 0;
                    loftZ = 0;
                    loftY = 1;
                }
            }
        }
        solvedLoftX = loftX;
        solvedLoftY = loftY;
        solvedLoftZ = loftZ;
    }

    private void publishThrust(double x, double y, double z, double accelSi) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            thrustCommand = new ThrustCommand(0, 1, 0, Math.max(0.0, accelSi));
        } else {
            thrustCommand = new ThrustCommand(
                    x / len, y / len, z / len, Math.max(0.0, accelSi));
        }
    }

    /** Publishes an acceleration vector while preserving its requested magnitude. */
    private void publishAcceleration(double x, double y, double z, double maxAccelSi) {
        double len = Math.sqrt(x * x + y * y + z * z);
        double limit = Math.max(0.0, maxAccelSi);
        if (len < 1.0e-6 || limit <= 0.0) {
            thrustCommand = new ThrustCommand(0, 1, 0, 0);
            return;
        }
        thrustCommand = new ThrustCommand(x / len, y / len, z / len, Math.min(len, limit));
    }

    /** Commands an exact elevation angle in the vertical plane containing the target. */
    private void writeAltitudeAngleThrust(double x, double z, double pitchDegrees,
                                          double accelSi) {
        double dx = targetX - x;
        double dz = targetZ - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-6) {
            dx = 1.0;
            dz = 0.0;
            horizontal = 1.0;
        }
        double pitch = Math.toRadians(Math.max(-89.0, Math.min(89.0, pitchDegrees)));
        double horizontalScale = Math.cos(pitch) / horizontal;
        publishThrust(dx * horizontalScale, Math.sin(pitch), dz * horizontalScale, accelSi);
    }

    /**
     * After loft: hold/glide to {@link #cruiseY}, fly straight toward target XZ.
     * If still above cruiseY (loft was higher), pitch over while ranging.
     */
    private void writeLevelCruiseThrust(double x, double y, double z, Vector3dc vel,
                                        double horizErr, double accelSi) {
        double ax = targetX - x;
        double az = targetZ - z;
        // Target altitude = cruise Y (not loft peak)
        double ay = cruiseY - y;
        if (vel != null) {
            ay -= vel.y() * 0.45;
            ax -= vel.x() * 0.12;
            az -= vel.z() * 0.12;
        }
        double horiz = Math.sqrt(ax * ax + az * az);
        // Still high above cruise: allow stronger descent while moving toward target
        if (y > cruiseY + 30.0) {
            // Pitch-over: keep going down to cruise, but also range toward target
            ay = Math.min(ay, -Math.max(8.0, (y - cruiseY) * 0.35));
            if (horiz > 1.0) {
                // Blend some vertical into horizontal aim strength
                double downBias = Math.min(0.55, (y - cruiseY) / Math.max(80.0, apexY - cruiseY + 1.0));
                // leave ay as-is; ensure horizontal component remains dominant when far
                if (horizErr > terminalRange * 2.0 && Math.abs(ay) > horiz * 0.9) {
                    ay = -horiz * (0.25 + 0.35 * downBias);
                }
            }
        } else if (horizErr > terminalRange * 1.5) {
            // Near cruise altitude: mostly level
            double maxV = Math.max(0.25, Math.min(1.2, Math.abs(ay) / 40.0 + 0.2));
            if (ay > 0) ay = Math.min(ay, maxV * Math.max(1.0, horiz * 0.15));
            if (ay < 0) ay = Math.max(ay, -maxV * Math.max(1.0, horiz * 0.12));
        }
        publishThrust(ax, ay, az, accelSi);
    }

    /**
     * Legacy corridor glide (kept for any external callers / tests).
     */
    private void writeGlideThrust(double x, double y, double z, Vector3dc vel,
                                  double groundFrac, double accelSi) {
        writeLevelCruiseThrust(x, y, z, vel,
                Math.sqrt((targetX - x) * (targetX - x) + (targetZ - z) * (targetZ - z)),
                accelSi);
    }

    private void writeTerminalThrust(double x, double y, double z, Vector3dc vel, double accelSi) {
        AccelerationCommand command = terminalAcceleration(
                targetX - x, targetY - y, targetZ - z,
                vel == null ? 0.0 : vel.x(),
                vel == null ? 0.0 : vel.y(),
                vel == null ? 0.0 : vel.z(), accelSi);
        publishAcceleration(command.x, command.y, command.z, accelSi);
    }

    /**
     * Velocity-vector terminal guidance. The old implementation normalized the
     * velocity error and therefore applied full thrust even for tiny corrections.
     * This retains error magnitude, damps cross-track velocity, and keeps a minimum
     * impact speed instead of trying to hover at the target.
     */
    static AccelerationCommand terminalAcceleration(double rx, double ry, double rz,
                                                     double vx, double vy, double vz,
                                                     double maxAccelSi) {
        double distance = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double limit = Math.max(0.0, maxAccelSi);
        if (distance < 1.0e-6 || limit <= 0.0) {
            return new AccelerationCommand(0, 0, 0);
        }
        double nx = rx / distance;
        double ny = ry / distance;
        double nz = rz / distance;

        // Non-zero floor makes this an impact trajectory, while the square-root
        // profile starts braking early enough for large or fast VS ships.
        double desiredSpeed = Math.max(8.0,
                Math.min(240.0, Math.sqrt(2.0 * Math.max(1.0, limit) * distance) * 0.82));
        double timeToGo = distance / desiredSpeed;
        double responseTime = Math.max(0.20, Math.min(1.25, timeToGo * 0.45));
        double ax = (nx * desiredSpeed - vx) / responseTime;
        double ay = (ny * desiredSpeed - vy) / responseTime;
        double az = (nz * desiredSpeed - vz) / responseTime;

        double magnitude = Math.sqrt(ax * ax + ay * ay + az * az);
        if (magnitude > limit && magnitude > 1.0e-9) {
            double scale = limit / magnitude;
            ax *= scale;
            ay *= scale;
            az *= scale;
        }
        return new AccelerationCommand(ax, ay, az);
    }

    static double segmentDistanceSquared(double ax, double ay, double az,
                                         double bx, double by, double bz,
                                         double px, double py, double pz) {
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double apx = px - ax, apy = py - ay, apz = pz - az;
        double denom = abx * abx + aby * aby + abz * abz;
        double t = denom <= 1.0e-12 ? 0.0 : (apx * abx + apy * aby + apz * abz) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = ax + abx * t - px;
        double dy = ay + aby * t - py;
        double dz = az + abz * t - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void writeWaypointThrust(double x, double y, double z, Vector3dc vel,
                                     Vec3 waypoint, double accelSi) {
        double rx = waypoint.x - x;
        double ry = waypoint.y - y;
        double rz = waypoint.z - z;
        double distance = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (distance < 1.0e-6) {
            publishThrust(0, 0, 0, 0);
            return;
        }
        // Arrival-speed guidance: start braking before the waypoint instead of aiming
        // full thrust at it until after crossing its altitude/plane.
        double desiredSpeed = Math.max(8.0,
                Math.min(240.0, Math.sqrt(2.0 * Math.max(1.0, accelSi) * distance) * 0.72));
        double ax = rx / distance * desiredSpeed;
        double ay = ry / distance * desiredSpeed;
        double az = rz / distance * desiredSpeed;
        if (vel != null) {
            ax -= vel.x();
            ay -= vel.y();
            az -= vel.z();
        }
        publishThrust(ax, ay, az, accelSi);
    }

    private double groundTrackFraction(double x, double z) {
        double dx = targetX - launchX;
        double dz = targetZ - launchZ;
        double range = Math.sqrt(dx * dx + dz * dz);
        if (range < 1.0) return 1.0;
        double px = x - launchX;
        double pz = z - launchZ;
        double along = (px * dx + pz * dz) / (range * range);
        return Math.max(0.0, Math.min(1.5, along));
    }

    private double horizontalError(double x, double z) {
        double dx = targetX - x;
        double dz = targetZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void setAttitude(double x, double y, double z) {
        setAttitude(x, y, z, ATTITUDE_SLEW_RADIANS);
    }

    private void setAttitude(double x, double y, double z, double maxSlewRadians) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) return;
        x /= len; y /= len; z /= len;
        AttitudeCommand previous = attitudeCommand;
        attitudeCommand = slewDirection(previous, x, y, z, maxSlewRadians);
    }

    /**
     * Starts guidance with the roll the assembled ship actually has on the launch pad.
     * The three-point body calibration defines a nose axis, but cannot define roll because
     * base, center, and nose lie on the same line. Capturing the live pose avoids inventing an
     * arbitrary world-up direction and makes existing ships retain their authored orientation.
     */
    private AttitudeCommand initialAttitude(ServerSubLevel ship, double nx, double ny, double nz) {
        try {
            Quaterniond rotation = new Quaterniond(ship.logicalPose().orientation()).normalize();
            Vector3d currentNose = rotation.transform(
                    new Vector3d(bodyNoseX, bodyNoseY, bodyNoseZ)).normalize();
            Vector3d currentUp = rotation.transform(
                    new Vector3d(bodyUpX, bodyUpY, bodyUpZ)).normalize();
            return transportRoll(new AttitudeCommand(
                    currentNose.x, currentNose.y, currentNose.z,
                    currentUp.x, currentUp.y, currentUp.z), nx, ny, nz);
        } catch (Throwable ignored) {
            return transportRoll(new AttitudeCommand(bodyNoseX, bodyNoseY, bodyNoseZ,
                    bodyUpX, bodyUpY, bodyUpZ), nx, ny, nz);
        }
    }

    /** Slews a unit direction without the zero-vector singularity of linear interpolation. */
    static AttitudeCommand slewDirection(AttitudeCommand from, double tx, double ty, double tz,
                                         double maxRadians) {
        double dot = Math.max(-1.0, Math.min(1.0, from.x * tx + from.y * ty + from.z * tz));
        double angle = Math.acos(dot);
        if (angle <= maxRadians || angle < 1.0e-8) return transportRoll(from, tx, ty, tz);

        double ax = from.y * tz - from.z * ty;
        double ay = from.z * tx - from.x * tz;
        double az = from.x * ty - from.y * tx;
        double axisLength = Math.sqrt(ax * ax + ay * ay + az * az);
        if (axisLength < 1.0e-7) {
            // Exact opposite direction: choose a deterministic perpendicular axis.
            if (Math.abs(from.y) < 0.9) {
                ax = -from.z; ay = 0.0; az = from.x;
            } else {
                ax = 0.0; ay = from.z; az = -from.y;
            }
            axisLength = Math.sqrt(ax * ax + ay * ay + az * az);
        }
        ax /= axisLength; ay /= axisLength; az /= axisLength;
        double c = Math.cos(maxRadians), s = Math.sin(maxRadians);
        double crossX = ay * from.z - az * from.y;
        double crossY = az * from.x - ax * from.z;
        double crossZ = ax * from.y - ay * from.x;
        double axisDot = ax * from.x + ay * from.y + az * from.z;
        double nx = from.x * c + crossX * s + ax * axisDot * (1.0 - c);
        double ny = from.y * c + crossY * s + ay * axisDot * (1.0 - c);
        double nz = from.z * c + crossZ * s + az * axisDot * (1.0 - c);
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return transportRoll(from, nx / n, ny / n, nz / n);
    }

    /**
     * Parallel-transports the roll-up vector through the shortest nose rotation. This keeps
     * roll continuous through climbs and dives without chasing a fixed world-up vector (which
     * becomes singular when the missile points vertically).
     */
    static AttitudeCommand transportRoll(AttitudeCommand from, double nx, double ny, double nz) {
        double noseLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (noseLength < 1.0e-9) return from;
        nx /= noseLength; ny /= noseLength; nz /= noseLength;

        double fx = from.x, fy = from.y, fz = from.z;
        double fromLength = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fromLength < 1.0e-9) {
            fx = 0; fy = 1; fz = 0;
        } else {
            fx /= fromLength; fy /= fromLength; fz /= fromLength;
        }

        double ax = fy * nz - fz * ny;
        double ay = fz * nx - fx * nz;
        double az = fx * ny - fy * nx;
        double sin = Math.sqrt(ax * ax + ay * ay + az * az);
        double dot = Math.max(-1.0, Math.min(1.0, fx * nx + fy * ny + fz * nz));

        double ux = from.upX, uy = from.upY, uz = from.upZ;
        if (sin > 1.0e-9) {
            ax /= sin; ay /= sin; az /= sin;
            double angle = Math.atan2(sin, dot);
            double c = Math.cos(angle), s = Math.sin(angle);
            double crossX = ay * uz - az * uy;
            double crossY = az * ux - ax * uz;
            double crossZ = ax * uy - ay * ux;
            double axisDot = ax * ux + ay * uy + az * uz;
            ux = ux * c + crossX * s + ax * axisDot * (1.0 - c);
            uy = uy * c + crossY * s + ay * axisDot * (1.0 - c);
            uz = uz * c + crossZ * s + az * axisDot * (1.0 - c);
        } else if (dot < 0.0) {
            // A true 180-degree reversal has no unique shortest axis. Rotating about the
            // current roll-up vector flips the nose while preserving roll deterministically.
            double projection = ux * fx + uy * fy + uz * fz;
            ux -= fx * projection; uy -= fy * projection; uz -= fz * projection;
        }

        // Remove accumulated floating-point drift and recover from a malformed legacy frame.
        double projection = ux * nx + uy * ny + uz * nz;
        ux -= nx * projection; uy -= ny * projection; uz -= nz * projection;
        double upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (upLength < 1.0e-9) {
            if (Math.abs(ny) < 0.9) {
                ux = 0; uy = 1; uz = 0;
            } else {
                ux = 0; uy = 0; uz = 1;
            }
            projection = ux * nx + uy * ny + uz * nz;
            ux -= nx * projection; uy -= ny * projection; uz -= nz * projection;
            upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        }
        return new AttitudeCommand(nx, ny, nz, ux / upLength, uy / upLength, uz / upLength);
    }

    public boolean consumeImpact() {
        if (!impactRequested) return false;
        impactRequested = false;
        flying = false;
        phase = MissilePhase.DEAD;
        if (activeShipId >= 0) {
            ACTIVE_FLIGHTS.remove(activeShipId);
            ACTIVE_FLIGHT_DIMS.remove(activeShipId);
            activeShipId = -1L;
        }
        CONTROLLERS.values().remove(this);
        return true;
    }

    // -------------------------------------------------------------------------
    // Physics thread
    // -------------------------------------------------------------------------

    private void physTick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaSeconds) {
        if (!flying || phase == MissilePhase.DEAD) return;

        long physicsNow = System.nanoTime();
        double physicsDeltaSeconds = Math.max(0.0, Math.min(0.1, deltaSeconds));
        physicsStepSeconds = physicsDeltaSeconds;
        lastPhysicsNanos = physicsNow;

        double mass = shipMass;
        try {
            double pm = subLevel.getMassTracker().getMass();
            if (pm > 1.0) {
                mass = pm;
                shipMass = pm;
            }
        } catch (Throwable ignored) {
        }
        mass = Math.max(100.0, mass);

        // Prefer live CoM from phys ship (model space)
        try {
            Vector3dc com = subLevel.getMassTracker().getCenterOfMass();
            if (com != null) {
                comModelX = com.x();
                comModelY = com.y();
                comModelZ = com.z();
                hasCom = true;
            }
        } catch (Throwable ignored) {
        }

        Vector3dc pos = subLevel.logicalPose().position();
        Vector3dc vel = handle.getLinearVelocity();

        // Physics-side swept intercept. This keeps working if the Minecraft game
        // thread stalls while VS continues simulating the ship.
        double dxTarget = targetX - pos.x();
        double dyTarget = targetY - pos.y();
        double dzTarget = targetZ - pos.z();
        double physDistance = Math.sqrt(dxTarget * dxTarget + dyTarget * dyTarget + dzTarget * dzTarget);
        double arrivalRadius = Math.max(Math.max(HIT_RANGE, hullArrivalRadius), guidanceStopDistance);
        boolean sweptArrival = hasPhysPreviousPosition
                && segmentDistanceSquared(physPreviousX, physPreviousY, physPreviousZ,
                pos.x(), pos.y(), pos.z(), targetX, targetY, targetZ)
                <= arrivalRadius * arrivalRadius;
        physClosestTargetDistance = Math.min(physClosestTargetDistance, physDistance);
        double closingSpeed = vel != null && physDistance > 1.0e-6
                ? (vel.x() * dxTarget + vel.y() * dyTarget + vel.z() * dzTarget) / physDistance
                : 0.0;
        boolean crossedAtClosestApproach = groundTrackFraction(pos.x(), pos.z()) >= 1.0
                && closingSpeed < 0.0 && physClosestTargetDistance <= arrivalRadius * 1.5;
        physPreviousX = pos.x();
        physPreviousY = pos.y();
        physPreviousZ = pos.z();
        hasPhysPreviousPosition = true;
        if (physDistance <= arrivalRadius || sweptArrival || crossedAtClosestApproach) {
            impactRequested = true;
            status = sweptArrival ? "arrived (physics sweep)" : "arrived (physics intercept)";
            applyLagBrake(handle, subLevel, mass, vel, true);
            return;
        }

        long heartbeatAge = physicsNow - lastGuidanceNanos;
        if (heartbeatAge > GUIDANCE_STALE_NANOS) {
            status = "guidance hold (server lag)";
            applyLagBrake(handle, subLevel, mass, vel, false);
            return;
        }

        if (phase == MissilePhase.BOOST && boostSecondsLeft > 0.0) {
            boostSecondsLeft = Math.max(0.0, boostSecondsLeft - physicsDeltaSeconds);
            boostTicksLeft = (int) Math.ceil(boostSecondsLeft * 20.0);
        }

        // SAS nose → loft plan aim (thrust vector = apex / glide / terminal)
        // One volatile read gives physics one coherent direction/acceleration command.
        ThrustCommand command = thrustCommand;
        if (autoStable) {
            applySasToLoft(handle, subLevel, mass, command);
        }

        // Thrust direction + accel published by gameTick — no re-solve on phys thread
        double a = command.accelSi;

        double desY = desiredY;
        double yErr = desY - pos.y();
        double vy = vel != null ? vel.y() : 0.0;

        scratchForce.set(command.x * mass * a, command.y * mass * a, command.z * mass * a);
        // VS2 already applies about 10 m/s² downward. Offset only the difference
        // so the selected calculator gravity is also the live flight gravity.
        scratchForce.y += mass * (VS_WORLD_GRAVITY_SI - gravitySi);

        // Phase-light altitude / gravity assist (no Vec3, no aim recompute)
        if (phase == MissilePhase.EJECT || phase == MissilePhase.BOOST) {
            // Full counter-G + climb assist so heavy ships still go UP, not crawl sideways
            scratchForce.y += mass * gravitySi * (phase == MissilePhase.EJECT ? 0.15 : 0.05);
            if (yErr > 2.0) {
                double altA = Math.min(a * 0.75, yErr * ALT_P * 1.2 + Math.max(0, -vy) * ALT_D);
                scratchForce.y += mass * altA;
            }
            // NEVER add target-horizontal force during boost.
            // That was yanking heavy ships flat into a straight line at the target.
            // Only a tiny soft pull toward planned apex XZ (climb corridor), and only if
            // already above half loft height so we do not burn range early.
            if (phase == MissilePhase.BOOST && pos.y() > launchY + (apexY - launchY) * 0.5) {
                double ax = apexX - pos.x();
                double az = apexZ - pos.z();
                double alen = Math.sqrt(ax * ax + az * az);
                if (alen > 12.0) {
                    double nh = a * 0.06; // tiny; climb vector already has bias
                    scratchForce.x += (ax / alen) * mass * nh;
                    scratchForce.z += (az / alen) * mass * nh;
                }
            }
        } else if (phase == MissilePhase.COAST
                && advancedMode != BallisticFlightPlan.FlightMode.PURE_BALLISTIC) {
            double altCmd = yErr * ALT_P - vy * ALT_D;
            altCmd = Math.max(-a * 0.6, Math.min(a * 0.8, altCmd));
            scratchForce.y += mass * (altCmd + gravitySi * 0.2);
            // Waypoint steering plus aerodynamic drag already provide damping. The
            // old extra horizontal brake destroyed velocity on map-scale routes.
        }

        // Quadratic aerodynamic loss: a_drag = k * |v|² opposite velocity.
        // Capped only to keep a bad GUI value from destabilizing the VS solver.
        if (vel != null && dragCoefficient > 0.0) {
            double speed = vel.length();
            if (speed > 1.0e-6) {
                double density = net.bullettrain.xenopixelsmod.missile.BallisticCalculator
                        .airDensityFactor(pos.y());
                double dragAccel = Math.min(50.0, dragCoefficient * density * speed * speed);
                double scale = mass * dragAccel / speed;
                scratchForce.x -= vel.x() * scale;
                scratchForce.y -= vel.y() * scale;
                scratchForce.z -= vel.z() * scale;
            }
        }
        applyComForce(handle, subLevel, scratchForce);
    }

    /**
     * Fail-safe used while the game thread is stale and while an impact waits to be
     * consumed. It never reuses the old thrust command: velocity is cancelled with a
     * bounded acceleration and the configured gravity correction remains active.
     */
    private void applyLagBrake(RigidBodyHandle handle, ServerSubLevel subLevel,
                               double mass, Vector3dc velocity,
                               boolean emergency) {
        double vx = velocity == null ? 0.0 : velocity.x();
        double vy = velocity == null ? 0.0 : velocity.y();
        double vz = velocity == null ? 0.0 : velocity.z();
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double brakeAccel = Math.max(12.0,
                Math.min(emergency ? 100.0 : 70.0,
                        boostAccel * ACCEL_TO_SI * (emergency ? 2.0 : 1.35)));
        if (speed > 1.0e-6) {
            double scale = mass * brakeAccel / speed;
            scratchForce.set(-vx * scale, -vy * scale, -vz * scale);
            ThrustCommand brake = new ThrustCommand(-vx / speed, -vy / speed, -vz / speed, brakeAccel);
            if (autoStable) applySasToLoft(handle, subLevel, mass, brake);
        } else {
            scratchForce.zero();
        }
        scratchForce.y += mass * (VS_WORLD_GRAVITY_SI - gravitySi);
        applyComForce(handle, subLevel, scratchForce);
    }

    /**
     * Apply world-space force through body CoM (zero lever arm → no tumble from offset).
     *
     * <p>Sable's impulse API takes BODY-space vectors, so the world-space force is rotated
     * into body space first. Sable's own {@code FloatingBlockController} transformInverse()s
     * world velocity and gravity into body space before filling the impulse vectors it hands
     * to this same call, and {@code ReactionWheelManager} transformInverse()s immediately
     * before applying. Passing world-space force made thrust come out rotated by the hull's
     * orientation — backwards for a hull facing 180 degrees off.
     */
    private void applyComForce(RigidBodyHandle handle, ServerSubLevel subLevel, Vector3d worldForce) {
        scratchAuxTorque.set(worldForce).mul(physicsStepSeconds);
        subLevel.logicalPose().orientation().transformInverse(scratchAuxTorque);
        handle.applyLinearImpulse(scratchAuxTorque);
    }

    /**
     * SAS to loft plan: damp rates, kill roll, point ship +Y (nose) at the same
     * direction we thrust — apex XYZ on boost, corridor on glide, target on terminal.
     */
    private void applySasToLoft(RigidBodyHandle handle, ServerSubLevel subLevel,
                                double mass, ThrustCommand command) {
        try {
            // Attitude and correction force are deliberately separate. Terminal
            // velocity cancellation can point backward while the nose must remain
            // on the intercept line.
            AttitudeCommand attitude = attitudeCommand;
            double ax = attitude.x;
            double ay = attitude.y;
            double az = attitude.z;
            double alen = Math.sqrt(ax * ax + ay * ay + az * az);
            if (alen < 1.0e-4) {
                ax = command.x;
                ay = command.y;
                az = command.z;
                alen = Math.sqrt(ax * ax + ay * ay + az * az);
            }
            if (alen < 1.0e-4) {
                ax = 0;
                ay = 1;
                az = 0;
                alen = 1;
            }
            ax /= alen;
            ay /= alen;
            az /= alen;

            // Phase: stronger hold during boost (to apex) and terminal (to target)
            double alignMul = switch (phase) {
                case EJECT -> 1.1;
                case BOOST -> 1.45;   // hard lock toward loft Y / apex
                case COAST -> 1.0;    // glide
                case TERMINAL -> 1.35;
                default -> 0.8;
            };
            double maxAngularAccel = SAS_MAX_ANGULAR_ACCEL
                    * (phase == MissilePhase.BOOST ? 1.15 : 1.0);

            Vector3dc omega = handle.getAngularVelocity();

            // Build a desired WORLD angular acceleration first. Converting it through
            // the full body inertia tensor below gives long/asymmetric ships the same
            // response on every axis; the old mean-inertia scalar made one axis violent
            // while another was too weak.
            scratchTorque.zero();
            if (omega != null && omega.lengthSquared() > 1.0e-8) {
                // Quaternion PD derivative term: damp all body rotation in world space.
                scratchTorque.set(omega).mul(
                        -SAS_OMEGA_GAIN * (0.85 + 0.15 * alignMul));
            }

            // Track the calibrated nose strongly. Roll uses a separately transported launch
            // reference below, so it stays continuous instead of chasing fixed world-up near
            // vertical flight (the source of the old full-quaternion shaking).
            double strength = SAS_ALIGN_BASE * 5.5 * alignMul;
            addNoseTrackingAcceleration(subLevel, ax, ay, az, strength, scratchTorque);
            addRollTrackingAcceleration(subLevel, attitude,
                    SAS_ALIGN_BASE * 1.4 * alignMul, scratchTorque);
            // Cap the combined damping + alignment acceleration, then convert it to
            // the exact torque this hull needs around each principal body axis.
            clampTorque(scratchTorque, maxAngularAccel);
            if (scratchTorque.lengthSquared() > 1.0e-10) {
                applyInertiaCompensatedTorque(handle, subLevel, scratchTorque, mass);
            }
        } catch (Throwable ignored) {
        }
    }

    private double meanMomentOfInertia(ServerSubLevel subLevel, double mass) {
        try {
            var tensor = subLevel.getMassTracker().getInertiaTensor();
            double mean = (Math.abs(tensor.m00()) + Math.abs(tensor.m11())
                    + Math.abs(tensor.m22())) / 3.0;
            if (Double.isFinite(mean) && mean > 1.0e-6) {
                return Math.max(mass * 0.05, Math.min(mass * 10_000.0, mean));
            }
        } catch (Throwable ignored) {
        }
        return mass;
    }

    private static void clampTorque(Vector3d t, double maxAbs) {
        double lenSq = t.lengthSquared();
        double maxSq = maxAbs * maxAbs;
        if (lenSq > maxSq && lenSq > 1.0e-12) {
            t.mul(maxAbs / Math.sqrt(lenSq));
        }
    }

    /** Converts a world-space angular-acceleration request through the model-space inertia tensor. */
    private void applyInertiaCompensatedTorque(RigidBodyHandle handle, ServerSubLevel subLevel,
                                                Vector3d angularAccelWorld,
                                                double mass) {
        try {
            scratchCurrentRotation.set(subLevel.logicalPose().orientation()).normalize();
            // The tensor is in model/body axes and Sable consumes body-space torque, so the
            // chain ends in body space: alphaWorld -> alphaBody -> I * alphaBody. Rotating the
            // result back to world (as this used to) applied SAS torque about the wrong axes.
            scratchAuxTorque.set(angularAccelWorld);
            scratchCurrentRotation.transformInverse(scratchAuxTorque);
            subLevel.getMassTracker().getInertiaTensor().transform(scratchAuxTorque);
            if (!Double.isFinite(scratchAuxTorque.x) || !Double.isFinite(scratchAuxTorque.y)
                    || !Double.isFinite(scratchAuxTorque.z)) return;
            applyBodyTorque(handle, scratchAuxTorque);
        } catch (Throwable ignored) {
            // Some hulls may not expose a usable tensor. Retain a conservative scalar
            // fallback instead of silently disabling SAS.
            scratchAuxTorque.set(angularAccelWorld).mul(meanMomentOfInertia(subLevel, mass));
            scratchCurrentRotation.set(subLevel.logicalPose().orientation()).normalize()
                    .transformInverse(scratchAuxTorque);
            applyBodyTorque(handle, scratchAuxTorque);
        }
    }

    /** Torque must already be in body/model axes — see {@link #applyComForce}. */
    private void applyBodyTorque(RigidBodyHandle handle, Vector3d bodyTorque) {
        scratchAuxTorque.set(bodyTorque).mul(physicsStepSeconds);
        handle.applyTorqueImpulse(scratchAuxTorque);
    }

    /** Adds world-space angular acceleration that directly aligns BASE→NOSE with guidance aim. */
    private void addNoseTrackingAcceleration(ServerSubLevel subLevel,
                                             double dx, double dy, double dz,
                                             double strength, Vector3d accumulator) {
        try {
            scratchCurrentRotation.set(subLevel.logicalPose().orientation()).normalize();
            scratchCurrentRotation.transform(scratchNose.set(bodyNoseX, bodyNoseY, bodyNoseZ));
            scratchNose.normalize();

            double dot = Math.max(-1.0, Math.min(1.0,
                    scratchNose.x * dx + scratchNose.y * dy + scratchNose.z * dz));
            double angle = Math.acos(dot);
            if (angle < Math.toRadians(0.20)) return;

            double axisX = scratchNose.y * dz - scratchNose.z * dy;
            double axisY = scratchNose.z * dx - scratchNose.x * dz;
            double axisZ = scratchNose.x * dy - scratchNose.y * dx;
            double axisLength = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (axisLength < 1.0e-7) {
                // Exact 180° error: use calibrated roll-up only to choose a stable
                // turn axis. Roll itself is deliberately unconstrained.
                scratchCurrentRotation.transform(scratchRight.set(bodyUpX, bodyUpY, bodyUpZ));
                axisX = scratchNose.y * scratchRight.z - scratchNose.z * scratchRight.y;
                axisY = scratchNose.z * scratchRight.x - scratchNose.x * scratchRight.z;
                axisZ = scratchNose.x * scratchRight.y - scratchNose.y * scratchRight.x;
                axisLength = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
                if (axisLength < 1.0e-7) return;
            }
            double scale = strength * angle / axisLength;
            accumulator.add(axisX * scale, axisY * scale, axisZ * scale);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Restores roll around the desired nose without changing the guidance direction.
     * Correction fades out during large nose errors so a hard pitch/yaw turn always wins.
     */
    private void addRollTrackingAcceleration(ServerSubLevel subLevel,
                                             AttitudeCommand desired,
                                             double strength, Vector3d accumulator) {
        try {
            scratchCurrentRotation.set(subLevel.logicalPose().orientation()).normalize();
            scratchCurrentRotation.transform(scratchNose.set(bodyNoseX, bodyNoseY, bodyNoseZ));
            scratchNose.normalize();
            double noseDot = Math.max(-1.0, Math.min(1.0,
                    scratchNose.x * desired.x + scratchNose.y * desired.y
                            + scratchNose.z * desired.z));
            // Avoid roll torque fighting the shortest nose correction during a hard turn.
            double authority = Math.max(0.0, Math.min(1.0,
                    (noseDot - Math.cos(Math.toRadians(35.0))) / 0.12));
            if (authority <= 0.0) return;

            scratchCurrentRotation.transform(scratchRight.set(bodyUpX, bodyUpY, bodyUpZ));
            double projection = scratchRight.x * desired.x
                    + scratchRight.y * desired.y + scratchRight.z * desired.z;
            scratchRight.sub(desired.x * projection, desired.y * projection,
                    desired.z * projection);
            if (scratchRight.lengthSquared() < 1.0e-9) return;
            scratchRight.normalize();

            scratchAuxTorque.set(desired.upX, desired.upY, desired.upZ);
            projection = scratchAuxTorque.x * desired.x
                    + scratchAuxTorque.y * desired.y + scratchAuxTorque.z * desired.z;
            scratchAuxTorque.sub(desired.x * projection, desired.y * projection,
                    desired.z * projection);
            if (scratchAuxTorque.lengthSquared() < 1.0e-9) return;
            scratchAuxTorque.normalize();

            double crossX = scratchRight.y * scratchAuxTorque.z
                    - scratchRight.z * scratchAuxTorque.y;
            double crossY = scratchRight.z * scratchAuxTorque.x
                    - scratchRight.x * scratchAuxTorque.z;
            double crossZ = scratchRight.x * scratchAuxTorque.y
                    - scratchRight.y * scratchAuxTorque.x;
            double sin = desired.x * crossX + desired.y * crossY + desired.z * crossZ;
            double cos = Math.max(-1.0, Math.min(1.0, scratchRight.dot(scratchAuxTorque)));
            double rollError = Math.atan2(sin, cos);
            accumulator.add(desired.x * rollError * strength * authority,
                    desired.y * rollError * strength * authority,
                    desired.z * rollError * strength * authority);
        } catch (Throwable ignored) {
        }
    }

    private void addQuaternionAttitudeTorque(ServerSubLevel subLevel,
                                             double dx, double dy, double dz,
                                             double strength, Vector3d accumulator) {
        try {
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-6 || strength < 1.0e-3) return;
            dx /= len;
            dy /= len;
            dz /= len;

            // Minimal swing maps the configured local nose (+Y) onto guidance aim.
            scratchDesiredRotation.identity().rotationTo(
                    bodyNoseX, bodyNoseY, bodyNoseZ, dx, dy, dz);

            // Preserve the current roll while swinging the nose. Switching between
            // fixed world-up axes near vertical created a discontinuous desired
            // quaternion and was the main source of sudden roll flips.
            scratchCurrentRotation.set(subLevel.logicalPose().orientation()).normalize();
            scratchDesiredRotation.transform(scratchNose.set(bodyUpX, bodyUpY, bodyUpZ));
            scratchCurrentRotation.transform(scratchRight.set(bodyUpX, bodyUpY, bodyUpZ));
            double projection = scratchRight.x * dx + scratchRight.y * dy + scratchRight.z * dz;
            scratchRight.sub(dx * projection, dy * projection, dz * projection);
            if (scratchRight.lengthSquared() < 1.0e-8) {
                // Current up is parallel to the desired nose; choose the least-aligned
                // world axis only for this true singularity.
                if (Math.abs(dy) < 0.8) scratchRight.set(0.0, 1.0, 0.0);
                else scratchRight.set(1.0, 0.0, 0.0);
                projection = scratchRight.x * dx + scratchRight.y * dy + scratchRight.z * dz;
                scratchRight.sub(dx * projection, dy * projection, dz * projection);
            }
            scratchRight.normalize();
            double crossX = scratchNose.y * scratchRight.z - scratchNose.z * scratchRight.y;
            double crossY = scratchNose.z * scratchRight.x - scratchNose.x * scratchRight.z;
            double crossZ = scratchNose.x * scratchRight.y - scratchNose.y * scratchRight.x;
            double rollSin = dx * crossX + dy * crossY + dz * crossZ;
            double rollCos = scratchNose.dot(scratchRight);
            double rollAngle = Math.atan2(rollSin, rollCos);
            scratchRollRotation.identity().rotationAxis(rollAngle, dx, dy, dz);
            scratchDesiredRotation.set(scratchRollRotation).mul(scratchDesiredRotation).normalize();

            // qError = qDesired * inverse(qCurrent), expressed in world axes.
            scratchCurrentRotation.conjugate();
            scratchErrorRotation.set(scratchDesiredRotation).mul(scratchCurrentRotation).normalize();
            // q and -q are the same attitude; positive W selects the shortest arc.
            if (scratchErrorRotation.w < 0.0) {
                scratchErrorRotation.set(-scratchErrorRotation.x, -scratchErrorRotation.y,
                        -scratchErrorRotation.z, -scratchErrorRotation.w);
            }
            double sinHalf = Math.sqrt(scratchErrorRotation.x * scratchErrorRotation.x
                    + scratchErrorRotation.y * scratchErrorRotation.y
                    + scratchErrorRotation.z * scratchErrorRotation.z);
            if (sinHalf < 1.0e-7) return;
            double angle = 2.0 * Math.atan2(sinHalf,
                    Math.max(0.0, scratchErrorRotation.w));
            scratchAuxTorque.set(scratchErrorRotation.x / sinHalf,
                    scratchErrorRotation.y / sinHalf,
                    scratchErrorRotation.z / sinHalf).mul(strength * angle);
            accumulator.add(scratchAuxTorque);
        } catch (Throwable ignored) {
        }
    }
}
