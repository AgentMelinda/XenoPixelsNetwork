package net.bullettrain.xenopixelsmod.combat.fx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The purple dissolve stamped on a Hakai target while the channel runs.
 *
 * <p>Reuses {@link SilhouetteFx}'s body-shaped particle stamper — the same one
 * {@code VanishShadeFx} uses for the vanish departure shade — at the target's own position
 * instead of the caster's, with density and scale ramping up across the channel so the
 * target visibly fills in with dust rather than popping straight to fully erased.
 */
public final class HakaiFx {

    /** Violet body colour for the dissolving silhouette. */
    private static final Vector3f BODY = new Vector3f(0.42f, 0.10f, 0.62f);
    /** Brighter magenta rim, separating the outline from the body fill. */
    private static final Vector3f RIM = new Vector3f(0.78f, 0.35f, 1.0f);

    private HakaiFx() {
    }

    /**
     * One tick's worth of dissolve on {@code target}.
     *
     * @param progress 0 at channel start, 1 at completion. Drives both the silhouette's
     *                 density (how much of the body is already "dust") and the swirl's
     *                 pull-inward intensity.
     */
    public static void tick(ServerLevel level, LivingEntity target, float progress) {
        if (level == null || target == null) return;
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));

        Vec3 feet = target.position();
        float height = Math.max(0.5f, target.getBbHeight());
        float width = Math.max(0.3f, target.getBbWidth());

        // Density ramps from a faint dusting to a full figure; scale grows slightly too, so
        // the last moments read as denser rather than just "more points."
        double density = 0.15 + 0.85 * clamped;
        float scale = 1.0f + 0.4f * clamped;
        SilhouetteFx.stamp(level, feet, target.yHeadRot, height, width, density, BODY, RIM, scale);

        swirl(level, feet, height, clamped);
    }

    /**
     * Ambient particles orbiting the target, reading as "being pulled apart." Vanilla's
     * {@code REVERSE_PORTAL} already animates inward on its own, so a ring of points around
     * the target with a small spread is enough to sell the pull without needing explicit
     * per-particle velocity steering.
     */
    private static void swirl(ServerLevel level, Vec3 feet, float height, float progress) {
        int count = 2 + Math.round(4 * progress);
        double radius = 0.9 - 0.5 * progress;
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            double y = feet.y + Math.random() * height;
            double x = feet.x + Math.cos(angle) * radius;
            double z = feet.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }
}
