package net.bullettrain.xenopixelsmod.combat.fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Stamps a humanoid figure out of particles.
 *
 * <p>Extracted from {@code VanishShadeFx}, which authored this body table for the vanish shade
 * and is still its main user. The afterimage trail needs the same figure in a different colour
 * at a dozen points along a dash, and duplicating thirty hand-placed body points to get it would
 * guarantee the two drift apart.
 *
 * <p>The table is authored rather than derived from the hitbox on purpose: a body sampled from
 * the bounding box alone is a rectangle, and a rectangle of dust does not read as a person. Head,
 * torso, arms and legs each carry their own density so the shape survives being seen for well
 * under a second.
 */
public final class SilhouetteFx {

    /**
     * Body-local sample points as fractions of the entity's own size: x across (right positive),
     * y up from the feet, and a per-point size multiplier.
     */
    private static final float[][] BODY = {
            // head
            {0.00f, 0.90f, 1.35f}, {-0.10f, 0.88f, 1.1f}, {0.10f, 0.88f, 1.1f},
            {0.00f, 0.97f, 1.1f}, {0.00f, 0.81f, 1.1f},
            // torso
            {0.00f, 0.72f, 1.3f}, {0.00f, 0.62f, 1.3f}, {0.00f, 0.52f, 1.3f},
            {0.00f, 0.42f, 1.2f},
            {-0.13f, 0.68f, 1.1f}, {0.13f, 0.68f, 1.1f},
            {-0.13f, 0.55f, 1.1f}, {0.13f, 0.55f, 1.1f},
            {-0.11f, 0.44f, 1.0f}, {0.11f, 0.44f, 1.0f},
            // arms
            {-0.28f, 0.70f, 1.0f}, {0.28f, 0.70f, 1.0f},
            {-0.30f, 0.58f, 1.0f}, {0.30f, 0.58f, 1.0f},
            {-0.31f, 0.46f, 1.0f}, {0.31f, 0.46f, 1.0f},
            {-0.30f, 0.35f, 0.9f}, {0.30f, 0.35f, 0.9f},
            // legs
            {-0.11f, 0.30f, 1.1f}, {0.11f, 0.30f, 1.1f},
            {-0.12f, 0.20f, 1.1f}, {0.12f, 0.20f, 1.1f},
            {-0.12f, 0.10f, 1.0f}, {0.12f, 0.10f, 1.0f},
            {-0.12f, 0.02f, 1.0f}, {0.12f, 0.02f, 1.0f},
    };

    /** Every Nth point takes the rim colour instead of the body colour: the figure's outline. */
    private static final int RIM_STRIDE = 4;

    private SilhouetteFx() {
    }

    /** Number of points in the full figure, for callers budgeting particles across a trail. */
    public static int fullPointCount() {
        return BODY.length;
    }

    /**
     * Stamp one figure.
     *
     * @param origin  feet position
     * @param yawDeg  facing; the local X offsets are rotated into world space around it
     * @param height  entity height in blocks
     * @param width   entity width in blocks
     * @param density 0..1 fraction of the body points to keep. Points are thinned evenly by
     *                stride rather than truncated, so turning it down thins the whole figure
     *                instead of lopping off whichever body part is last in the table.
     * @param body    colour for interior points
     * @param rim     colour for outline points
     * @param scale   overall particle size multiplier
     */
    public static void stamp(ServerLevel level, Vec3 origin, double yawDeg,
                             float height, float width, double density,
                             Vector3f body, Vector3f rim, float scale) {
        stamp(level, origin, yawDeg, height, width, density, body, rim, scale, 1.0f);
    }

    /**
     * @param keepBelowYFrac 1 = full figure, 0 = none. Head is ~0.97, feet ~0.02, so
     *                       lowering this erases from the head down.
     */
    public static void stamp(ServerLevel level, Vec3 origin, double yawDeg,
                             float height, float width, double density,
                             Vector3f body, Vector3f rim, float scale, float keepBelowYFrac) {
        if (level == null || origin == null) return;
        double clamped = Math.max(0.0, Math.min(1.0, density));
        if (clamped <= 0.0) return;
        float cutoff = Math.max(0.0f, Math.min(1.0f, keepBelowYFrac));
        if (cutoff <= 0.0f) return;

        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(-yaw);
        double cos = Math.cos(-yaw);
        int keep = Math.max(1, (int) Math.round(1.0 / Math.max(1.0e-3, clamped)));

        for (int i = 0; i < BODY.length; i++) {
            if (clamped < 1.0 && (i % keep) != 0) continue;
            float[] point = BODY[i];
            if (point[1] > cutoff) continue;
            double lx = point[0] * width * 2.0;
            double x = origin.x + lx * cos;
            double z = origin.z + lx * sin;
            double y = origin.y + point[1] * height;

            boolean outline = (i % RIM_STRIDE) == 0;
            DustParticleOptions dust = new DustParticleOptions(
                    outline ? rim : body, point[2] * (outline ? 1.0f : 1.25f) * scale);
            // Zero velocity: a silhouette has to hold its shape for the moment it exists.
            level.sendParticles(dust, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
