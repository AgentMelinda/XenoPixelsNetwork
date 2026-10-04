package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileGuidance;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.minecraft.world.phys.Vec3;

/**
 * Guidance V3 for tube (entity) missiles after they leave the silo. Tube missiles are kinematic -
 * the entity sets its own velocity - so each tick is: V3 command + gravity + drag, straight into
 * the velocity, from the missile's current position.
 *
 * <p>A cruise altitude on the guidance computer selects {@link GuidanceV3.Mode#GUIDED} (hold that
 * height, then dive); otherwise the round flies {@link GuidanceV3.Mode#BALLISTIC} with coast correction.
 */
public final class TubeV3 {
    private final GuidanceV3.Params params;
    private final double gravity;
    private final double drag;
    private GuidanceV3.Phase phase = GuidanceV3.Phase.BOOST;
    private int phaseTicks;
    private int fuelTicks;

    private TubeV3(GuidanceV3.Params params, double gravity, double drag, int fuelTicks) {
        this.params = params;
        this.gravity = gravity;
        this.drag = drag;
        this.fuelTicks = fuelTicks;
    }

    public static TubeV3 plan(Vec3 launch, Vec3 target, double boostAccel, int boostTicks, double gravitySi,
                              double drag, double apexY, double cruiseY, boolean terminal, double clearance,
                              Vec3 railAxis) {
        double g = BallisticCalculator.toTickGravity(gravitySi);
        GuidanceV3.Mode mode = cruiseY > 0 ? GuidanceV3.Mode.GUIDED : GuidanceV3.Mode.BALLISTIC;
        GuidanceV3.Params params = GuidanceV3.Params.plan(launch, target,
                GuidanceV3.engineAccel(MissileGuidance.burnPerTick(boostAccel), 0, g), boostTicks, g, drag, mode, GuidanceV3.Arc.AUTO, apexY, cruiseY, terminal, clearance, railAxis);
        return new TubeV3(params, g, drag, boostTicks);
    }

    /** Rebuilds a saved flight: same apex, phase and fuel left. */
    public static TubeV3 restore(Vec3 launch, Vec3 target, double boostAccel, int fuelLeft, double gravitySi,
                                 double drag, double apexY, double cruiseY, boolean terminal, double clearance,
                                 Vec3 railAxis, int phaseOrdinal) {
        TubeV3 v3 = plan(launch, target, boostAccel, Math.max(1, fuelLeft), gravitySi, drag, apexY, cruiseY,
                terminal, clearance, railAxis);
        v3.fuelTicks = Math.max(0, fuelLeft);
        GuidanceV3.Phase[] phases = GuidanceV3.Phase.values();
        v3.phase = phaseOrdinal >= 0 && phaseOrdinal < phases.length && phases[phaseOrdinal] != GuidanceV3.Phase.RAIL
                ? phases[phaseOrdinal] : GuidanceV3.Phase.BOOST;
        return v3;
    }

    /** The next velocity (blocks/tick) for a round at {@code pos} moving at {@code vel}. */
    public Vec3 step(Vec3 pos, Vec3 vel, Vec3 target) {
        GuidanceV3.Command c = GuidanceV3.step(
                new GuidanceV3.State(pos, vel, target, Vec3.ZERO, phase, phaseTicks, fuelTicks), params);
        phaseTicks = c.phase() == phase ? phaseTicks + 1 : 0;
        phase = c.phase();
        if (c.engineOn()) fuelTicks = Math.max(0, fuelTicks - 1);
        return ArcPredictor.dragged(vel.add(c.accel()).add(0, -gravity, 0), drag, pos.y, 1.0);
    }

    public MissilePhase phase() {
        return switch (phase) {
            case RAIL, BOOST -> MissilePhase.BOOST;
            case COAST -> MissilePhase.COAST;
            case TERMINAL -> MissilePhase.TERMINAL;
        };
    }

    public int phaseOrdinal() {
        return phase.ordinal();
    }

    public int fuelTicks() {
        return fuelTicks;
    }

    public double apexY() {
        return params.apexY();
    }

    public boolean reachable() {
        return params.reachable();
    }
}
