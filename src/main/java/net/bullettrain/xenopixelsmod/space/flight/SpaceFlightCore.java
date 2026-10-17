package net.bullettrain.xenopixelsmod.space.flight;

import org.joml.Vector3d;

/**
 * The Sable-native flight core that the {@code aero/} stack migrates onto.
 *
 * <p><b>This is the seam, not the migration.</b> {@code aero/} has 126 import sites across block
 * entities, combat, missiles, networking, HUD and the pilot-seat entity — and {@code pilot_seat} /
 * {@code pilot_seat_fork} are registered entity types sitting in live worlds. Swapping all of that in
 * one pass would break ship flight with no way to attribute the regression. So this class defines the
 * <i>contract</i> the new core honours, and the existing {@code AeroAeroForceSystem} pipeline keeps
 * running underneath until callers move over one at a time.
 *
 * <p>The contract is deliberately the one {@code AeroAeroForceSystem.setAeroAcceleration} already
 * consumes: a caller publishes a <b>world-space linear acceleration</b>, and the physics-tick side
 * rotates it into body space and turns it into {@code mass * accel * dt} of impulse. Anything that can
 * produce those three doubles can drive a ship today, which is what makes this a drop-in rather than a
 * rewrite. {@code AeroAeroModel.compute} produces them from an atmosphere model; this produces them
 * from atmosphere <i>plus</i> chart gravity, and reduces to the same answer when density is the only
 * force in play.
 *
 * <p><b>Pure, and that is the point.</b> No Sable types, no Minecraft types, no entity, no level. Input
 * is three small value records, output is written into caller-owned vectors. That means the entire
 * flight model is unit-testable and benchmarkable without booting the game, and the per-tick path
 * allocates nothing — which matters because §13's Q21 puts hundreds of pods on this maths every tick.
 *
 * <p>Atmospheric and orbital flight are one model rather than two switched between, because the §3
 * handoff has to be continuous: a ship crossing the exit altitude must not feel a discontinuity in the
 * forces acting on it. Density simply falls to zero and the drag term vanishes with it.
 */
public final class SpaceFlightCore {

    /**
     * What the pilot is asking for, all in {@code [-1, 1]} except throttle.
     *
     * @param throttle forward thrust fraction; {@code [-1, 1]} where negative is reverse
     * @param pitch    nose up/down rate request
     * @param yaw      nose left/right rate request
     * @param roll     rotation about the forward axis
     * @param strafe   lateral translation request, for docking and pod manoeuvring
     * @param lift     vertical translation request, independent of attitude
     */
    public record Intent(double throttle, double pitch, double yaw, double roll,
                         double strafe, double lift) {
        /** The neutral stick: no thrust, no rotation, no translation. */
        public static final Intent IDLE = new Intent(0, 0, 0, 0, 0, 0);

        /**
         * Clamps every axis into {@code [-1, 1]}, so a corrupt or malicious packet cannot produce an
         * unbounded force.
         *
         * <p>NaN and infinity are handled differently on purpose. NaN carries no direction at all, so
         * it degrades to neutral — the stick is centred. An infinite value does carry a direction and
         * is merely excessive, so it clamps to full deflection, which is what "clamp" should mean and
         * which keeps the result inside the ship's real authority either way. Treating both as neutral
         * would silently discard a pilot's input that happened to arrive as infinity; treating NaN as
         * a direction would propagate it into every force the model computes.
         */
        public Intent sanitized() {
            return new Intent(
                    clamp(throttle), clamp(pitch), clamp(yaw), clamp(roll),
                    clamp(strafe), clamp(lift));
        }

        private static double clamp(double value) {
            // NaN has to go first: Math.min/Math.max propagate it, so it would survive the clamp.
            if (Double.isNaN(value)) return 0.0;
            return Math.max(-1.0, Math.min(1.0, value));
        }
    }

    /**
     * What the ship is doing right now.
     *
     * @param velocity        world-space linear velocity, blocks per second
     * @param angularVelocity world-space rotational rate, radians per second
     * @param mass            the sub-level's tracked mass; guarded against zero by the caller because
     *                        Sable's mass tracker can read zero for a freshly assembled body
     */
    public record Body(Vector3d velocity, Vector3d angularVelocity, double mass) {
        public double safeMass() {
            return mass > 0.0 && Double.isFinite(mass) ? mass : 1.0;
        }
    }

    /**
     * What the ship is flying through.
     *
     * @param gravity world-space gravitational acceleration, taken from
     *                {@link net.bullettrain.xenopixelsmod.space.chart.ChartRegistry#gravityAt}. Already
     *                range-cut and contributor-capped, so this is the expensive part done once.
     * @param density atmospheric density at the current altitude. Zero in vacuum, which is most of the
     *                Voidsea; the existing {@code AeroConfig.airDensity} value inside a planet's
     *                atmosphere. Density rather than altitude, because the caller already knows which
     *                body it is near and this class should not have to redo that lookup.
     * @param forward the ship's current forward unit vector in world space, needed so throttle and
     *                strafe produce forces along the hull rather than along the world axes
     * @param up      the ship's current up unit vector in world space
     * @param right   the ship's current starboard unit vector in world space
     */
    public record Environment(Vector3d gravity, double density,
                              Vector3d forward, Vector3d up, Vector3d right) {
        /** Vacuum with no gravity, for tests and for a body that contributes nothing. */
        public static Environment empty() {
            return new Environment(new Vector3d(), 0.0,
                    new Vector3d(0, 0, -1), new Vector3d(0, 1, 0), new Vector3d(1, 0, 0));
        }
    }

    /**
     * Tuning, all of it headed for {@code xenopixelsmod-space.json}. Kept as one record rather than
     * static config reads so the solver stays pure and a test can pin every value.
     *
     * @param maxThrustAccel   acceleration at full throttle, blocks/s², already mass-normalised
     * @param maxStrafeAccel   lateral translation authority
     * @param maxLiftAccel     vertical translation authority
     * @param maxAngularAccel  rotational authority, radians/s²
     * @param angularDamping   how fast rotation decays with no input; space has no air to do it for us,
     *                         so this is RCS-style rate damping and it is what stops a ship spinning
     *                         forever after one tap
     * @param dragCoefficient  quadratic drag scale, multiplied by density
     * @param linearDamping    velocity decay in vacuum, for the arcade feel option; zero is Newtonian
     */
    public record Tuning(double maxThrustAccel, double maxStrafeAccel, double maxLiftAccel,
                         double maxAngularAccel, double angularDamping,
                         double dragCoefficient, double linearDamping) {
        public static Tuning defaults() {
            return new Tuning(40.0, 12.0, 12.0, 3.0, 4.0, 0.012, 0.0);
        }
    }

    private SpaceFlightCore() {
    }

    /**
     * Solves one tick of flight into world-space <b>accelerations</b>.
     *
     * <p>Acceleration rather than impulse, and no {@code deltaSeconds} parameter, because that is the
     * split {@code AeroAeroForceSystem} already uses: the game thread publishes a world-space
     * acceleration and the physics-tick side owns the step — it clamps {@code deltaSeconds} to
     * {@code [0, 0.1]}, multiplies by the sub-level's tracked mass, rotates into body space and calls
     * {@code applyLinearImpulse}. Duplicating the clamp here would mean two places that have to agree
     * on it, and a caller that forgot would be a physics explosion.
     *
     * @param intent     what the pilot asked for; sanitised internally so a bad packet is bounded
     * @param body       current motion and mass
     * @param environment gravity, density and the hull's basis vectors
     * @param tuning     the model's constants
     * @param linearOut  caller-owned vector, receives the world-space linear acceleration to publish
     * @param angularOut caller-owned vector, receives the angular acceleration along the hull axes
     * @return {@code linearOut}, so the common single-output case can be chained
     */
    public static Vector3d solve(Intent intent, Body body, Environment environment, Tuning tuning,
                                 Vector3d linearOut, Vector3d angularOut) {
        linearOut.set(0, 0, 0);
        angularOut.set(0, 0, 0);
        if (body == null || environment == null || tuning == null) return linearOut;

        Intent want = intent == null ? Intent.IDLE : intent.sanitized();

        // --- Translation: thrust along the hull basis, not the world axes. ---
        addScaled(linearOut, environment.forward(), want.throttle() * tuning.maxThrustAccel());
        addScaled(linearOut, environment.right(), want.strafe() * tuning.maxStrafeAccel());
        addScaled(linearOut, environment.up(), want.lift() * tuning.maxLiftAccel());

        // --- Gravity. Already summed and capped by the chart; just add it. ---
        Vector3d gravity = environment.gravity();
        if (gravity != null && gravity.isFinite()) linearOut.add(gravity);

        // --- Quadratic drag, which is what makes atmospheric flight behave and vanishes in vacuum. ---
        double density = environment.density();
        Vector3d velocity = body.velocity();
        if (density > 0.0 && velocity != null && velocity.isFinite()) {
            double speed = velocity.length();
            if (speed > 1.0e-6) {
                // F/m = 0.5 * rho * Cd * v^2, opposing motion. Mass is folded into the drag
                // coefficient, matching how AeroAeroModel expresses wing area per unit mass.
                // Applied inline rather than through a scratch vector: this runs per pod per tick, and
                // a shared mutable scratch would not be safe across the render and physics threads.
                double scale = -0.5 * density * tuning.dragCoefficient() * speed;
                linearOut.add(velocity.x * scale, velocity.y * scale, velocity.z * scale);
            }
        }

        // --- Optional vacuum damping, for an arcade feel. Zero by default: Newtonian is the honest
        // answer in space and §22's "cartoon" options are a per-world choice, not a global one. ---
        if (tuning.linearDamping() > 0.0 && velocity != null && velocity.isFinite()) {
            addScaled(linearOut, velocity, -tuning.linearDamping());
        }

        if (!linearOut.isFinite()) linearOut.set(0, 0, 0);

        // --- Rotation: direct rate request from the stick, damped toward zero with no input. ---
        // Angular acceleration is expressed along the hull axes because that is what a pilot means by
        // pitch and roll; converting to world space is the caller's job, since only it knows the pose.
        angularOut.set(
                finite(want.pitch() * tuning.maxAngularAccel()),
                finite(want.yaw() * tuning.maxAngularAccel()),
                finite(want.roll() * tuning.maxAngularAccel()));
        Vector3d spin = body.angularVelocity();
        if (tuning.angularDamping() > 0.0 && spin != null && spin.isFinite()) {
            angularOut.add(spin.x * -tuning.angularDamping(),
                    spin.y * -tuning.angularDamping(),
                    spin.z * -tuning.angularDamping());
        }
        if (!angularOut.isFinite()) angularOut.set(0, 0, 0);
        return linearOut;
    }

    /**
     * Linear acceleration only, for callers that do not steer — pods, debris, unguided stages, and the
     * §25.2 pod swarm where hundreds of bodies need drag and gravity but never a stick input.
     */
    public static Vector3d solveLinear(Intent intent, Body body, Environment environment, Tuning tuning,
                                       Vector3d linearOut) {
        return solve(intent, body, environment, tuning, linearOut, SCRATCH_ANGULAR.get());
    }

    /**
     * Discard sink for the angular result. A ThreadLocal rather than one shared static because the
     * client render thread and the server physics thread can both be inside this model, and a shared
     * scratch vector would let one overwrite the other mid-solve.
     */
    private static final ThreadLocal<Vector3d> SCRATCH_ANGULAR = ThreadLocal.withInitial(Vector3d::new);

    private static void addScaled(Vector3d target, Vector3d direction, double scale) {
        if (direction == null || scale == 0.0 || !direction.isFinite()) return;
        target.add(direction.x * scale, direction.y * scale, direction.z * scale);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
