package net.bullettrain.xenopixelsmod.aero.control;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One tick of powered flight: flaps travel, aerodynamics are published, attitude is commanded
 * and thrust is distributed.
 *
 * <p>Every host that can fly a ship — the flight controller block and the pilot seat — runs
 * this, so the two cannot drift apart in how a ship behaves. What differs between them is what
 * they feed in: the controller can derive its setpoints from an autopilot, while a standalone
 * seat is manual only. Deciding the setpoint is the host's job; turning a setpoint into forces
 * is this class's job.
 *
 * <p>An instance owns its scratch vectors, which is the only reason it is not static: the
 * aerodynamic model is deliberately allocation-free and the tick must not undo that.
 * Instances are therefore per-host and not thread-safe, which is fine — this runs on the server
 * thread, and only the values it publishes are read by the physics thread.
 */
public final class AeroFlightCore {

    /** Ceiling on how many panels one scan re-renders; a hull is not a wind tunnel. */
    private static final int MAX_ANIMATED_PANELS = 512;
    /** How often role-driven panels are rescanned. Every tick would re-render on the slightest
     * stick jitter; a bounded interval keeps the visual responsive without a per-tick chunk churn. */
    private static final int PANEL_SCAN_INTERVAL_TICKS = 3;
    /** Error magnitude below which a role panel shows centered rather than deflected either way. */
    private static final double ERROR_DEADBAND = 0.03;
    /** Error magnitude at which an error-driven panel reaches full deflection. */
    private static final double ERROR_SATURATION = 0.35;
    /** Full deflection angle in degrees, matching the old model's baked &plusmn;22.5&deg; feel
     * closely enough while giving the continuous chase somewhere to travel through. Package-
     * private: {@link AeroControlSurfaceTorque} normalizes against this same ceiling. */
    static final double MAX_DEFLECT_DEG = 30.0;
    /** Below this ground speed (blocks/s), angle of attack is not a meaningful reading — see
     * {@link #applyAeroModel}. */
    private static final double MIN_STALL_SPEED = 3.0;

    private int panelScanCooldown;

    private final Vector3d noseScratch = new Vector3d();
    private final Vector3d upScratch = new Vector3d();
    private final Vector3d rightScratch = new Vector3d();
    private final Vector3d errorScratch = new Vector3d();
    private final Vector3d forceScratch = new Vector3d();
    private final Vector3d rightLocalScratch = new Vector3d();

    /**
     * @param bus            the host's authoritative state; flap travel is advanced on it
     * @param ship           the sub-level being flown
     * @param bodyNose       ship-local forward direction
     * @param bodyUp         ship-local roll reference
     * @param bodyThrust     ship-local direction to push along (usually the nose)
     * @param worldVelocity  current ship velocity, world space
     * @param deltaSeconds   observed server tick length
     * @param linkedPanels   this host's explicitly linked wing panels; only these are driven
     * @return true when the ship is past its stall angle
     */
    public boolean tick(AeroBus bus, ServerSubLevel ship, Level level,
                        List<AeroLinkManager.Link> links,
                        Vector3dc bodyNose, Vector3dc bodyUp, Vector3dc bodyThrust,
                        double yawDeg, double pitchDeg, double rollDeg, double throttle,
                        Vector3dc worldVelocity, double deltaSeconds, Set<BlockPos> linkedPanels) {
        double airspeed = worldVelocity.length();

        // Flaps move before the model reads them, so lift and drag reflect where the surfaces
        // actually are this tick rather than where they were commanded to end up.
        bus.advanceFlaps(deltaSeconds, airspeed);
        updatePanelDeflections(bus, ship, level, bodyNose, bodyUp, linkedPanels);
        boolean stalled = applyAeroModel(bus, ship, worldVelocity, bodyNose);

        // The PD attitude-hold auto-pilot only drives rotation in mouse-aim mode. In keyboard
        // mode the sticks deflect panels directly instead (see updatePanelDeflections/deflectFor)
        // and AeroControlSurfaceTorque's real per-panel torque is the only thing that turns the
        // ship — enabled=false leaves this entry idle rather than fighting that with a second,
        // separate torque toward a target attitude nobody is actually commanding by feel anymore.
        // Every non-seat attitude source (GUI, autopilot, CC, panel) always has mouseAim()==true
        // (AeroBus's own default), so this is unchanged for them.
        AeroStabilizerSystem.setTargetAttitude(ship, yawDeg, pitchDeg, rollDeg, bodyNose, bodyUp, bus.mouseAim());
        VectorMixer.apply(level, links, bodyThrust.x(), bodyThrust.y(), bodyThrust.z(), throttle);
        return stalled;
    }

    /**
     * Publish this tick's aerodynamic acceleration for the physics thread to apply.
     *
     * <p>{@link AeroAeroModel} works in world space, but the nose is a ship-local direction, so
     * it is rotated body&rarr;world first. {@link AeroAeroForceSystem} rotates the result back
     * into body space at impulse time, because that is the frame Sable's impulse API takes.
     */
    private boolean applyAeroModel(AeroBus bus, ServerSubLevel ship, Vector3dc worldVelocity,
                                   Vector3dc bodyNose) {
        if (!AeroConfig.aeroModelEnabled) {
            AeroAeroForceSystem.setAeroAcceleration(ship, 0.0, 0.0, 0.0, false);
            return false;
        }
        noseScratch.set(bodyNose);
        ship.logicalPose().orientation().transform(noseScratch);

        AeroAeroModel.compute(worldVelocity.x(), worldVelocity.y(), worldVelocity.z(),
                noseScratch.x, noseScratch.y, noseScratch.z, bus.flap(), baseLiftFactor(ship),
                forceScratch);
        AeroAeroForceSystem.setAeroAcceleration(ship,
                forceScratch.x, forceScratch.y, forceScratch.z, true);

        // Angle of attack is the angle between the nose and the VELOCITY direction, and a
        // near-zero velocity vector's direction is essentially noise — taxiing a couple blocks/s
        // and turning is enough to point "velocity" almost anywhere relative to the nose, which
        // read as a stall even standing still on the ground. Below MIN_STALL_SPEED the angle
        // isn't a real aerodynamic reading at all, so don't report one.
        if (worldVelocity.length() < MIN_STALL_SPEED) return false;
        return AeroAeroModel.angleOfAttackDeg(worldVelocity.x(), worldVelocity.y(), worldVelocity.z(),
                noseScratch.x, noseScratch.y, noseScratch.z) > AeroConfig.stallAoADeg;
    }

    /**
     * How much of the lumped whole-hull lift still applies to this ship.
     *
     * <p>A craft with real aerodynamic surfaces on it — Xeno wing panels, Create sails, anything
     * else implementing Sable's lift provider — already gets lift from Sable's own per-block
     * pass, applied at those surfaces' centre. Adding the lumped hull term on top would count
     * the same lift twice and make a winged craft float unreasonably. So the base term steps
     * aside as soon as the ship has any real surface; flap lift is unaffected, because flaps are
     * a high-lift device layered on top of whatever wing is there.
     */
    private static double baseLiftFactor(ServerSubLevel ship) {
        if (AeroConfig.aeroModelBaseLiftWithWings) return 1.0;
        try {
            return ship.getPlot().getLiftProviders().isEmpty() ? 1.0 : 0.0;
        } catch (Throwable ignored) {
            // Never let an aerodynamic nicety break the flight tick.
            return 1.0;
        }
    }

    /**
     * Drive every role-assigned wing panel's visual deflection from what it is actually tracking.
     *
     * <p>Rescanned every {@link #PANEL_SCAN_INTERVAL_TICKS} ticks rather than continuously — this
     * writes block states, which re-renders a chunk of the ship, so it is throttled the same way
     * the old flap-only version was, just on a fixed interval instead of a threshold crossing
     * (a PITCH/ROLL/YAW panel's error can cross zero far more often than flaps toggle).
     *
     * <p>PITCH/ROLL/YAW roles read the stabilizer's own world-space attitude error
     * ({@link AeroStabilizerSystem#lastError}) decomposed onto the ship's body axes — the error's
     * component along the lateral (right) axis is pitch error, along up is yaw error, along the
     * nose is roll error, since that is the axis each of those rotations happens around.
     *
     * <p>The positions are copied out before any block is changed. Setting a block updates the
     * plot's own lift-provider map, so iterating it while writing into it would be a
     * modification during iteration.
     *
     * <p>Only panels in {@code linkedPanels} are driven — a panel a hull happens to have
     * somewhere is not automatically this host's to command, matching the same ownership rule
     * paired thrusters already enforce for thrust.
     */
    private void updatePanelDeflections(AeroBus bus, ServerSubLevel ship, Level level,
                                        Vector3dc bodyNose, Vector3dc bodyUp, Set<BlockPos> linkedPanels) {
        if (level == null || level.isClientSide) return;
        if (linkedPanels.isEmpty()) return;
        if (--panelScanCooldown > 0) return;
        panelScanCooldown = PANEL_SCAN_INTERVAL_TICKS;

        // Right, in the same un-rotated ship-local frame bodyNose/bodyUp already arrive in —
        // needed below to tell a ROLL panel which side of the centerline it is on, in the same
        // frame as panel BlockPos and Sable's own center of mass (see resolvePanel's comment on
        // panel positions being ship-local). The world-space rightScratch computed just after
        // this is a *different* vector (nose/up rotated into world space), used only for the
        // attitude-error decomposition below — do not conflate the two.
        rightLocalScratch.set(bodyNose).cross(bodyUp);
        if (rightLocalScratch.lengthSquared() > 1.0e-8) rightLocalScratch.normalize();

        noseScratch.set(bodyNose);
        ship.logicalPose().orientation().transform(noseScratch);
        upScratch.set(bodyUp);
        ship.logicalPose().orientation().transform(upScratch);
        rightScratch.set(noseScratch).cross(upScratch);
        if (rightScratch.lengthSquared() > 1.0e-8) rightScratch.normalize();

        Vector3dc centerOfMass = null;
        try {
            centerOfMass = ship.getMassTracker().getCenterOfMass();
        } catch (Throwable ignored) {
            // No auto-mirroring this scan; the manual INVERT toggle still works either way.
        }

        double[] err = AeroStabilizerSystem.lastError(ship);
        errorScratch.set(err[0], err[1], err[2]);
        double pitchErr = errorScratch.dot(rightScratch);
        double yawErr = errorScratch.dot(upScratch);
        double rollErr = errorScratch.dot(noseScratch);

        List<BlockPos> panels = null;
        try {
            for (var provider : ship.getPlot().getLiftProviders()) {
                if (!(provider.state().getBlock() instanceof WingPanelBlock)) continue;
                BlockPos pos = provider.pos().immutable();
                if (!linkedPanels.contains(pos)) continue;
                if (panels == null) panels = new ArrayList<>();
                panels.add(pos);
                if (panels.size() >= MAX_ANIMATED_PANELS) break;
            }
        } catch (Throwable ignored) {
            return;
        }
        if (panels == null) {
            AeroControlSurfaceTorque.sync(ship, List.of());
            return;
        }

        List<AeroControlSurfaceTorque.PanelState> torquePanels = new ArrayList<>(panels.size());
        for (BlockPos pos : panels) {
            WingPanelBlockEntity panel = resolvePanel(ship, level, pos);
            if (panel == null) continue;
            BlockState state = panel.getBlockState();
            if (!(state.getBlock() instanceof WingPanelBlock)) continue;
            PanelRole role = state.getValue(WingPanelBlock.ROLE);
            if (role == PanelRole.NONE) continue;
            double deg = deflectFor(role, bus, bus.mouseAim(), pitchErr, rollErr, yawErr);
            // Ailerons mirror automatically from which side of the hull they're mounted on —
            // a real rigger never needs to be told "the left one goes the other way." PITCH/YAW
            // surfaces (elevators, rudders) are real-world same-sign and must not be mirrored.
            if (role == PanelRole.ROLL && centerOfMass != null) {
                double sign = Math.signum(
                        (pos.getX() + 0.5 - centerOfMass.x()) * rightLocalScratch.x
                                + (pos.getY() + 0.5 - centerOfMass.y()) * rightLocalScratch.y
                                + (pos.getZ() + 0.5 - centerOfMass.z()) * rightLocalScratch.z);
                if (sign != 0.0) deg *= sign;
            }
            if (state.getValue(WingPanelBlock.INVERT)) deg = -deg;
            panel.setTargetDeflectDeg(deg);
            torquePanels.add(AeroControlSurfaceTorque.PanelState.of(
                    pos, state.getValue(WingPanelBlock.AXIS), deg));
        }
        AeroControlSurfaceTorque.sync(ship, torquePanels);
    }

    /**
     * Wing panels live in the ship's plot, not the parent overworld at the same BlockPos.
     * Sable's plot chunk map is addressed in local chunk coords; {@code plot.toLocal}
     * converts a global chunk pos when the stored panel pos is world-space. Fall back to
     * the parent world so a not-yet-assembled hull still animates.
     */
    private static WingPanelBlockEntity resolvePanel(ServerSubLevel ship, Level level, BlockPos pos) {
        try {
            var plot = ship.getPlot();
            ChunkPos raw = new ChunkPos(pos);
            LevelChunk chunk = plot.getChunk(raw);
            if (chunk == null) {
                chunk = plot.getChunk(plot.toLocal(raw));
            }
            if (chunk != null) {
                BlockEntity be = chunk.getBlockEntity(pos);
                if (be instanceof WingPanelBlockEntity panel) return panel;
            }
        } catch (Throwable ignored) {
            // Plot API shape can differ across Sable builds; parent-world fallback is next.
        }
        if (level != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel) {
            return panel;
        }
        return null;
    }

    /**
     * In mouse-aim mode, PITCH/ROLL/YAW read attitude error exactly as before (the PD
     * stabilizer is what's actually flying the ship there, and these visualize/apply torque
     * from the same error it's correcting). In keyboard mode there is no attitude-hold error to
     * read — {@link #tick} disables the stabilizer entirely — so PITCH/ROLL/YAW instead read the
     * pilot's raw stick position directly (A/D, Q/E, W/S respectively — see
     * {@code XenoFlightControls}), proportional like a real control surface, rather than
     * commanding any absolute attitude at all.
     */
    private static double deflectFor(PanelRole role, AeroBus bus, boolean mouseAim,
                                     double pitchErr, double rollErr, double yawErr) {
        return switch (role) {
            case NONE -> 0.0;
            case FLAP -> bus.flap() * MAX_DEFLECT_DEG;
            case PITCH -> mouseAim ? fromError(pitchErr) : bus.pitchStick() * MAX_DEFLECT_DEG;
            case ROLL -> mouseAim ? fromError(rollErr) : bus.rollStick() * MAX_DEFLECT_DEG;
            case YAW -> mouseAim ? fromError(yawErr) : bus.yawStick() * MAX_DEFLECT_DEG;
            case BRAKE -> bus.airBrakeEngaged() ? MAX_DEFLECT_DEG : 0.0;
        };
    }

    /** Maps error magnitude onto a continuous deflection, centered inside the deadband and
     * reaching {@link #MAX_DEFLECT_DEG} at {@link #ERROR_SATURATION}. */
    private static double fromError(double err) {
        double magnitude = Math.abs(err);
        if (magnitude < ERROR_DEADBAND) return 0.0;
        double t = Mth.clamp((magnitude - ERROR_DEADBAND) / (ERROR_SATURATION - ERROR_DEADBAND), 0.0, 1.0);
        return Math.copySign(t * MAX_DEFLECT_DEG, err);
    }

    /**
     * Hand the ship back to plain physics.
     *
     * <p>The stabilizer and the aerodynamic force are both latched on the physics thread: they
     * keep applying the last published value until told otherwise. Releasing one without the
     * other leaves a disengaged ship still generating lift, so they are always released together.
     */
    public static void release(ServerSubLevel ship) {
        if (ship == null) return;
        AeroStabilizerSystem.clear(ship);
        AeroAeroForceSystem.clear(ship);
        AeroControlSurfaceTorque.clearShip(ship);
    }
}
