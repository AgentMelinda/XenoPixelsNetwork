package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.bullettrain.xenopixelsmod.compat.thruster.ExternalThrusterCompat;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.missile.BallisticTrajectory;
import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.bullettrain.xenopixelsmod.missile.BallisticPlanOptimizer;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.bullettrain.xenopixelsmod.vs.ShipBallisticController;
import net.bullettrain.xenopixelsmod.vs.ShipGravityControl;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.bullettrain.xenopixelsmod.vs.XenoThrusterControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Native ballistic guidance computer for XenoPixels.
 *
 * <p><b>Pair thrusters</b> (shift-click thruster, or GUI “Pair Nearby”), set XYZ target,
 * then <b>redstone pulse</b> → ship launches and paired thrusters follow flight phases.
 *
 * <p><b>Primary mode (on a VS2 ship):</b> hull is the ballistic missile via
 * {@link ShipBallisticController}. Paired thrusters provide boost scale + plume.
 */
public class ShipVlsGuidanceBlockEntity extends BlockEntity {
    private BlockPos target;
    private boolean wasPowered;
    private int searchRadius = 8;
    private boolean terminalGuidance = true;
    private double boostAccel = 0.35;
    private int boostTicks = 40;
    /**
     * User-facing missile speed / power 1–20 (GUI).
     * Higher = more accel + longer burn — use 12–20 for heavy hulls.
     */
    private int speedLevel = 5;
    /**
     * Loft peak (world Y) to climb to first. {@code 0} = auto from range/physics.
     */
    private int desiredApexY = 0;
    /**
     * Level-cruise altitude (world Y) after loft — fly straight to target here.
     * {@code 0} = same as loft peak (no separate glide-down).
     */
    private int desiredCruiseY = 0;
    /** Flight environment used by both preview calculator and live VS2 controller. */
    private double gravitySi = BallisticCalculator.EARTH_GRAVITY;
    private double dragCoefficient = BallisticCalculator.DEFAULT_DRAG;
    /** 0 = automatic hull-safe distance; otherwise requested target distance in blocks. */
    private double guidanceStopDistance;
    /** Persistent ship-local block calibration used by the body visualizer/SAS. */
    private @Nullable BlockPos missileBaseBlock;
    private @Nullable BlockPos missileCenterBlock;
    private @Nullable BlockPos missileNoseBlock;
    private BallisticFlightPlan.Settings plannerSettings = BallisticFlightPlan.Settings.defaults();
    private @Nullable BallisticFlightPlan.Result appliedPlan;
    /** VS2 moving-target designation; -1 means the normal fixed BlockPos target. */
    private long targetShipId = -1L;
    private int movingTargetMissingTicks;
    private int adaptiveReplanCooldown;
    /** 0 = standalone; positive values join a dimension-scoped fleet channel. */
    private int fleetChannel;
    /** Delay between vessels in a fleet salvo. */
    private int salvoIntervalTicks = 10;
    /** 0 = no explosion (default). Tube entity missiles only explode if set &gt; 0. */
    private float warheadYield = 0.0f;
    /** When true (default), firePulse launches the hosting VS ship as the warhead. */
    private boolean shipMissileMode = true;
    /** Also fire entity tubes when launching the ship (submunitions). Default false. */
    private boolean alsoFireTubes = false;
    /** Explicit thruster positions paired to this computer. */
    private final Set<BlockPos> pairedThrusters = new LinkedHashSet<>();
    private int lastLaunchCount;
    private int lastEtaTicks = -1;
    private double lastPitchDeg;
    private @Nullable BallisticCalculator.Result lastCalculation;
    private String lastStatus = "idle";
    private boolean commandingFlight;
    private int pruneCooldown;
    private MissilePhase lastSyncedPhase;
    private double lastSyncedThrottle = -1;
    /** Cached ship id for flight sync (avoid expensive pos scan every tick). */
    private long cachedShipId = -1L;
    private int shipCacheCooldown;
    /** Redstone edge from neighborChanged — no hasNeighborSignal every tick when idle. */
    private boolean cachedRedstone;
    private int redstoneSafetyPoll;
    /** EMA of actual server tick spacing, used for lag-aware ETA and target lead. */
    private transient long lastServerTickNanos;
    private transient double observedTickSeconds = 0.05;
    /** Guidance trajectory recalculation interval (ticks). Higher = better perf, less responsive. */
    private static final int GUIDANCE_UPDATE_INTERVAL = Math.max(3, Math.min(20, 
            net.bullettrain.xenopixelsmod.config.XenoPerfConfig.guidanceUpdateIntervalTicks));
    /** Cache ballistic solution longer when target is stationary (saves CPU). */
    private int stationaryTargetCacheTicks;
    private Vec3 lastKnownTargetPos;

    public ShipVlsGuidanceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_VLS_GUIDANCE.get(), pos, state);
    }

    private @Nullable LoadedServerShip resolveShipCached(ServerLevel sl) {
        return resolveShipCached(sl, false);
    }

    /**
     * @param force if true (during flight), never return null due to cooldown — miss must be real
     */
    private @Nullable LoadedServerShip resolveShipCached(ServerLevel sl, boolean force) {
        if (cachedShipId >= 0) {
            LoadedServerShip byId = VsShipHelper.getLoadedShipById(sl, cachedShipId);
            if (byId != null) return byId;
            cachedShipId = -1L;
        }
        if (!force && --shipCacheCooldown > 0) return null;
        shipCacheCooldown = force ? 5 : 20;
        LoadedServerShip loaded = VsShipHelper.getLoadedShipAtFast(sl, worldPosition);
        if (loaded == null) loaded = VsShipHelper.getLoadedShipAt(sl, worldPosition);
        cachedShipId = loaded != null ? loaded.getId() : -1L;
        return loaded;
    }

    public void registerThruster(BlockPos thrusterPos) {
        if (thrusterPos == null) return;
        pairedThrusters.add(thrusterPos.immutable());
        setChanged();
        sync();
    }

    public void unregisterThruster(BlockPos thrusterPos) {
        if (thrusterPos == null) return;
        pairedThrusters.remove(thrusterPos.immutable());
        setChanged();
        sync();
    }

    public int getPairedThrusterCount() {
        return pairedThrusters.size();
    }

    public List<BlockPos> getPairedThrusters() {
        return new ArrayList<>(pairedThrusters);
    }

    /**
     * Pair every thruster in {@link #searchRadius} (same ship preferred) to this computer.
     * @return number of thrusters newly paired
     */
    public int pairNearbyThrusters() {
        if (level == null) return 0;
        int added = 0;
        int r = Math.max(searchRadius, 12);
        long myShipId = -1L;
        try {
            if (level instanceof ServerLevel sl) {
                LoadedServerShip ls = VsShipHelper.getLoadedShipAt(sl, worldPosition);
                if (ls != null) myShipId = ls.getId();
            }
            if (myShipId < 0) {
                Ship s = VsShipHelper.getShipAt(level, worldPosition);
                if (s != null) myShipId = s.getId();
            }
        } catch (Throwable ignored) {
        }
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -8, -r),
                worldPosition.offset(r, 12, r))) {
            BlockState candidateState = level.getBlockState(p);
            BlockEntity candidateEntity = level.getBlockEntity(p);
            ShipThrusterBlockEntity thruster = candidateEntity instanceof ShipThrusterBlockEntity own ? own : null;
            if (thruster == null && !ExternalThrusterCompat.isCompatible(candidateState)) continue;
            try {
                if (myShipId >= 0) {
                    Ship s = level instanceof ServerLevel sl2
                            ? VsShipHelper.getLoadedShipAt(sl2, p)
                            : VsShipHelper.getShipAt(level, p);
                    if (s == null) s = VsShipHelper.getShipAt(level, p);
                    if (s == null || s.getId() != myShipId) continue;
                }
            } catch (Throwable ignored) {
            }
            BlockPos ip = p.immutable();
            if (pairedThrusters.add(ip)) {
                if (thruster != null) thruster.setPairedGuidance(worldPosition);
                added++;
            } else if (thruster != null) {
                thruster.setPairedGuidance(worldPosition);
            }
        }
        lastStatus = "paired compatible thrusters: " + pairedThrusters.size();
        setChanged();
        sync();
        return added;
    }

    public int clearPairedThrusters() {
        int n = pairedThrusters.size();
        for (BlockPos p : new ArrayList<>(pairedThrusters)) {
            if (level != null && level.getBlockEntity(p) instanceof ShipThrusterBlockEntity t) {
                if (worldPosition.equals(t.getPairedGuidance())) {
                    t.setPairedGuidance(null);
                    t.setGuidanceOwned(false);
                }
            } else if (level != null) {
                ExternalThrusterCompat.setThrottle(level, p, 0.0);
            }
        }
        pairedThrusters.clear();
        lastStatus = "thrusters unpaired";
        setChanged();
        sync();
        return n;
    }

    public @Nullable BlockPos getTarget() {
        return target;
    }

    public int getFleetChannel() {
        return fleetChannel;
    }

    public void setFleetChannel(int channel) {
        FleetFireControlManager.unregister(this);
        fleetChannel = Math.max(0, Math.min(9_999, channel));
        FleetFireControlManager.register(this);
        setChanged();
        sync();
    }

    public int getSalvoIntervalTicks() {
        return salvoIntervalTicks;
    }

    public void setSalvoIntervalTicks(int ticks) {
        salvoIntervalTicks = Math.max(1, Math.min(200, ticks));
        setChanged();
        sync();
    }

    public int broadcastFleetTarget() {
        return FleetFireControlManager.broadcastTarget(this);
    }

    public int queueFleetSalvo() {
        return FleetFireControlManager.queueSalvo(this);
    }

    void applyFleetTarget(BlockPos fleetTarget) {
        target = fleetTarget.immutable();
        recomputeSolution();
        setChanged();
        sync();
    }

    String fleetVesselKey() {
        if (level instanceof ServerLevel sl) {
            LoadedServerShip ship = resolveShipCached(sl, true);
            if (ship != null) return "ship:" + ship.getId();
        }
        return "block:" + worldPosition.asLong();
    }

    public int getSearchRadius() {
        return searchRadius;
    }

    public void setSearchRadius(int radius) {
        this.searchRadius = Math.max(1, Math.min(16, radius));
        setChanged();
    }

    public boolean isTerminalGuidance() {
        return terminalGuidance;
    }

    public void setTerminalGuidance(boolean terminalGuidance) {
        this.terminalGuidance = terminalGuidance;
        setChanged();
    }

    public double getBoostAccel() {
        return boostAccel;
    }

    public void setBoostAccel(double boostAccel) {
        this.boostAccel = Math.max(0.05, Math.min(5.0, boostAccel));
        setChanged();
    }

    public int getBoostTicks() {
        return boostTicks;
    }

    public void setBoostTicks(int boostTicks) {
        this.boostTicks = Math.max(10, Math.min(300, boostTicks));
        setChanged();
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    /** Loft peak world Y (climb to), or 0 for auto. */
    public int getDesiredApexY() {
        return desiredApexY;
    }

    /**
     * Set loft peak (world Y) to climb to before pitch-over / cruise.
     * @param y 0 = automatic loft; else absolute world Y (clamped 16k)
     */
    public void setDesiredApexY(int y) {
        if (y <= 0) {
            this.desiredApexY = 0;
        } else {
            this.desiredApexY = y;
        }
        recomputeSolution();
        setChanged();
        sync();
    }

    /** Level-cruise world Y (fly straight to target), or 0 = same as loft. */
    public int getDesiredCruiseY() {
        return desiredCruiseY;
    }

    public double getGravitySi() {
        return gravitySi;
    }

    public double getDragCoefficient() {
        return dragCoefficient;
    }

    public double getGuidanceStopDistance() {
        return guidanceStopDistance;
    }

    public void setGuidanceStopDistance(double blocks) {
        guidanceStopDistance = Double.isFinite(blocks)
                ? Math.max(0.0, Math.min(100_000.0, blocks)) : 0.0;
        setChanged();
        sync();
    }

    public BlockPos getMissileBaseBlock() {
        return missileBaseBlock != null ? missileBaseBlock : worldPosition.below();
    }

    public BlockPos getMissileCenterBlock() {
        return missileCenterBlock != null ? missileCenterBlock : worldPosition;
    }

    public BlockPos getMissileNoseBlock() {
        return missileNoseBlock != null ? missileNoseBlock : worldPosition.above();
    }

    public enum BodyCalibrationResult {
        APPLIED("Missile body calibration applied"),
        INVALID_POSITIONS("Base, center, and nose must be three different blocks"),
        HOST_SHIP_MISSING("The guidance computer is not on a loaded VS ship"),
        BLOCK_UNLOADED("One or more selected blocks are not loaded"),
        BLOCK_EMPTY("One or more selected positions do not contain a block"),
        WRONG_SHIP("All three blocks must belong to this guidance computer's ship");

        private final String message;

        BodyCalibrationResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** Applies three occupied blocks on this same VS ship as body calibration. */
    public BodyCalibrationResult applyMissileBodyCalibration(BlockPos base, BlockPos center, BlockPos nose) {
        if (!(level instanceof ServerLevel sl) || base == null || center == null || nose == null) {
            return BodyCalibrationResult.INVALID_POSITIONS;
        }
        if (base.equals(center) || base.equals(nose) || center.equals(nose)) {
            return BodyCalibrationResult.INVALID_POSITIONS;
        }
        LoadedServerShip host = VsShipHelper.getLoadedShipAt(sl, worldPosition);
        if (host == null) return BodyCalibrationResult.HOST_SHIP_MISSING;
        for (BlockPos p : List.of(base, center, nose)) {
            if (!sl.hasChunkAt(p)) return BodyCalibrationResult.BLOCK_UNLOADED;
            if (sl.getBlockState(p).isAir()) return BodyCalibrationResult.BLOCK_EMPTY;
            LoadedServerShip selectedShip = VsShipHelper.getLoadedShipAt(sl, p);
            if (selectedShip == null || selectedShip.getId() != host.getId()) {
                return BodyCalibrationResult.WRONG_SHIP;
            }
        }
        double fullX = nose.getX() - base.getX();
        double fullY = nose.getY() - base.getY();
        double fullZ = nose.getZ() - base.getZ();
        if (fullX * fullX + fullY * fullY + fullZ * fullZ < 1.0) {
            return BodyCalibrationResult.INVALID_POSITIONS;
        }
        missileBaseBlock = base.immutable();
        missileCenterBlock = center.immutable();
        missileNoseBlock = nose.immutable();
        appliedPlan = null;
        lastStatus = "body calibrated: base " + shortPos(base) + " center "
                + shortPos(center) + " nose " + shortPos(nose);
        setChanged();
        sync();
        return BodyCalibrationResult.APPLIED;
    }

    public boolean setMissileBodyCalibration(BlockPos base, BlockPos center, BlockPos nose) {
        return applyMissileBodyCalibration(base, center, nose) == BodyCalibrationResult.APPLIED;
    }

    private static String shortPos(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    public BallisticFlightPlan.Settings getPlannerSettings() {
        return plannerSettings;
    }

    public void setPlannerSettings(BallisticFlightPlan.Settings settings) {
        plannerSettings = settings == null ? BallisticFlightPlan.Settings.defaults() : settings;
        appliedPlan = null;
        setChanged();
        sync();
    }

    public @Nullable BallisticFlightPlan.Result calculateAdvancedPlan(int revision) {
        if (target == null || level == null) return null;
        Vec3 launch = launchWorldPos();
        Vec3 targetWorld = Vec3.atCenterOf(target);
        Vec3 targetVelocity = Vec3.ZERO;
        long resolvedShipId = -1L;
        if (targetShipId >= 0 && level instanceof ServerLevel sl) {
            LoadedServerShip moving = VsShipHelper.getLoadedShipById(sl, targetShipId);
            if (moving != null) {
                var p = moving.getTransform().getPositionInWorld();
                var v = moving.getVelocity();
                targetWorld = new Vec3(p.x(), p.y(), p.z());
                targetVelocity = v == null ? Vec3.ZERO : new Vec3(v.x(), v.y(), v.z());
                resolvedShipId = targetShipId;
            }
        }
        return BallisticPlanOptimizer.optimize(revision, launch, targetWorld, targetVelocity, resolvedShipId,
                plannerSettings, boostAccel, boostTicks, gravitySi, dragCoefficient,
                level instanceof ServerLevel sl ? (x, z) -> sl.hasChunk(x >> 4, z >> 4)
                        ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Double.NaN : null,
                getObservedTickSeconds());
    }

    public double getObservedTickSeconds() {
        return Math.max(0.05, Math.min(0.5, observedTickSeconds));
    }

    public void applyAdvancedPlan(BallisticFlightPlan.Result result) {
        if (result == null) return;
        plannerSettings = result.settings();
        appliedPlan = result;
        setChanged();
        sync();
    }

    public void setFlightPhysics(double gravitySi, double dragCoefficient) {
        double sanitizedGravity = BallisticCalculator.sanitizeGravity(gravitySi);
        double sanitizedDrag = BallisticCalculator.sanitizeDrag(dragCoefficient);
        if (Math.abs(this.gravitySi - sanitizedGravity) < 1.0e-9
                && Math.abs(this.dragCoefficient - sanitizedDrag) < 1.0e-12) return;
        this.gravitySi = sanitizedGravity;
        this.dragCoefficient = sanitizedDrag;
        recomputeSolution();
        setChanged();
        sync();
    }

    /**
     * Set cruise altitude after loft. Climb to loft first, then hold this Y toward target.
     * @param y 0 = cruise at loft peak; else absolute world Y
     */
    public void setDesiredCruiseY(int y) {
        if (y <= 0) {
            this.desiredCruiseY = 0;
        } else {
            this.desiredCruiseY = y;
        }
        recomputeSolution();
        setChanged();
        sync();
    }

    /**
     * Set missile speed/power 1–20 and derive boost accel + burn time.
     * Heavy ships: try 12–20 (GUI Heavy / MAX) if the craft barely climbs.
     * <ul>
     *   <li>1 → accel 0.20, burn 30t</li>
     *   <li>5 (default) → ~0.68 / 70t</li>
     *   <li>14 (Heavy) → ~1.76 / 160t</li>
     *   <li>20 (MAX) → ~2.48 / 220t</li>
     * </ul>
     */
    public void setSpeedLevel(int level) {
        this.speedLevel = Math.max(1, Math.min(20, level));
        // Slightly super-linear accel at the top end so capital hulls can still loft
        double t = this.speedLevel / 20.0;
        this.boostAccel = Math.min(5.0, 0.12 + this.speedLevel * 0.10 + t * t * 0.80);
        this.boostTicks = Math.min(300, 24 + this.speedLevel * 11);
        recomputeSolution();
        setChanged();
        sync();
    }

    public float getWarheadYield() {
        return warheadYield;
    }

    public void setWarheadYield(float warheadYield) {
        // 0 allowed = inert arrival / practice
        this.warheadYield = Math.max(0f, Math.min(24f, warheadYield));
        setChanged();
    }

    public boolean isShipMissileMode() {
        return shipMissileMode;
    }

    public void setShipMissileMode(boolean shipMissileMode) {
        this.shipMissileMode = shipMissileMode;
        setChanged();
    }

    public boolean isAlsoFireTubes() {
        return alsoFireTubes;
    }

    public void setAlsoFireTubes(boolean alsoFireTubes) {
        this.alsoFireTubes = alsoFireTubes;
        setChanged();
    }

    public int getLastLaunchCount() {
        return lastLaunchCount;
    }

    public int getLastEtaTicks() {
        return lastEtaTicks;
    }

    public double getLastPitchDeg() {
        return lastPitchDeg;
    }

    public @Nullable BallisticCalculator.Result getLastCalculation() {
        return lastCalculation;
    }

    public String getLastStatus() {
        // Live status if this ship is currently flying (cheap id path)
        if (level instanceof ServerLevel sl && commandingFlight) {
            try {
                LoadedServerShip loaded = resolveShipCached(sl);
                if (loaded != null) {
                    ShipBallisticController c = ShipBallisticController.get(loaded);
                    if (c != null && c.isFlying()) {
                        return c.getStatus();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return lastStatus;
    }

    public void clearTarget() {
        target = null;
        lastEtaTicks = -1;
        lastCalculation = null;
        lastStatus = "aim cleared";
        setChanged();
        sync();
    }

    public void setTargetWorld(int x, int y, int z) {
        target = new BlockPos(x, y, z);
        targetShipId = -1L;
        movingTargetMissingTicks = 0;
        recomputeSolution();
        setChanged();
        sync();
    }

    public void setMovingTarget(long shipId, BlockPos lastKnownPosition) {
        if (shipId < 0 || lastKnownPosition == null) return;
        target = lastKnownPosition;
        targetShipId = shipId;
        movingTargetMissingTicks = 0;
        recomputeSolution();
        setChanged();
        sync();
    }

    public long getTargetShipId() { return targetShipId; }

    /**
     * Horizontal range from computer (world) to current target, or -1 if no target.
     */
    public double horizontalRangeToTarget() {
        if (target == null || level == null) return -1;
        Vec3 launch = launchWorldPos();
        double dx = target.getX() + 0.5 - launch.x;
        double dz = target.getZ() + 0.5 - launch.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** @return null if in range; otherwise a short error string */
    public @Nullable String rangeLimitMessage() {
        double r = horizontalRangeToTarget();
        if (r < 0) return "no target";
        double max = XenoPerfConfig.maxBallisticRange();
        if (r > max) {
            return String.format("out of range: %.0f > max %.0f blocks (%.0f km)",
                    r, max, max / 1000.0);
        }
        return null;
    }

    private Vec3 launchWorldPos() {
        Vec3 launch = Vec3.atCenterOf(worldPosition).add(0, 1, 0);
        if (level == null) return launch;
        try {
            var world = net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat
                    .shipyardToWorld(level, launch);
            if (world != null) launch = world;
            if (level instanceof ServerLevel sl) {
                LoadedServerShip ship = VsShipHelper.getLoadedShipAt(sl, worldPosition);
                if (ship != null && ship.getTransform() != null) {
                    var com = ship.getTransform().getPositionInWorld();
                    launch = new Vec3(com.x(), com.y(), com.z());
                }
            }
        } catch (Throwable ignored) {
        }
        return launch;
    }

    public Vec3 getLaunchWorldPosition() {
        return launchWorldPos();
    }

    public void setTargetFromLook(Player player) {
        if (player == null) return;
        HitResult hit = player.pick(200.0, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) return;
        Vec3 loc = hit.getLocation();
        BlockPos raw = BlockPos.containing(loc);
        if (level != null) {
            try {
                if (VSGameUtilsKt.isBlockInShipyard(level, raw)
                        || VSGameUtilsKt.getShipManagingPos(level, raw) != null) {
                    var world = net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat
                            .shipyardToWorld(level, loc);
                    if (world != null) {
                        target = BlockPos.containing(world);
                    } else {
                        target = raw;
                    }
                } else {
                    target = raw;
                }
            } catch (Throwable t) {
                target = raw;
            }
        } else {
            target = raw;
        }
        recomputeSolution();
        setChanged();
        sync();
    }

    /** From block neighborChanged — preferred redstone path. */
    public void onRedstoneChanged(boolean powered) {
        cachedRedstone = powered;
        if (powered && !wasPowered && level instanceof ServerLevel sl) {
            if (fleetChannel > 0) queueFleetSalvo();
            else firePulse(sl);
        }
        wasPowered = powered;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = System.nanoTime();
        if (lastServerTickNanos > 0L) {
            double sample = Math.max(0.01,
                    Math.min(0.5, (now - lastServerTickNanos) / 1_000_000_000.0));
            observedTickSeconds = observedTickSeconds * 0.85 + sample * 0.15;
        }
        lastServerTickNanos = now;
        FleetFireControlManager.register(this);
        FleetFireControlManager.tick(sl);

        // Idle: almost free — only rare prune + rare redstone safety poll
        if (!commandingFlight) {
            if (--pruneCooldown <= 0) {
                pruneCooldown = 80;
                if (!pairedThrusters.isEmpty()) pruneDeadPairs();
            }
            // Safety poll every 10t in case neighborChanged was missed (piston edge cases)
            if (--redstoneSafetyPoll <= 0) {
                redstoneSafetyPoll = 10;
                boolean powered = level.hasNeighborSignal(worldPosition);
                if (powered != cachedRedstone) {
                    onRedstoneChanged(powered);
                }
            }
            return;
        }

        // In flight: sync thruster visuals only on phase change
        if (--pruneCooldown <= 0) {
            pruneCooldown = 100;
            pruneDeadPairs();
        }
        syncThrustersToFlight(sl);
        tickAdaptiveGuidance(sl);
    }

    private void tickAdaptiveGuidance(ServerLevel sl) {
        if (!plannerSettings.autoEnabled()) return;
        LoadedServerShip missile = resolveShipCached(sl, true);
        if (missile == null) return;
        ShipBallisticController controller = ShipBallisticController.get(missile);
        if (controller == null || !controller.isFlying()) return;

        // Performance: throttle recalc based on phase and target movement
        int interval = controller.getPhase() == MissilePhase.TERMINAL ? 2 : GUIDANCE_UPDATE_INTERVAL;
        
        // Extend cache when target is stationary (saves CPU)
        if (targetShipId < 0 && target != null) {
            Vec3 currentTargetPos = Vec3.atCenterOf(target);
            if (lastKnownTargetPos != null && lastKnownTargetPos.distanceTo(currentTargetPos) < 0.5) {
                stationaryTargetCacheTicks += interval;
                if (stationaryTargetCacheTicks < GUIDANCE_UPDATE_INTERVAL * 2) {
                    return; // Skip recalc - target hasn't moved
                }
            } else {
                stationaryTargetCacheTicks = 0;
                lastKnownTargetPos = currentTargetPos;
            }
        }
        
        if (--adaptiveReplanCooldown > 0) return;
        adaptiveReplanCooldown = interval;

        if (targetShipId >= 0) {
            LoadedServerShip moving = VsShipHelper.getLoadedShipById(sl, targetShipId);
            if (moving == null) {
                movingTargetMissingTicks += interval;
                controller.enterSearchHold();
                lastStatus = "moving target lost — SEARCH/HOLD " + (movingTargetMissingTicks / 20) + "s/300s";
                if (movingTargetMissingTicks >= 20 * 60 * 5) {
                    controller.abort();
                    commandingFlight = false;
                    releaseThrusters();
                    lastStatus = "safe abort — moving target unavailable for five minutes";
                    setChanged();
                }
                return;
            }
            movingTargetMissingTicks = 0;
            var position = moving.getTransform().getPositionInWorld();
            target = BlockPos.containing(position.x(), position.y(), position.z());
        }

        BallisticFlightPlan.Result replanned = calculateAdvancedPlan(0);
        if (replanned != null && replanned.feasible()) {
            appliedPlan = replanned;
            controller.updateAdaptivePlan(replanned);
        }
    }

    /** Debug-friendly ship detect for status strings / GUI. */
    public String describeShipLink(ServerLevel sl) {
        LoadedServerShip loaded = VsShipHelper.getLoadedShipAt(sl, worldPosition);
        if (loaded != null) {
            return "ship#" + loaded.getId() + " loaded";
        }
        ServerShip any = VsShipHelper.getShipAt(sl, worldPosition);
        if (any != null) {
            return "ship#" + any.getId() + " (not loaded object)";
        }
        try {
            if (VSGameUtilsKt.isBlockInShipyard(sl, worldPosition)) {
                return "shipyard chunk but no ship object";
            }
        } catch (Throwable ignored) {
        }
        return "no ship (place on assembled VS hull)";
    }

    /**
     * Redstone / CC fire.
     * <ol>
     *   <li>If guidance sits on a VS ship and {@link #shipMissileMode}: launch <b>the ship</b>
     *       as a real ballistic missile.</li>
     *   <li>Else (or also if {@link #alsoFireTubes}): launch nearby entity tubes.</li>
     * </ol>
     *
     * @return number of munitions started (1 for ship launch + tube count)
     */
    public int firePulse(ServerLevel sl) {
        lastLaunchCount = 0;
        if (target == null) {
            lastStatus = "no target — open GUI and Set Target XYZ";
            return 0;
        }

        String rangeErr = rangeLimitMessage();
        if (rangeErr != null) {
            lastStatus = rangeErr;
            setChanged();
            return 0;
        }

        // Auto-pair if player never paired but thrusters sit next to the computer
        if (pairedThrusters.isEmpty()) {
            pairNearbyThrusters();
        }

        recomputeSolution();
        MissileChunkLoadManager.forceNear(sl, worldPosition, 1, 20 * 20, MissileChunkLoadManager.Role.PAD);
        if (target != null) {
            MissileChunkLoadManager.forceNear(sl, target, 1, 20 * 40, MissileChunkLoadManager.Role.TARGET);
        }

        boolean launchedShip = false;
        if (shipMissileMode) {
            launchedShip = tryLaunchShipMissile(sl);
        }

        if (!launchedShip || alsoFireTubes) {
            lastLaunchCount += fireTubes(sl);
        }

        if (launchedShip) {
            lastLaunchCount = Math.max(1, lastLaunchCount);
        }

        setChanged();
        return lastLaunchCount;
    }

    /** Abort an in-flight ship ballistic if this computer is on that hull. */
    public boolean abortShipFlight() {
        if (!(level instanceof ServerLevel sl)) return false;
        boolean aborted = false;
        try {
            LoadedServerShip loaded = VsShipHelper.getLoadedShipAt(sl, worldPosition);
            if (loaded != null) {
                ShipBallisticController c = ShipBallisticController.get(loaded);
                if (c != null && c.isFlying()) {
                    c.abort();
                    aborted = true;
                }
                restoreShipGravity(loaded);
            }
        } catch (Throwable ignored) {
        }
        releaseThrusters();
        commandingFlight = false;
        if (aborted) {
            lastStatus = "flight aborted";
            setChanged();
        }
        return aborted;
    }

    private boolean tryLaunchShipMissile(ServerLevel sl) {
        ShipGravityControl gravityControl = null;
        try {
            LoadedServerShip loaded = VsShipHelper.getLoadedShipAt(sl, worldPosition);
            if (loaded == null) {
                lastStatus = "not on VS ship — " + describeShipLink(sl);
                return false;
            }

            ShipBallisticController existing = ShipBallisticController.get(loaded);
            if (existing != null && existing.isFlying()) {
                lastStatus = "already in flight: " + existing.getStatus();
                return false;
            }

            List<BlockPos> thrusters = resolvePairedThrusterPositions();
            int thrusterCount = thrusters.size();
            if (thrusterCount == 0) {
                lastStatus = "no paired thrusters — shift-click thrusters or Pair Nearby";
                // Still allow launch with pure physics (no thruster bonus)
            }

            Vec3 targetWorld = Vec3.atCenterOf(target);
            Vec3 loft = resolveLoftDirection(thrusters);

            ShipBallisticController ctrl = ShipBallisticController.getOrCreate(loaded);
            ctrl.setBodyCalibration(getMissileBaseBlock(), getMissileCenterBlock(), getMissileNoseBlock());
            ctrl.setGuidanceStopDistance(guidanceStopDistance);
            // ShipGravityControl and ShipBallisticController both compensate VS gravity.
            // Hand ownership to guidance for the flight so their forces never stack.
            gravityControl = loaded.getAttachment(ShipGravityControl.class);
            if (gravityControl != null) gravityControl.setSuppressed(true);
            boolean ok = ctrl.launch(loaded, targetWorld, loft,
                    boostAccel, boostTicks, terminalGuidance, warheadYield, thrusterCount,
                    desiredApexY, desiredCruiseY, gravitySi, dragCoefficient);
            if (ok) {
                BallisticFlightPlan.Result plan = appliedPlan;
                if (plan == null) plan = calculateAdvancedPlan(0);
                if (plan != null) ctrl.setAdvancedPlan(plan);
                ctrl.setFlightDim(sl.dimension());
                cachedShipId = loaded.getId();
                shipCacheCooldown = 0;
                commandingFlight = true;
                lastSyncedPhase = null;
                lastSyncedThrottle = -1;
                // Visual throttle only — phys thrust is CoM forces from ShipBallisticController.
                // Clear any lingering thruster attachment forces that would torque the hull.
                try {
                    XenoThrusterControl tc = loaded.getAttachment(XenoThrusterControl.class);
                    if (tc != null) tc.clearAll();
                } catch (Throwable ignored) {
                }
                engageThrusters(thrusters, 1.0);
                lastStatus = String.format(
                        "LAUNCHED thr=%d loftY=%.0f cruiseY=%.0f | %s",
                        thrusterCount, ctrl.getApexY(), ctrl.getCruiseY(), ctrl.getStatus());
                setChanged();
                return true;
            }
            if (gravityControl != null) gravityControl.setSuppressed(false);
            lastStatus = "ship launch failed";
            return false;
        } catch (Throwable t) {
            if (gravityControl != null) gravityControl.setSuppressed(false);
            lastStatus = "ship launch error: " + t.getClass().getSimpleName() + " — " + t.getMessage();
            return false;
        }
    }

    private void syncThrustersToFlight(ServerLevel sl) {
        try {
            // force=true: never treat ship-cache cooldown as "flight over"
            LoadedServerShip loaded = resolveShipCached(sl, true);
            if (loaded == null) {
                // Ship unloaded / gone — end guidance ownership only
                releaseThrusters();
                commandingFlight = false;
                lastSyncedPhase = null;
                lastSyncedThrottle = -1;
                lastStatus = "ship lost mid-flight";
                setChanged();
                return;
            }
            ShipBallisticController c = ShipBallisticController.get(loaded);
            if (c == null || !c.isFlying()) {
                restoreShipGravity(loaded);
                releaseThrusters();
                commandingFlight = false;
                lastSyncedPhase = null;
                lastSyncedThrottle = -1;
                lastStatus = c != null ? "flight ended" : "idle";
                setChanged();
                return;
            }
            MissilePhase phase = c.getPhase();
            double throttle = switch (phase) {
                case EJECT, BOOST, TERMINAL -> 1.0;
                case COAST -> 0.55;
                default -> 0.0;
            };
            // Clear thruster phys map once at phase entry — not every 20t forever
            if (phase != lastSyncedPhase) {
                try {
                    XenoThrusterControl tc = loaded.getAttachment(XenoThrusterControl.class);
                    if (tc != null && !tc.isEmpty()) tc.clearAll();
                } catch (Throwable ignored) {
                }
            }
            // Only push throttle when phase/value changes (avoid N BE writes every tick)
            if (phase != lastSyncedPhase || Math.abs(throttle - lastSyncedThrottle) > 0.01) {
                lastSyncedPhase = phase;
                lastSyncedThrottle = throttle;
                for (BlockPos p : resolvePairedThrusterPositions()) {
                    if (level.getBlockEntity(p) instanceof ShipThrusterBlockEntity t) {
                        if (!t.isGuidanceOwned()) t.setGuidanceOwned(true);
                        t.setGuidanceThrottle(throttle);
                    } else {
                        ExternalThrusterCompat.setThrottle(level, p, throttle);
                    }
                }
            }
            // Status string only every 10t
            if (sl.getGameTime() % 10 == 0) {
                lastStatus = c.getStatus() + " thrusters=" + pairedThrusters.size();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreShipGravity(LoadedServerShip ship) {
        try {
            ShipGravityControl gravity = ship.getAttachment(ShipGravityControl.class);
            if (gravity != null) gravity.setSuppressed(false);
        } catch (Throwable ignored) {
        }
    }

    private void engageThrusters(List<BlockPos> thrusters, double throttle) {
        for (BlockPos p : thrusters) {
            if (level.getBlockEntity(p) instanceof ShipThrusterBlockEntity t) {
                t.setGuidanceOwned(true);
                t.setGuidanceThrottle(throttle);
            } else {
                ExternalThrusterCompat.setThrottle(level, p, throttle);
            }
        }
        lastSyncedThrottle = throttle;
    }

    private void releaseThrusters() {
        if (level == null) return;
        // O(paired) only — no volume scan
        for (BlockPos p : new ArrayList<>(pairedThrusters)) {
            if (level.getBlockEntity(p) instanceof ShipThrusterBlockEntity t) {
                t.forceShutdown();
            } else {
                ExternalThrusterCompat.setThrottle(level, p, 0.0);
            }
        }
        lastSyncedPhase = null;
        lastSyncedThrottle = -1;
    }

    private void pruneDeadPairs() {
        if (level == null || pairedThrusters.isEmpty()) return;
        boolean dirty = false;
        Iterator<BlockPos> it = pairedThrusters.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof ShipThrusterBlockEntity)
                    && !ExternalThrusterCompat.isCompatible(level.getBlockState(p))) {
                it.remove();
                dirty = true;
            }
        }
        if (dirty) setChanged();
    }

    /** Resolve previously paired native or external engines without another volume scan. */
    private List<BlockPos> resolvePairedThrusterPositions() {
        List<BlockPos> list = new ArrayList<>(pairedThrusters.size());
        if (level == null) return list;
        for (BlockPos p : pairedThrusters) {
            if (level.getBlockEntity(p) instanceof ShipThrusterBlockEntity
                    || ExternalThrusterCompat.isCompatible(level.getBlockState(p))) {
                list.add(p);
            }
        }
        return list;
    }

    private Vec3 resolveLoftDirection(List<BlockPos> thrusters) {
        // Prefer paired thruster thrust dir, then tube facing, then UP
        Direction thrusterThrust = null;
        for (BlockPos p : thrusters) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof ShipThrusterBlock) {
                thrusterThrust = st.getValue(ShipThrusterBlock.FACING).getOpposite();
                break;
            }
            Direction external = ExternalThrusterCompat.thrustDirection(st);
            if (external != null) {
                thrusterThrust = external;
                break;
            }
        }
        int r = searchRadius;
        Direction tubeFace = null;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -4, -r),
                worldPosition.offset(r, 8, r))) {
            BlockState st = level.getBlockState(p);
            if (tubeFace == null && st.getBlock() instanceof MissileTubeBlock) {
                tubeFace = st.getValue(MissileTubeBlock.FACING);
            }
        }
        Direction d = thrusterThrust != null ? thrusterThrust
                : tubeFace != null ? tubeFace
                : Direction.UP;
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private int fireTubes(ServerLevel sl) {
        int count = 0;
        int r = searchRadius;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -3, -r),
                worldPosition.offset(r, 8, r))) {
            BlockEntity be = level.getBlockEntity(p.immutable());
            if (be instanceof MissileTubeBlockEntity tube) {
                if (tube.tryLaunch(target, boostAccel, boostTicks, terminalGuidance, warheadYield,
                        gravitySi, dragCoefficient)) {
                    count++;
                }
            }
        }
        if (count > 0) {
            lastStatus = "fired " + count + " tube missile(s)";
        } else if (!shipMissileMode) {
            lastStatus = "no ready tubes";
        }
        return count;
    }

    /** Solve loft for UI / CC without firing. */
    public void recomputeSolution() {
        if (target == null || level == null) {
            lastEtaTicks = -1;
            lastCalculation = null;
            return;
        }
        Vec3 launch = launchWorldPos();
        Vec3 tgt = Vec3.atCenterOf(target);
        double horiz = Math.sqrt(
                (tgt.x - launch.x) * (tgt.x - launch.x)
                        + (tgt.z - launch.z) * (tgt.z - launch.z));
        double maxR = XenoPerfConfig.maxBallisticRange();
        if (horiz > maxR) {
            lastEtaTicks = -1;
            lastStatus = String.format(
                    "OUT OF RANGE %.0f > max %.0f blocks (%.0f km) — /xenoperf set maxrange",
                    horiz, maxR, maxR / 1000.0);
            return;
        }
        // Plan loft uses loft peak; if only cruise is set, plan to at least that height
        double planLoft = desiredApexY > 0
                ? desiredApexY
                : (desiredCruiseY > 0 ? desiredCruiseY : 0);
        var plan = BallisticTrajectory.plan(launch, tgt, boostAccel, boostTicks, planLoft,
                gravitySi, dragCoefficient);
        lastPitchDeg = Math.toDegrees(plan.pitchRad());
        lastEtaTicks = plan.etaTicks();
        double loftY = desiredApexY > 0 ? desiredApexY : plan.apexY();
        double cruiseY = desiredCruiseY > 0 ? desiredCruiseY : loftY;
        if (cruiseY > loftY) loftY = cruiseY;
        String maxStr = maxR >= Double.MAX_VALUE / 2 ? "∞" : String.format("%.0f", maxR);
        String etaStr = lastEtaTicks > 0 && lastEtaTicks < Integer.MAX_VALUE / 4
                ? String.format("%.0fs", lastEtaTicks / 20.0) : "?";
        var calc = plan.calculation();
        lastCalculation = calc;
        lastStatus = String.format(
                "CALC %s az=%.1f° elev=%.1f° loftY=%.0f%s cruiseY=%.0f%s range=%.0f/%s eta~%s v=%.1f/%.1f m/s impact=%.1f miss~%.0f",
                calc.reachable() ? "SOLVED" : "POWERED-GUIDANCE",
                calc.azimuthDeg(), calc.selectedAngleDeg(),
                loftY, desiredApexY > 0 ? "" : "(auto)",
                cruiseY, desiredCruiseY > 0 ? "" : "(=loft)",
                plan.rangeHorizontal(),
                maxStr,
                etaStr,
                calc.availableSpeed() * 20.0, calc.requiredSpeed() * 20.0,
                calc.impactSpeed() * 20.0,
                calc.predictedMiss());
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target != null) tag.putLong("Target", target.asLong());
        if (targetShipId >= 0) tag.putLong("TargetShipId", targetShipId);
        tag.putBoolean("WasPowered", wasPowered);
        tag.putInt("SearchRadius", searchRadius);
        tag.putBoolean("Terminal", terminalGuidance);
        tag.putDouble("BoostA", boostAccel);
        tag.putInt("BoostT", boostTicks);
        tag.putInt("SpeedLvl", speedLevel);
        tag.putInt("ApexY", desiredApexY);
        tag.putInt("CruiseY", desiredCruiseY);
        tag.putDouble("GravitySI", gravitySi);
        tag.putDouble("DragCoefficient", dragCoefficient);
        tag.putDouble("GuidanceStopDistance", guidanceStopDistance);
        if (missileBaseBlock != null) tag.putLong("MissileBaseBlock", missileBaseBlock.asLong());
        if (missileCenterBlock != null) tag.putLong("MissileCenterBlock", missileCenterBlock.asLong());
        if (missileNoseBlock != null) tag.putLong("MissileNoseBlock", missileNoseBlock.asLong());
        tag.put("Planner", plannerSettings.save());
        tag.putInt("FleetChannel", fleetChannel);
        tag.putInt("SalvoInterval", salvoIntervalTicks);
        tag.putFloat("Yield", warheadYield);
        tag.putBoolean("ShipMissile", shipMissileMode);
        tag.putBoolean("AlsoTubes", alsoFireTubes);
        // Commanding is runtime-only (not restored on load)
        ListTag list = new ListTag();
        for (BlockPos p : pairedThrusters) {
            list.add(LongTag.valueOf(p.asLong()));
        }
        tag.put("PairedThrusters", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        targetShipId = tag.contains("TargetShipId") ? tag.getLong("TargetShipId") : -1L;
        movingTargetMissingTicks = 0;
        adaptiveReplanCooldown = 0;
        wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("SearchRadius")) searchRadius = Math.max(1, Math.min(16, tag.getInt("SearchRadius")));
        terminalGuidance = !tag.contains("Terminal") || tag.getBoolean("Terminal");
        if (tag.contains("BoostA")) boostAccel = tag.getDouble("BoostA");
        if (tag.contains("BoostT")) boostTicks = tag.getInt("BoostT");
        if (tag.contains("SpeedLvl")) {
            speedLevel = Math.max(1, Math.min(20, tag.getInt("SpeedLvl")));
        } else {
            // Derive approx level from accel for older saves
            speedLevel = (int) Math.max(1, Math.min(20, Math.round((boostAccel - 0.08) / 0.12)));
        }
        if (tag.contains("ApexY")) {
            int ay = tag.getInt("ApexY");
            desiredApexY = Math.max(0, ay);
        }
        if (tag.contains("CruiseY")) {
            int cy = tag.getInt("CruiseY");
            desiredCruiseY = Math.max(0, cy);
        }
        if (tag.contains("GravitySI")) {
            gravitySi = BallisticCalculator.sanitizeGravity(tag.getDouble("GravitySI"));
        }
        if (tag.contains("DragCoefficient")) {
            dragCoefficient = BallisticCalculator.sanitizeDrag(tag.getDouble("DragCoefficient"));
        }
        if (tag.contains("GuidanceStopDistance")) {
            double distance = tag.getDouble("GuidanceStopDistance");
            guidanceStopDistance = Double.isFinite(distance)
                    ? Math.max(0.0, Math.min(100_000.0, distance)) : 0.0;
        }
        missileBaseBlock = tag.contains("MissileBaseBlock")
                ? BlockPos.of(tag.getLong("MissileBaseBlock")) : null;
        missileCenterBlock = tag.contains("MissileCenterBlock")
                ? BlockPos.of(tag.getLong("MissileCenterBlock")) : null;
        missileNoseBlock = tag.contains("MissileNoseBlock")
                ? BlockPos.of(tag.getLong("MissileNoseBlock")) : null;
        plannerSettings = tag.contains("Planner", Tag.TAG_COMPOUND)
                ? BallisticFlightPlan.Settings.load(tag.getCompound("Planner"))
                : BallisticFlightPlan.Settings.defaults();
        appliedPlan = null;
        if (tag.contains("FleetChannel")) {
            fleetChannel = Math.max(0, Math.min(9_999, tag.getInt("FleetChannel")));
        }
        if (tag.contains("SalvoInterval")) {
            salvoIntervalTicks = Math.max(1, Math.min(200, tag.getInt("SalvoInterval")));
        }
        if (tag.contains("Yield")) warheadYield = tag.getFloat("Yield");
        shipMissileMode = !tag.contains("ShipMissile") || tag.getBoolean("ShipMissile");
        alsoFireTubes = tag.contains("AlsoTubes") && tag.getBoolean("AlsoTubes");
        // Never resume mid-flight after chunk reload — ACTIVE_FLIGHTS / phys state is lost
        commandingFlight = false;
        pairedThrusters.clear();
        if (tag.contains("PairedThrusters", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PairedThrusters", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                Tag entry = list.get(i);
                if (entry instanceof LongTag lt) {
                    pairedThrusters.add(BlockPos.of(lt.getAsLong()));
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        FleetFireControlManager.unregister(this);
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
