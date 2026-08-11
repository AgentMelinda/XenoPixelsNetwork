package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.fx.SilhouetteFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The black shade a fighter leaves behind when they vanish.
 *
 * <p>A humanoid silhouette of near-black dust is stamped at the departure point, wrapped in
 * electric arcs and a thunder crack, and holds for roughly a second before fading. The read is
 * "the body was here and something violent removed it" rather than the usual teleport puff.
 *
 * <p><b>Why particles and not a rendered model.</b> Drawing a real fading copy of the player
 * model would need a client renderer holding a pose snapshot per vanish, and would only ever be
 * as reliable as its hook into whatever is rendering players that frame. This is spawned
 * server-side, so every nearby client sees the same shade with no client-side state, no mixin,
 * and no dependency on the player renderer.
 *
 * <p><b>Cost.</b> The rest of this package is pointed about particle budget — {@link AfterimageFx}
 * explicitly avoids {@code FLASH} and caps its trail at six steps. A silhouette needs more points
 * than a trail to read as a body at all, so the count is bounded and scaled by
 * {@code vanishShadeDensity}, and it is emitted once per vanish rather than per tick. At the
 * default density that is about fifty particles, against a move already on a cooldown.
 */
public final class VanishShadeFx {

    /** Near-black rather than pure black: total black reads as a hole, not a figure. */
    private static final Vector3f SHADE = new Vector3f(0.05f, 0.04f, 0.08f);
    /** Rim colour, a cold violet that separates the silhouette from a dark background. */
    private static final Vector3f RIM = new Vector3f(0.42f, 0.16f, 0.62f);

    /**
     * Silhouette sample points in body-local space, as fractions of the entity's own size:
     * x across (right positive), y up from the feet, and a per-point size multiplier.
     *
     * <p>Authored rather than derived: a body sampled from the hitbox alone is a rectangle, and
     * a rectangle of dust does not read as a person. Head, torso, arms and legs each get their
     * own density so the shape survives being seen for under a second.
     */
    private VanishShadeFx() {
    }

    /**
     * Stamp the shade at {@code origin}, oriented to the way {@code owner} was facing.
     *
     * @param owner  the vanishing entity; supplies size and facing only, and may already have
     *               been teleported away by the time this is called
     * @param origin the position the shade is left at
     */
    public static void spawn(LivingEntity owner, Vec3 origin) {
        if (owner == null || origin == null) return;
        if (!XenoServerConfig.vanishShadeEnabled) return;
        if (!(owner.level() instanceof ServerLevel level)) return;

        double density = Math.max(0.0, Math.min(3.0, XenoServerConfig.vanishShadeDensity));
        if (density <= 0.0) return;

        float height = Math.max(0.5f, owner.getBbHeight());
        float width = Math.max(0.3f, owner.getBbWidth());
        // The figure itself is SilhouetteFx's; density above 1 only thickens the arcs below.
        SilhouetteFx.stamp(level, origin, owner.yHeadRot, height, width,
                Math.min(1.0, density), SHADE, RIM, 1.0f);

        arcs(level, origin, height, density);
        thunder(level, origin);
    }

    /** Electric arcs climbing the shade, plus a low ground crackle. */
    private static void arcs(ServerLevel level, Vec3 origin, float height, double density) {
        int count = (int) Math.round(6 * Math.min(1.5, density));
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    origin.x, origin.y + height * t, origin.z,
                    2, 0.28, 0.10, 0.28, 0.03);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                origin.x, origin.y + 0.05, origin.z, 4, 0.45, 0.02, 0.45, 0.06);
        // One soul-flame lick at the core reads as the "dark" half of the effect; the smoke
        // below it stops the silhouette from floating.
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                origin.x, origin.y + height * 0.5, origin.z, 2, 0.10, 0.25, 0.10, 0.01);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                origin.x, origin.y + 0.1, origin.z, 3, 0.25, 0.05, 0.25, 0.01);
    }

    private static void thunder(ServerLevel level, Vec3 origin) {
        float volume = (float) Math.max(0.0, Math.min(1.0, XenoServerConfig.vanishThunderVolume));
        if (volume <= 0.0f) return;
        // Impact carries the crack; the thunder roll underneath it is kept much quieter so a
        // vanish in a fight does not sound like a storm.
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, volume, 1.6f);
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, volume * 0.35f, 1.8f);
    }
}
