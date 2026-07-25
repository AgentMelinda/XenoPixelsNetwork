package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * Lightweight afterimage trails (few dust puffs only — no FLASH / cloud spam).
 */
public final class AfterimageFx {
    private static final DustParticleOptions MAGENTA =
            new DustParticleOptions(new Vector3f(0.85f, 0.35f, 0.95f), 1.0f);
    private static final DustParticleOptions CYAN =
            new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 0.9f);

    private AfterimageFx() {}

    /** Server: sparse trail from → to. */
    public static void spawnTrailServer(ServerPlayer player, Vec3 from, Vec3 to, int ghosts) {
        if (!(player.level() instanceof ServerLevel level)) return;
        int steps = Math.min(6, Math.max(3, ghosts + 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t + player.getBbHeight() * 0.5;
            double z = from.z + (to.z - from.z) * t;
            DustParticleOptions dust = (i & 1) == 0 ? MAGENTA : CYAN;
            level.sendParticles(dust, x, y, z, 1, 0.08, 0.15, 0.08, 0.0);
        }
        // Single land puff (no FLASH — that particle is expensive)
        level.sendParticles(MAGENTA, to.x, to.y + 1.0, to.z, 3, 0.15, 0.2, 0.15, 0.01);
        level.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.6, to.z, 2, 0.12, 0.15, 0.12, 0.01);
    }

    /** Client prediction particles (local only). */
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
            DustParticleOptions dust = (i & 1) == 0 ? MAGENTA : CYAN;
            level.addParticle(dust, x, y, z, 0, 0.005, 0);
        }
        level.addParticle(ParticleTypes.POOF, to.x, to.y + 0.6, to.z, 0, 0.01, 0);
    }
}
