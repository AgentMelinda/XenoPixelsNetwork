package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.fx.effek.EffectSlot;
import net.bullettrain.xenopixelsmod.fx.effek.XenoEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hakai VFX. Body / rim colours come from {@code hakaiFxColor} / {@code hakaiFxRimColor};
 * the silhouette wipe uses the same charged sweep as the body fade.
 */
public final class HakaiFx {

    private HakaiFx() {
    }

    public static void tick(ServerLevel level, LivingEntity caster, LivingEntity target, float progress) {
        if (level == null || target == null) return;
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        target.setInvisible(false);
        // Dragon Ball Super's Hakai: a purple veil clings to the victim (re-placed at the feet
        // every pulse, positional so effekseerRange applies), the body vanishes from the head
        // down (the client wipe) and violet flakes break off at that line. The vanilla dust,
        // particle silhouette and caster aura are only the fallback when effects do not play.
        boolean effek = false;
        long now = level.getGameTime();
        float height = Math.max(0.5f, target.getBbHeight());
        if (PULSES.due(target.getId(), now)) {
            effek = XenoEffects.play(level, EffectSlot.HAKAI_CHANNEL, target.position(), UP,
                    HakaiEffectRules.bodyScale(height), -1);
            if (effek) PULSES.showUntil(target.getId(), now + 12);
            if (effek && caster != null) {
                XenoEffects.play(level, EffectSlot.HAKAI_PALM,
                        HakaiEffectRules.palm(caster.position(), caster.getBbHeight(), caster.getLookAngle()),
                        UP, HakaiEffectRules.bodyScale(caster.getBbHeight()), -1);
            }
        }
        boolean effekShowing = effek || PULSES.showing(target.getId(), now);
        float speed = XenoServerConfig.hakaiFadeSpeed;
        float curve = XenoServerConfig.hakaiFadeCurve;
        if (effekShowing && HakaiEffectRules.crumbling(clamped, speed, curve)
                && CRUMBLES.due(target.getId(), now)) {
            double lineY = target.getY() + height * HakaiEffectRules.crumbleLine(clamped, speed, curve);
            XenoEffects.play(level, EffectSlot.HAKAI_CRUMBLE,
                    new Vec3(target.getX(), lineY, target.getZ()), UP,
                    HakaiEffectRules.widthScale(target.getBbWidth()), -1);
        }
        if (effekShowing) return;
        if (dustEnabled()) splat(level, target, clamped);
        if (silhouetteEnabled()) dissolve(level, target, clamped);
        if (dustEnabled() && caster != null) {
            casterAura(level, caster, clamped);
        }
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** Crumble bursts at the dissolve line, more often than the veil. */
    private static final PulseClock CRUMBLES = new PulseClock(HakaiEffectRules.CRUMBLE_INTERVAL);

    /** Per-target swirl timing (server thread only). */
    private static final PulseClock PULSES = new PulseClock();

    /**
     * When the channel swirl is (re)sent: on a target's first channel tick, then every 10 ticks,
     * whatever the parity of the calls (tick() runs every 2nd channel tick). The old
     * (gameTime + id) % 10 rule never matched for half the casts, so the swirl never showed.
     */
    public static final class PulseClock {
        static final int INTERVAL = 10;
        private final int interval;
        private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap last = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
        private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap until = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

        public PulseClock() {
            this(INTERVAL);
        }

        public PulseClock(int interval) {
            this.interval = Math.max(1, interval);
        }

        public boolean due(int targetId, long now) {
            if (last.containsKey(targetId) && now - last.get(targetId) < interval) return false;
            last.put(targetId, now);
            return true;
        }

        public void showUntil(int targetId, long tick) {
            until.put(targetId, tick);
        }

        public boolean showing(int targetId, long now) {
            return until.containsKey(targetId) && now < until.get(targetId);
        }

        /** A finished or cancelled channel: the next one on this target starts its swirl at once. */
        public void forget(int targetId) {
            last.remove(targetId);
            until.remove(targetId);
        }
    }

    public static void restore(ServerLevel level, LivingEntity target, float keepBelow) {
        if (level == null || target == null) return;
        PULSES.forget(target.getId());
        CRUMBLES.forget(target.getId());
        target.setInvisible(false);
        if (silhouetteEnabled()) {
            dissolve(level, target, 1.0f - Math.max(0.0f, Math.min(1.0f, keepBelow)));
        }
    }

    public static void reveal(LivingEntity target) {
        if (target == null) return;
        target.setInvisible(false);
    }

    public static void burst(ServerLevel level, LivingEntity target, boolean erase) {
        if (level == null || target == null) return;
        PULSES.forget(target.getId());
        CRUMBLES.forget(target.getId());
        Vec3 centre = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        float size = HakaiEffectRules.bodyScale(target.getBbHeight()) * (erase ? 1.0f : 0.7f);
        if (XenoEffects.play(level, EffectSlot.HAKAI_ERASE, centre, UP, size, target.getId())) {
            return;
        }
        if (!dustEnabled()) return;
        splat(level, target, erase ? 1.0f : 0.55f);
    }

    /**
     * A block erased by area Hakai: a small violet puff where it was. {@code fading} is the
     * crack-stage puff while it fades; false is the last one as it vanishes.
     */
    public static void blockPuff(ServerLevel level, Vec3 centre, boolean fading) {
        if (level == null || centre == null || !dustEnabled()) return;
        DustParticleOptions dust = new DustParticleOptions(fading ? rimColor() : bodyColor(),
                fading ? 0.9f : 1.35f);
        level.sendParticles(dust, centre.x, centre.y, centre.z, fading ? 1 : 4, 0.3, 0.3, 0.3, 0.02);
    }

    static boolean dustEnabled() {
        return XenoServerConfig.hakaiFxEnabled && XenoServerConfig.hakaiDustEnabled;
    }

    static boolean silhouetteEnabled() {
        return XenoServerConfig.hakaiFxEnabled && XenoServerConfig.hakaiSilhouetteEnabled;
    }

    private static void splat(ServerLevel level, LivingEntity target, float progress) {
        Vector3f body = bodyColor();
        Vector3f rim = rimColor();
        DustParticleOptions splat = new DustParticleOptions(body, 1.55f);
        DustParticleOptions spark = new DustParticleOptions(rim, 1.15f);
        DustParticleOptions orb = new DustParticleOptions(coreColor(body), 1.45f);
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
            level.sendParticles(splat, x, y, z, 2, 0.35, 0.35, 0.35, 0.08);
            if ((i & 1) == 0) {
                level.sendParticles(spark, x, y, z, 1, 0.25, 0.25, 0.25, 0.05);
                DmzHitParticles.spark(level, x, y, z, rim.x, rim.y, rim.z);
            }
        }
        level.sendParticles(orb, center.x, center.y, center.z, 8, 0.4, 0.35, 0.4, 0.06);
    }

    private static void dissolve(ServerLevel level, LivingEntity target, float progress) {
        Vec3 feet = target.position();
        float height = Math.max(0.5f, target.getBbHeight());
        float width = Math.max(0.3f, target.getBbWidth());
        float sweep = HakaiFade.charged(progress, XenoServerConfig.hakaiFadeSpeed,
                XenoServerConfig.hakaiFadeCurve);
        float keepBelow = 1.0f - sweep;
        Vector3f body = silhouetteColor();
        Vector3f rim = rimColor();
        SilhouetteFx.stamp(level, feet, target.yBodyRot, height, width,
                0.4 + 0.6 * sweep, body, rim, 1.2f + 0.3f * sweep, keepBelow);
    }

    private static void casterAura(ServerLevel level, LivingEntity caster, float progress) {
        Vector3f body = bodyColor();
        DustParticleOptions splat = new DustParticleOptions(body, 1.55f);
        Vec3 hand = caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0)
                .add(caster.getLookAngle().scale(0.45));
        int n = 4 + Math.round(4 * progress);
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2.0;
            double r = 0.12 + Math.random() * 0.2;
            level.sendParticles(splat,
                    hand.x + Math.cos(a) * r,
                    hand.y + (Math.random() - 0.5) * 0.2,
                    hand.z + Math.sin(a) * r,
                    1, 0.08, 0.08, 0.08, 0.02);
        }
    }

    static Vector3f rgb(int packed) {
        int c = packed & 0xFFFFFF;
        return new Vector3f(((c >> 16) & 0xFF) / 255.0f, ((c >> 8) & 0xFF) / 255.0f, (c & 0xFF) / 255.0f);
    }

    static Vector3f coreColor(Vector3f body) {
        return new Vector3f(body.x * 0.75f, body.y * 0.75f, body.z * 0.75f);
    }

    private static Vector3f bodyColor() {
        return rgb(XenoServerConfig.hakaiFxColor);
    }

    private static Vector3f silhouetteColor() {
        return rgb(XenoServerConfig.hakaiSilhouetteColor);
    }

    private static Vector3f rimColor() {
        return rgb(XenoServerConfig.hakaiFxRimColor);
    }
}
