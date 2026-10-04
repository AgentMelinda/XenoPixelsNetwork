package net.bullettrain.xenopixelsmod.missile.v3;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Guidance V3: missile guidance that keeps correcting after the motor cuts off. Level-free and in
 * game units (blocks, ticks, blocks/tick²) so both ship missiles and tube missiles can use it and a
 * whole shot can be simulated in a test.
 *
 * <p>Phases:
 * <ul>
 *   <li><b>RAIL</b> - out of the launcher along its axis until clear.</li>
 *   <li><b>BOOST</b> - every tick, work out the velocity that carries the round over the planned
 *       apex and down onto the target (drag folded in by checking with {@link ArcPredictor}), and
 *       push toward it. The motor cuts off once that velocity is reached (no fuel limit).</li>
 *   <li><b>COAST</b> - predict where the current arc comes down and apply a bounded nudge that
 *       closes the gap in the time left. {@link Mode#BALLISTIC} gets a limited nudge (fins, motor
 *       off); {@link Mode#GUIDED} gets full engine authority and can hold a cruise altitude.</li>
 *   <li><b>TERMINAL</b> - the same correction with full authority close to the target.</li>
 * </ul>
 * The command is the acceleration to add on top of gravity; the caller applies gravity and drag.
 */
public final class GuidanceV3 {
    public enum Phase { RAIL, BOOST, COAST, TERMINAL }

    public enum Mode { BALLISTIC, GUIDED }

    public enum Arc { AUTO, HIGH, LOW }

    /** Coast nudge in ballistic mode, as a share of the engine's acceleration. */
    static final double BALLISTIC_AUTHORITY = 0.5;
    /** Lowest apex above the launcher, so the climb clears it. */
    static final double MIN_APEX_ABOVE = 32.0;
    /** Longest flight predicted, in ticks. */
    static final int MAX_PREDICT_TICKS = 40_000;
    /** Terminal starts inside this many ticks of impact, or this distance. */
    static final int TERMINAL_TICKS = 60;
    static final double TERMINAL_DISTANCE = 96.0;
    static final int RAIL_MAX_TICKS = 200;
    /** V3 engines push this many times V1's base for the same speed level. */
    public static final double ENGINE_SCALE = 2.0;
    /** Extra engine per paired thruster; no cap. */
    public static final double THRUSTER_BONUS = 0.25;
    /** The engine never drops below this many times gravity, so every missile climbs hard. */
    public static final double MIN_ENGINE_G = 3.0;

    private GuidanceV3() {}

    /**
     * The V3 engine, blocks/tick²: the speed level's push ({@code burnPerTick}) doubled, +25% per
     * paired thruster, and never under 3 g. There is no fuel limit - the engine burns as long as the
     * boost needs.
     */
    public static double engineAccel(double burnPerTick, int thrusters, double gravityPerTick) {
        double scaled = Math.max(0.0, burnPerTick) * ENGINE_SCALE * (1.0 + THRUSTER_BONUS * Math.max(0, thrusters));
        return Math.max(MIN_ENGINE_G * Math.max(0.0, gravityPerTick), scaled);
    }

    /**
     * Fixed for the flight. {@code apexY} is absolute. {@code launchVelocity} is the velocity the
     * boost aims for from the launcher, and {@code reachable} says whether the fuel covers it.
     */
    public record Params(double engineAccel, double gravity, double drag, Mode mode, double apexY,
                         double cruiseY, boolean terminal, double railClearance, Vec3 railAxis, Vec3 railOrigin,
                         Vec3 launchVelocity, boolean reachable) {

        public static Params plan(Vec3 launch, Vec3 target, double engineAccel, int fuelTicks, double gravity,
                                  double drag, Mode mode, Arc arc, double desiredApexY, double cruiseY,
                                  boolean terminal, double railClearance, Vec3 railAxis) {
            Vec3 axis = railAxis == null || railAxis.lengthSqr() < 1.0e-9 ? new Vec3(0, 1, 0) : railAxis.normalize();
            Vec3 railEnd = launch.add(axis.scale(railClearance));
            double range = Math.hypot(target.x - launch.x, target.z - launch.z);
            double high = Math.max(launch.y, target.y);
            double apex;
            if (desiredApexY > 0) {
                apex = desiredApexY;
            } else {
                double share = switch (arc == null ? Arc.AUTO : arc) {
                    case HIGH -> 0.6;
                    case LOW -> 0.15;
                    case AUTO -> 0.35;
                };
                apex = high + Math.max(MIN_APEX_ABOVE, range * share);
            }
            apex = Math.max(apex, Math.max(railEnd.y, high) + 8.0);
            Vec3 need = requiredVelocity(railEnd, target, apex, gravity, drag);
            // No fuel limit: any engine stronger than gravity gets there, it only takes longer.
            return new Params(engineAccel, gravity, drag, mode == null ? Mode.BALLISTIC : mode, apex, cruiseY,
                    terminal, railClearance, axis, launch, need, engineAccel > gravity * 1.05);
        }
    }

    /** Where the round is now. {@code targetVel} lets a moving target be led (zero for a fixed one). */
    public record State(Vec3 pos, Vec3 vel, Vec3 target, Vec3 targetVel, Phase phase, int phaseTicks, int fuelTicks) {}

    /** Acceleration to add this tick (gravity not included), where the nose points, and the phase. */
    public record Command(Vec3 accel, Vec3 nose, Phase phase, boolean engineOn) {}

    public static Command step(State s, Params p) {
        return switch (s.phase()) {
            case RAIL -> rail(s, p);
            case BOOST -> boost(s, p);
            case COAST, TERMINAL -> coast(s, p);
        };
    }

    private static Command rail(State s, Params p) {
        double along = s.pos().subtract(p.railOrigin()).dot(p.railAxis());
        if (along >= p.railClearance() || s.phaseTicks() >= RAIL_MAX_TICKS) return boost(s, p);
        // Straight out along the axis, holding gravity so the rail speed is what is commanded.
        Vec3 accel = p.railAxis().scale(p.engineAccel()).add(0, p.gravity(), 0);
        return new Command(clamp(accel, p.engineAccel() * 1.5), p.railAxis(), Phase.RAIL, true);
    }

    private static Command boost(State s, Params p) {
        Vec3 aim = lead(s, p);
        Vec3 want = requiredVelocity(s.pos(), aim, p.apexY(), p.gravity(), p.drag());
        Vec3 error = want.subtract(s.vel());
        double tolerance = Math.max(0.01, want.length() * 0.004);
        if (error.length() <= tolerance) {
            return coast(new State(s.pos(), s.vel(), s.target(), s.targetVel(), Phase.COAST, 0, s.fuelTicks()), p);
        }
        // Reach the wanted velocity next tick if the engine can; gravity is added back so it cancels.
        Vec3 accel = clamp(error.add(0, p.gravity(), 0), p.engineAccel());
        Vec3 nose = accel.lengthSqr() < 1.0e-12 ? s.vel() : accel;
        return new Command(accel, unit(nose), Phase.BOOST, true);
    }

    private static Command coast(State s, Params p) {
        Vec3 aim = lead(s, p);
        boolean guided = p.mode() == Mode.GUIDED;
        double authority = guided ? p.engineAccel() : p.engineAccel() * BALLISTIC_AUTHORITY;

        if (guided && p.cruiseY() > 0 && s.phase() != Phase.TERMINAL) {
            Command cruise = cruise(s, p, aim);
            if (cruise != null) return cruise;
        }

        ArcPredictor.Landing landing = ArcPredictor.predict(s.pos(), s.vel(), p.gravity(), p.drag(), aim.y,
                MAX_PREDICT_TICKS);
        boolean terminal = p.terminal() && (s.phase() == Phase.TERMINAL
                || s.pos().distanceTo(aim) < TERMINAL_DISTANCE
                || (landing != null && landing.ticks() < TERMINAL_TICKS));
        Phase phase = terminal ? Phase.TERMINAL : Phase.COAST;
        if (terminal) authority = p.engineAccel();

        Vec3 accel;
        if (landing == null) {
            // The arc does not come back down to the target height (target above it): climb toward it.
            accel = unit(aim.subtract(s.pos())).scale(authority).add(0, p.gravity(), 0);
        } else {
            Vec3 miss = new Vec3(aim.x - landing.point().x, 0, aim.z - landing.point().z);
            double t = Math.max(1.0, landing.ticks());
            // Constant acceleration a over the remaining t ticks moves the landing by a*t²/2.
            accel = miss.scale(2.0 / (t * t));
            if (landing.ticks() <= 2) {
                // Last ticks: aim straight at it.
                Vec3 direct = aim.subtract(s.pos()).subtract(s.vel());
                accel = direct.add(0, p.gravity(), 0);
            }
        }
        return new Command(clamp(accel, authority), unit(s.vel()), phase, false);
    }

    /** Guided cruise: hold {@code cruiseY} toward the target, then hand over to the arc law to dive. */
    private static Command cruise(State s, Params p, Vec3 aim) {
        Vec3 flat = new Vec3(aim.x - s.pos().x, 0, aim.z - s.pos().z);
        double distance = flat.length();
        double drop = Math.max(0.0, p.cruiseY() - aim.y);
        if (distance < Math.max(TERMINAL_DISTANCE, drop * 1.5)) return null;
        double speed = Math.max(0.8, Math.hypot(s.vel().x, s.vel().z));
        Vec3 wantFlat = flat.scale(speed / Math.max(distance, 1.0e-6));
        double wantY = Mth.clamp((p.cruiseY() - s.pos().y) * 0.05, -speed * 0.5, speed * 0.5);
        Vec3 want = new Vec3(wantFlat.x, wantY, wantFlat.z);
        Vec3 accel = clamp(want.subtract(s.vel()).scale(0.2).add(0, p.gravity(), 0), p.engineAccel());
        return new Command(accel, unit(s.vel()), Phase.COAST, false);
    }

    /** The target where it will be when the round arrives (the target itself when not moving). */
    private static Vec3 lead(State s, Params p) {
        if (s.targetVel() == null || s.targetVel().lengthSqr() < 1.0e-9) return s.target();
        double speed = Math.max(0.5, s.vel().length());
        double eta = s.pos().distanceTo(s.target()) / speed;
        return s.target().add(s.targetVel().scale(Math.min(eta, 2_400)));
    }

    /**
     * The velocity that carries a round from {@code from} over {@code apexY} and down onto
     * {@code to}: solved without drag, then corrected by flying it through {@link ArcPredictor}.
     */
    public static Vec3 requiredVelocity(Vec3 from, Vec3 to, double apexY, double gravity, double drag) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double range = Math.hypot(dx, dz);
        double dy = to.y - from.y;
        double climb = Math.max(Math.max(apexY - from.y, 0.5), dy + 0.5);
        double vy = Math.sqrt(2.0 * gravity * climb);
        double time = vy / gravity + Math.sqrt(2.0 * (climb - dy) / gravity);
        if (range < 1.0e-6) return new Vec3(0, vy, 0);
        double ux = dx / range;
        double uz = dz / range;
        double vh = range / time;
        Vec3 v = new Vec3(ux * vh, vy, uz * vh);
        // Fold in drag and the game's tick stepping: fly it, then shift by the miss per tick of flight.
        for (int i = 0; i < 4; i++) {
            ArcPredictor.Landing landing = ArcPredictor.predict(from, v, gravity, drag, to.y, MAX_PREDICT_TICKS);
            if (landing == null || landing.ticks() <= 0) break;
            Vec3 miss = new Vec3(to.x - landing.point().x, 0, to.z - landing.point().z);
            if (miss.length() < 0.25) break;
            v = v.add(miss.scale(1.0 / landing.ticks()));
        }
        return v;
    }

    private static Vec3 clamp(Vec3 v, double max) {
        double length = v.length();
        return length <= max || length < 1.0e-12 ? v : v.scale(max / length);
    }

    private static Vec3 unit(Vec3 v) {
        double length = v.length();
        return length < 1.0e-9 ? new Vec3(0, 1, 0) : v.scale(1.0 / length);
    }
}
