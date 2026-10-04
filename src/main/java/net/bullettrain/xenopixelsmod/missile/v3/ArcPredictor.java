package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.minecraft.world.phys.Vec3;

/**
 * Where an unpowered round lands: steps gravity and drag (the {@link BallisticCalculator} air model)
 * until the arc comes down through {@code targetY}. Units are blocks and ticks.
 *
 * <p>Guidance V3 asks this every tick, so the step grows with flight time: single ticks for the
 * first 64 steps, then doubling every 64 steps (capped at 32). The last part of any flight is
 * always predicted finely, because by then the whole remaining flight is short.
 */
public final class ArcPredictor {
    private static final int FINE_STEPS = 64;
    private static final double MAX_DT = 32.0;

    private ArcPredictor() {}

    /** Landing point and ticks until it, measured from now. */
    public record Landing(Vec3 point, int ticks) {}

    /**
     * @return the landing, or null when the arc never comes back down to {@code targetY} within
     *         {@code maxTicks}. A round already below it and falling lands where it is.
     */
    public static Landing predict(Vec3 pos, Vec3 vel, double gravityPerTick, double drag,
                                  double targetY, int maxTicks) {
        if (pos.y < targetY && vel.y <= 0) return new Landing(pos, 0);
        double dt = 1.0;
        double time = 0.0;
        int steps = 0;
        Vec3 p = pos;
        Vec3 v = vel;
        while (time < maxTicks) {
            Vec3 prev = p;
            Vec3 start = v;
            v = dragged(v.add(0, -gravityPerTick * dt, 0), drag, p.y, dt);
            // The game moves by the new velocity every tick; over dt ticks with velocity changing
            // linearly that sums to dt*(start+end)/2 + (end-start)/2 - exact for dt = 1.
            p = p.add(start.add(v).scale(dt * 0.5)).add(v.subtract(start).scale(0.5));
            time += dt;
            if (v.y < 0 && prev.y >= targetY && p.y < targetY) {
                double f = (prev.y - targetY) / (prev.y - p.y);
                Vec3 hit = prev.add(p.subtract(prev).scale(f));
                return new Landing(hit, (int) Math.ceil(time - dt + dt * f));
            }
            if (++steps % FINE_STEPS == 0 && dt < MAX_DT) dt *= 2.0;
        }
        return null;
    }

    /** Drag over {@code dt} ticks, the same law as the tube missiles (k * density * v^2). */
    public static Vec3 dragged(Vec3 velocity, double drag, double altitude, double dt) {
        double speed = velocity.length();
        if (speed < 1.0e-9 || drag <= 0.0) return velocity;
        double loss = Math.min(speed * 0.95,
                drag * BallisticCalculator.airDensityFactor(altitude) * speed * speed * dt);
        return velocity.scale(Math.max(0.0, 1.0 - loss / speed));
    }
}
