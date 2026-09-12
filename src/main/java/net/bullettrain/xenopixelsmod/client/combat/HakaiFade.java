package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Client alpha for a body currently being erased by Hakai.
 *
 * <p>The number comes from {@code HakaiFadePacket}, not from the hidden
 * {@code hakai_dissolve} effect. Vanilla never syncs a non-player living entity's effects
 * to tracker clients; the packet is the same pattern Sparking already uses. Amplifier 0 is
 * solid; 255 is fully charged. The effect is only a fallback if a packet is ever missed.
 */
public final class HakaiFade {
    private static final ConcurrentHashMap<Integer, Integer> AMPLIFIERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Lerp> LERPS = new ConcurrentHashMap<>();

    private record Lerp(int from, int to, long tick) {}

    private HakaiFade() {}

    /** Server → client: amplifier {@code 0} clears the fade. */
    public static void set(int entityId, int amplifier) {
        set(entityId, amplifier, nowTick());
    }

    /** Test hook with an explicit game-time so lerp can be checked without a client. */
    static void set(int entityId, int amplifier, long gameTime) {
        if (amplifier <= 0) {
            AMPLIFIERS.remove(entityId);
            LERPS.remove(entityId);
            return;
        }
        int clamped = Math.max(1, Math.min(255, amplifier));
        Lerp previous = LERPS.get(entityId);
        int from = previous == null ? clamped : previous.to;
        LERPS.put(entityId, new Lerp(from, clamped, gameTime));
        AMPLIFIERS.put(entityId, clamped);
    }

    /** Dropped on disconnect so a rejoin cannot inherit a stale dissolve. */
    public static void clear() {
        AMPLIFIERS.clear();
        LERPS.clear();
    }

    /** Test / debug: packet amplifier for this id, or {@code null} when none. */
    static Integer amplifierOf(int entityId) {
        return AMPLIFIERS.get(entityId);
    }

    public static float alpha(Entity entity) {
        return alpha(entity, 1.0f);
    }

    public static float alpha(Entity entity, float partialTick) {
        if (!XenoServerConfig.hakaiFadeEnabled) return 1.0f;
        if (!(entity instanceof LivingEntity living)) return 1.0f;
        Float progress = progressOf(living, partialTick);
        if (progress == null) return 1.0f;
        return alphaFor(progress, XenoServerConfig.hakaiFadeMinAlpha,
                XenoServerConfig.hakaiFadeCurve);
    }

    /** Charge fraction 0..1 from the packet (preferred) or the fallback effect. */
    public static float progress(Entity entity) {
        return progress(entity, 1.0f);
    }

    public static float progress(Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity living)) return 0.0f;
        Float progress = progressOf(living, partialTick);
        return progress == null ? 0.0f : progress;
    }

    private static Float progressOf(LivingEntity living, float partialTick) {
        Lerp lerp = LERPS.get(living.getId());
        if (lerp != null) {
            return lerpedAmplifier(lerp, partialTick, nowTick()) / 255.0f;
        }
        Integer packet = AMPLIFIERS.get(living.getId());
        if (packet != null) return packet / 255.0f;
        MobEffectInstance effect = living.getEffect(ModEffects.HAKAI_DISSOLVE);
        if (effect == null) return null;
        return Math.max(0, Math.min(255, effect.getAmplifier())) / 255.0f;
    }

    static float lerpedAmplifier(int entityId, float partialTick, long gameTime) {
        Lerp lerp = LERPS.get(entityId);
        if (lerp == null) {
            Integer packet = AMPLIFIERS.get(entityId);
            return packet == null ? 0.0f : packet;
        }
        return lerpedAmplifier(lerp, partialTick, gameTime);
    }

    private static float lerpedAmplifier(Lerp lerp, float partialTick, long gameTime) {
        float t = (gameTime - lerp.tick) + Math.max(0.0f, Math.min(1.0f, partialTick));
        t = Math.max(0.0f, Math.min(1.0f, t));
        return lerp.from + (lerp.to - lerp.from) * t;
    }

    private static long nowTick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) return mc.level.getGameTime();
        } catch (RuntimeException ignored) {
        }
        return 0L;
    }

    /** The configured curve, read from the server config. */
    public static float alphaFromAmplifier(int amplifier) {
        int amp = Math.max(0, Math.min(255, amplifier));
        return alphaFor(amp / 255.0f, XenoServerConfig.hakaiFadeMinAlpha,
                XenoServerConfig.hakaiFadeCurve);
    }

    /**
     * {@code 1 - progress ^ curve}, floored at {@code minAlpha}.
     *
     * <p>Kept pure so the shape is testable without the config singleton. {@code curve 1.0} and
     * {@code minAlpha 0.02} reproduce the original linear ramp, including its floor being a true
     * {@code max}: applying the floor only at exactly-zero made the curve non-monotonic, because
     * amplifier 254 yielded {@code 1/255} — below the floor — and 255 then stepped back
     * <em>up</em> to {@code 0.02}.
     *
     * <p>The exponent is on the charge fraction, not on its complement: that is what makes
     * {@code curve > 1} hold the body opaque longer and fade it late, matching the config key's
     * own description. Exponentiating {@code (1 - progress)} instead would do the opposite and
     * make {@code curve > 1} fade <em>earlier</em>.
     */
    static float alphaFor(float progress, float minAlpha, float curve) {
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        float exponent = curve <= 0.0f ? 1.0f : curve;
        float faded = 1.0f - (float) Math.pow(p, exponent);
        float floor = Math.max(0.0f, Math.min(1.0f, minAlpha));
        return Math.max(floor, Math.min(1.0f, faded));
    }

    /**
     * How far the head-to-feet wipe has travelled, 0..1, after {@code speed} and {@code curve}.
     * 0 = body intact; 1 = wipe has reached the feet.
     */
    public static float charged(float progress, float speed, float curve) {
        float p = Math.max(0.0f, Math.min(1.0f, progress)) * (speed <= 0.0f ? 1.0f : speed);
        p = Math.max(0.0f, Math.min(1.0f, p));
        float exponent = curve <= 0.0f ? 1.0f : curve;
        return (float) Math.pow(p, exponent);
    }

    /** Height fraction that is still solid, measured up from the feet. */
    static float wipeLine(float progress, float speed, float curve) {
        return 1.0f - charged(progress, speed, curve);
    }

    /**
     * Alpha at a body-local height. {@code localY} is 0 at the feet and 1 at the head, so the
     * head crosses the wipe line first.
     *
     * <p>{@code charged == 0} stays solid at every height and {@code charged == 1} is the floor
     * everywhere, so the soft band does not nibble the head before the channel starts or leave
     * the feet visible at full charge.
     */
    static float alphaAtHeight(float progress, float localY, float minAlpha,
                               float curve, float speed, float band) {
        float amount = charged(progress, speed, curve);
        float floor = Math.max(0.0f, Math.min(1.0f, minAlpha));
        if (amount <= 0.0f) return 1.0f;
        if (amount >= 1.0f) return floor;
        float wipe = 1.0f - amount;
        float half = Math.max(0.02f, band) * 0.5f;
        float t = smoothstep(wipe - half, wipe + half, localY);
        return 1.0f + (floor - 1.0f) * t;
    }

    /**
     * Identity-camera convenience: {@code worldY = cameraSpaceY + cameraY}.
     * Pitched cameras must use {@link #worldYFromCameraSpace} then {@link #alphaAtWorldY}.
     */
    static float alphaAtCameraY(float progress, float cameraSpaceY, float cameraY, float feetY,
                                float bbHeight, float minAlpha, float curve, float speed,
                                float band, float uniformFallback) {
        return alphaAtWorldY(progress, cameraSpaceY + cameraY, feetY, bbHeight,
                minAlpha, curve, speed, band, uniformFallback);
    }

    /**
     * World-up Y → body-local height, then {@link #alphaAtHeight}. Values outside
     * {@code [-0.25, 1.25]} fall back to {@code uniformFallback}.
     */
    static float alphaAtWorldY(float progress, float worldY, float feetY, float bbHeight,
                               float minAlpha, float curve, float speed, float band,
                               float uniformFallback) {
        if (bbHeight <= 1.0e-4f || Float.isNaN(worldY)) return uniformFallback;
        float localY = (worldY - feetY) / bbHeight;
        if (localY < -0.25f || localY > 1.25f) return uniformFallback;
        return alphaAtHeight(progress, localY, minAlpha, curve, speed, band);
    }

    /**
     * Camera-space vertex → world Y using the camera pose.
     * {@code worldY = cameraPos.y + rotate(cameraSpace).y}.
     */
    static float worldYFromCameraSpace(float cx, float cy, float cz, float cameraY,
                                       float qx, float qy, float qz, float qw) {
        float n2 = qx * qx + qy * qy + qz * qz + qw * qw;
        if (n2 < 1.0e-8f || Float.isNaN(cameraY)) {
            return cy + (Float.isNaN(cameraY) ? 0.0f : cameraY);
        }
        Vector3f offset = new Vector3f(cx, cy, cz);
        new Quaternionf(qx, qy, qz, qw).transform(offset);
        return cameraY + offset.y;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1.0f : 0.0f;
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    public static boolean dissolving(Entity entity) {
        if (!XenoServerConfig.hakaiFadeEnabled || !(entity instanceof LivingEntity living)) {
            return false;
        }
        return targeted(living);
    }

    /** True while this entity is a Hakai victim, even if the body fade itself is off. */
    public static boolean targeted(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return AMPLIFIERS.containsKey(living.getId())
                || living.getEffect(ModEffects.HAKAI_DISSOLVE) != null;
    }
}
