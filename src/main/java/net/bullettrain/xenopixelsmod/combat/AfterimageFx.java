package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.fx.SilhouetteFx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * Afterimage trails: a chain of fading humanoid ghosts along the path a fighter just crossed.
 *
 * <p>This used to be a handful of coloured dust puffs, which said "something moved through here"
 * but not "a person was here a moment ago" — and the second is the whole point of the effect in
 * the games this is modelled on. It now stamps the same silhouette the vanish shade uses, once
 * per step, fading toward the destination so the trail reads with a direction.
 *
 * <p>Ghosts thin out along the trail rather than all rendering at full density: the oldest image
 * is the faintest, which is what gives the chain its sense of motion. That also keeps the
 * particle budget close to the old dust version despite drawing something far more legible.
 */
public final class AfterimageFx {

    /** Ghost body: near-black with a violet cast, the same family as the vanish shade. */
    private static final Vector3f GHOST_BODY = new Vector3f(0.10f, 0.07f, 0.16f);
    /** Ghost outline. Bright enough to separate the figure from a dark background. */
    private static final Vector3f GHOST_RIM = new Vector3f(0.55f, 0.28f, 0.85f);

    private static final DustParticleOptions MAGENTA =
            new DustParticleOptions(new Vector3f(0.85f, 0.35f, 0.95f), 1.0f);
    private static final DustParticleOptions CYAN =
            new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 0.9f);

    private AfterimageFx() {
    }

    /**
     * Server: a trail of ghosts from {@code from} to {@code to}.
     *
     * <p>The endpoints are skipped — the fighter is standing at one of them and just left the
     * other, so a ghost there would sit inside the real model and read as a rendering fault.
     */
    public static void spawnTrailServer(ServerPlayer player, Vec3 from, Vec3 to, int ghosts) {
        if (player == null || from == null || to == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int steps = Math.min(7, Math.max(3, ghosts + 2));
        float height = Math.max(0.5f, player.getBbHeight());
        float width = Math.max(0.3f, player.getBbWidth());
        // yHeadRot rather than getYRot: the player has usually already been teleported and
        // snapped to face a new target by the time this runs.
        double yaw = player.yHeadRot;

        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 at = from.add(to.subtract(from).scale(t));
            // Oldest ghost faintest: the density ramp is what makes the chain read as motion
            // rather than as a row of identical figures.
            double density = 0.28 + 0.5 * t;
            SilhouetteFx.stamp(level, at, yaw, height, width, density,
                    GHOST_BODY, GHOST_RIM, 0.9f);
        }

        // Landing puff, unchanged: it marks where the movement actually ended.
        level.sendParticles(MAGENTA, to.x, to.y + 1.0, to.z, 3, 0.15, 0.2, 0.15, 0.01);
        level.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.6, to.z, 2, 0.12, 0.15, 0.12, 0.01);
    }

    /**
     * Client prediction trail, local only.
     *
     * <p>Still dust rather than silhouettes. This fires on the moving player's own client for
     * responsiveness and is immediately followed by the server's real trail; drawing two full
     * figures at the same points would double the density of exactly the effect that is supposed
     * to be fading out.
     */
    @OnlyIn(Dist.CLIENT)
    public static void spawnTrailClient(Player player, Vec3 from, Vec3 to, int ghosts) {
        Level level = player.level();
        if (level == null) return;
        int steps = Math.min(6, Math.max(3, ghosts + 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t + player.getBbHeight() * 0.5;
            double z = from.z + (to.z - from.z) * t;
            level.addParticle((i & 1) == 0 ? MAGENTA : CYAN, x, y, z, 0, 0.005, 0);
        }
        level.addParticle(ParticleTypes.POOF, to.x, to.y + 0.6, to.z, 0, 0.01, 0);
    }
}
