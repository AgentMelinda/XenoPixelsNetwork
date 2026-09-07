package net.bullettrain.xenopixelsmod.combat.fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hakai VFX. The magenta splat is intentional — keep it.
 */
public final class HakaiFx {

    private static final Vector3f BODY = new Vector3f(0.95f, 0.20f, 0.95f);
    private static final Vector3f RIM = new Vector3f(1.0f, 0.45f, 1.0f);
    private static final Vector3f CORE = new Vector3f(0.75f, 0.15f, 0.90f);
    private static final DustParticleOptions ORB = new DustParticleOptions(CORE, 1.45f);
    private static final DustParticleOptions SPLAT = new DustParticleOptions(BODY, 1.55f);
    private static final DustParticleOptions SPARK = new DustParticleOptions(RIM, 1.15f);

    private HakaiFx() {
    }

    public static void tick(ServerLevel level, LivingEntity caster, LivingEntity target, float progress) {
        if (level == null || target == null) return;
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        target.setInvisible(false);
        splat(level, target, clamped);
        dissolve(level, target, clamped);
        if (caster != null) {
            casterAura(level, caster, clamped);
        }
    }

    public static void restore(ServerLevel level, LivingEntity target, float keepBelow) {
        if (level == null || target == null) return;
        target.setInvisible(false);
        dissolve(level, target, 1.0f - Math.max(0.0f, Math.min(1.0f, keepBelow)));
    }

    public static void reveal(LivingEntity target) {
        if (target == null) return;
        target.setInvisible(false);
    }

    public static void burst(ServerLevel level, LivingEntity target, boolean erase) {
        if (level == null || target == null) return;
        splat(level, target, erase ? 1.0f : 0.55f);
    }

    private static void splat(ServerLevel level, LivingEntity target, float progress) {
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        float height = Math.max(0.5f, target.getBbHeight());
        int n = 18 + Math.round(28 * progress);
        double spread = 0.45 + 0.55 * progress;
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2.0;
            double r = spread * (0.2 + Math.random());
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            double y = center.y + (Math.random() - 0.5) * height * 0.8;
            level.sendParticles(SPLAT, x, y, z, 2, 0.35, 0.35, 0.35, 0.08);
            if ((i & 1) == 0) {
                level.sendParticles(SPARK, x, y, z, 1, 0.25, 0.25, 0.25, 0.05);
                DmzHitParticles.spark(level, x, y, z, RIM.x, RIM.y, RIM.z);
            }
        }
        level.sendParticles(ORB, center.x, center.y, center.z, 8, 0.4, 0.35, 0.4, 0.06);
    }

    private static void dissolve(ServerLevel level, LivingEntity target, float progress) {
        Vec3 feet = target.position();
        float height = Math.max(0.5f, target.getBbHeight());
        float width = Math.max(0.3f, target.getBbWidth());
        float keepBelow = 1.0f - progress;
        SilhouetteFx.stamp(level, feet, target.yBodyRot, height, width,
                0.4 + 0.6 * progress, BODY, RIM, 1.2f + 0.3f * progress, keepBelow);
    }

    private static void casterAura(ServerLevel level, LivingEntity caster, float progress) {
        Vec3 hand = caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0)
                .add(caster.getLookAngle().scale(0.45));
        int n = 4 + Math.round(4 * progress);
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2.0;
            double r = 0.12 + Math.random() * 0.2;
            level.sendParticles(SPLAT,
                    hand.x + Math.cos(a) * r,
                    hand.y + (Math.random() - 0.5) * 0.2,
                    hand.z + Math.sin(a) * r,
                    1, 0.08, 0.08, 0.08, 0.02);
        }
    }
}
