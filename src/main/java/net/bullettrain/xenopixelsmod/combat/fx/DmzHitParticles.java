package net.bullettrain.xenopixelsmod.combat.fx;

import com.dragonminez.common.init.MainParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * DMZ punch / spark / guard particles. Count {@code 0} plus RGB in the speed slots is
 * how DragonMineZ colours {@code PunchParticle} — vanilla crit/sweep is what made
 * Xeno hits look like a stock Minecraft fight.
 */
public final class DmzHitParticles {

    private DmzHitParticles() {
    }

    public static void punch(ServerLevel level, double x, double y, double z, float r, float g, float b) {
        spawn(level, punchType(), x, y, z, r, g, b);
    }

    public static void spark(ServerLevel level, double x, double y, double z, float r, float g, float b) {
        spawn(level, sparkType(), x, y, z, r, g, b);
    }

    public static void guard(ServerLevel level, double x, double y, double z, float r, float g, float b) {
        spawn(level, guardType(), x, y, z, r, g, b);
    }

    public static void punchClient(Level level, double x, double y, double z, float r, float g, float b) {
        if (level == null) return;
        ParticleOptions type = punchType();
        if (type != null) level.addParticle(type, x, y, z, r, g, b);
    }

    public static void sparkClient(Level level, double x, double y, double z, float r, float g, float b) {
        if (level == null) return;
        ParticleOptions type = sparkType();
        if (type != null) level.addParticle(type, x, y, z, r, g, b);
    }

    private static void spawn(ServerLevel level, ParticleOptions type, double x, double y, double z,
                              float r, float g, float b) {
        if (level == null || type == null) return;
        level.sendParticles(type, x, y, z, 0, r, g, b, 1.0);
    }

    private static ParticleOptions punchType() {
        try {
            return MainParticles.PUNCH_PARTICLE.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static ParticleOptions sparkType() {
        try {
            return MainParticles.SPARKS.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static ParticleOptions guardType() {
        try {
            return MainParticles.GUARD_BLOCK.get();
        } catch (Throwable t) {
            return punchType();
        }
    }
}
