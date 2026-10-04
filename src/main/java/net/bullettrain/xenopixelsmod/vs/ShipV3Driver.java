package net.bullettrain.xenopixelsmod.vs;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileGuidance;
import net.bullettrain.xenopixelsmod.missile.MissilePhase;
import net.bullettrain.xenopixelsmod.missile.v3.GuidanceV3;
import net.minecraft.world.phys.Vec3;

/**
 * Guidance V3 for a Sable ship missile. Holds the flight's V3 state and turns each game tick's
 * position and velocity (blocks, m/s) into an acceleration command in m/s² for the controller's
 * physics tick, which applies exactly that plus the configured gravity and drag.
 *
 * <p>Level-free so a whole ship shot can be simulated. Unlike V1 there are no hidden thrust
 * multipliers: the planner and the flight use the same engine acceleration.
 */
public final class ShipV3Driver {
    private static final double TICKS_PER_SECOND = 20.0;
    /** A round this far below the target height and still falling has come down short. */
    private static final double BELOW_TARGET = 3.0;

    private final GuidanceV3.Params params;
    private GuidanceV3.Phase phase = GuidanceV3.Phase.RAIL;
    private int phaseTicks;
    private int fuelTicks;
    private int flightTicks;

    private ShipV3Driver(GuidanceV3.Params params, int fuelTicks) {
        this.params = params;
        this.fuelTicks = fuelTicks;
    }

    /**
     * @param boostAccel the block's boost setting (from the speed level, packets or CC)
     * @param thrusters paired thrusters; each adds 25% engine (see {@link GuidanceV3#engineAccel})
     */
    public static ShipV3Driver plan(Vec3 launch, Vec3 target, double boostAccel, int thrusters, int boostTicks,
                                    double gravitySi, double drag, GuidanceV3.Mode mode, GuidanceV3.Arc arc,
                                    double desiredApexY, double cruiseY, boolean terminal, Vec3 railAxis) {
        double g = BallisticCalculator.toTickGravity(gravitySi);
        double engine = GuidanceV3.engineAccel(MissileGuidance.burnPerTick(boostAccel), thrusters, g);
        GuidanceV3.Params params = GuidanceV3.Params.plan(launch, target, engine, boostTicks, g, drag, mode, arc, desiredApexY, cruiseY, terminal,
                16.0, railAxis);
        return new ShipV3Driver(params, boostTicks);
    }

    /** What the controller does this tick. {@code accelSi} excludes gravity. */
    public record Output(Vec3 accelSi, Vec3 nose, MissilePhase phase, boolean impact, String status) {}

    /**
     * @param onGround true when the hull is touching terrain (the caller checks the world)
     */
    public Output tick(Vec3 pos, Vec3 velSi, Vec3 target, Vec3 targetVelSi, boolean onGround) {
        flightTicks++;
        Vec3 vel = velSi.scale(1.0 / TICKS_PER_SECOND);
        Vec3 targetVel = targetVelSi == null ? Vec3.ZERO : targetVelSi.scale(1.0 / TICKS_PER_SECOND);
        boolean afterBoost = phase == GuidanceV3.Phase.COAST || phase == GuidanceV3.Phase.TERMINAL;
        if (afterBoost && flightTicks > 40) {
            if (onGround) return new Output(Vec3.ZERO, unit(velSi), phaseOf(phase), true, "V3 ground impact");
            if (pos.y < target.y - BELOW_TARGET && vel.y < 0) {
                return new Output(Vec3.ZERO, unit(velSi), phaseOf(phase), true, "V3 came down short");
            }
        }
        GuidanceV3.Command c = GuidanceV3.step(
                new GuidanceV3.State(pos, vel, target, targetVel, phase, phaseTicks, fuelTicks), params);
        phaseTicks = c.phase() == phase ? phaseTicks + 1 : 0;
        phase = c.phase();
        if (c.engineOn()) fuelTicks = Math.max(0, fuelTicks - 1);
        // blocks/tick² -> m/s² (20 ticks per second, squared).
        Vec3 accelSi = c.accel().scale(TICKS_PER_SECOND * TICKS_PER_SECOND);
        String status = String.format("V3 %s a=%.1f/%.1f m/s²", phase, accelSi.length(),
                params.engineAccel() * TICKS_PER_SECOND * TICKS_PER_SECOND);
        return new Output(accelSi, c.nose(), phaseOf(phase), false, status);
    }

    /**
     * Whether the last command should keep flying through a game-thread stall. After the motor
     * is off V3 only nudges, so holding it keeps the arc; V1's brake would cancel the flight.
     */
    public boolean holdThroughStall() {
        return phase == GuidanceV3.Phase.COAST || phase == GuidanceV3.Phase.TERMINAL;
    }

    public GuidanceV3.Params params() {
        return params;
    }

    public int fuelTicks() {
        return fuelTicks;
    }

    static MissilePhase phaseOf(GuidanceV3.Phase phase) {
        return switch (phase) {
            case RAIL -> MissilePhase.EJECT;
            case BOOST -> MissilePhase.BOOST;
            case COAST -> MissilePhase.COAST;
            case TERMINAL -> MissilePhase.TERMINAL;
        };
    }

    private static Vec3 unit(Vec3 v) {
        double length = v.length();
        return length < 1.0e-9 ? new Vec3(0, 1, 0) : v.scale(1.0 / length);
    }
}
