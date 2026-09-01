package net.bullettrain.xenopixelsmod.client.combat.fx;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.network.packet.CombatFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client state for combat impact feedback: camera shake and the screen flash.
 *
 * <p>Minecraft cannot freeze time, so the hitstop these fighting games use is not available. The
 * substitute is a short, sharply-decaying camera kick plus a one-frame-bright flash, which
 * carries the same "that connected" information in the same few hundred milliseconds without
 * touching the simulation. Nothing here is gameplay — a player with every effect disabled fights
 * exactly the same fight.
 *
 * <p><b>Shake is opt-out.</b> Camera motion the player did not ask for is a genuine accessibility
 * problem (motion sickness, vestibular disorders), so {@link XenoClientConfig#bt3ScreenShake}
 * turns it off outright and {@link XenoClientConfig#bt3ScreenShakeStrength} scales it, with the
 * flash on its own separate switch. Disabling shake must never disable the information: the
 * flash and the world particles still say a hit landed.
 *
 * <p>State is a single decaying envelope rather than a queue of effects. Two hits landing in the
 * same tick should not stack into double the shake — the stronger one wins and refreshes the
 * envelope, which is what keeps a rush chain from turning the camera into a blur.
 */
@OnlyIn(Dist.CLIENT)
public final class CombatFxClient {

    /** Past this the cue is ignored outright; matches the server's broadcast radius. */
    private static final double MAX_DISTANCE = 48.0;
    /** Inside this, an impact is at full strength; beyond it, it falls off to nothing. */
    private static final double FULL_STRENGTH_DISTANCE = 6.0;

    /** Peak yaw/pitch swing in degrees at intensity 1. Kept small — this is a kick, not a quake. */
    private static final float SHAKE_DEGREES = 1.9f;
    /** Peak roll in degrees at intensity 1. Roll sells impact harder than yaw, so it leads. */
    private static final float SHAKE_ROLL_DEGREES = 2.6f;
    /** Shake envelope decay per tick. ~0.55 puts a heavy hit at a fifth of peak after 3 ticks. */
    private static final float SHAKE_DECAY = 0.55f;
    /** Oscillations per tick. Fast enough to read as an impact rather than a sway. */
    private static final float SHAKE_FREQUENCY = 2.7f;

    /** Flash envelope decay per tick. Faster than the shake: a flash that lingers reads as fog. */
    private static final float FLASH_DECAY = 0.42f;
    /** Ceiling on flash opacity, so even an ultimate never fully blanks the screen. */
    private static final float FLASH_MAX_ALPHA = 0.42f;

    /** Current shake envelope, 0..~2. Decays every client tick. */
    private static float shake;
    /** Ticks since the shake started, driving the oscillation phase. */
    private static float shakeAge;
    /** Direction the triggering blow travelled, in world space; biases the kick. */
    private static Vec3 shakeDir = new Vec3(0.0, 1.0, 0.0);

    private static float flash;
    private static int flashColor = 0xFFFFFF;

    private CombatFxClient() {
    }

    /** Bound to {@code ClientScreens.receiveCombatFx} at client setup. */
    public static void accept(CombatFxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (!XenoClientConfig.bt3CombatClient) return;

        double distance = minecraft.player.position().distanceTo(packet.pos());
        if (distance > MAX_DISTANCE) return;

        float falloff = falloff(distance);
        if (falloff <= 0.0f) return;
        float strength = packet.intensity() * falloff;

        Profile profile = Profile.of(packet.kind());
        addShake(strength * profile.shake, packet.dir());
        addFlash(strength * profile.flash, profile.color);
    }

    /**
     * Inverse-square-ish falloff between the full-strength radius and the cutoff.
     *
     * <p>Linear falloff makes distant fights feel as physical as one happening on top of you,
     * which is exactly the thing that turns impact feedback into noise on a busy server.
     */
    private static float falloff(double distance) {
        if (distance <= FULL_STRENGTH_DISTANCE) return 1.0f;
        double t = (distance - FULL_STRENGTH_DISTANCE) / (MAX_DISTANCE - FULL_STRENGTH_DISTANCE);
        double remaining = 1.0 - Math.min(1.0, t);
        return (float) (remaining * remaining);
    }

    /** Stronger cue wins rather than summing; see the class note on stacking. */
    private static void addShake(float amount, Vec3 dir) {
        if (amount <= 0.0f) return;
        if (amount <= shake) return;
        shake = Math.min(2.5f, amount);
        shakeAge = 0.0f;
        shakeDir = dir;
    }

    private static void addFlash(float amount, int color) {
        if (amount <= 0.0f) return;
        if (amount <= flash) return;
        flash = Math.min(1.5f, amount);
        flashColor = color;
    }

    /** Advance both envelopes. Called once per client tick. */
    public static void tick() {
        if (shake > 0.0f) {
            shakeAge += 1.0f;
            shake *= SHAKE_DECAY;
            if (shake < 0.004f) shake = 0.0f;
        }
        if (flash > 0.0f) {
            flash *= FLASH_DECAY;
            if (flash < 0.004f) flash = 0.0f;
        }
    }

    /** Drop everything — used on disconnect and dimension change so nothing bleeds across. */
    public static void reset() {
        shake = 0.0f;
        flash = 0.0f;
        shakeAge = 0.0f;
    }

    public static boolean shakeActive() {
        return shake > 0.0f && XenoClientConfig.bt3ScreenShake;
    }

    /**
     * Camera offsets for this frame as {yaw, pitch, roll} degrees.
     *
     * @param partialTick sub-tick progress, so the oscillation is smooth at any frame rate
     *                    instead of stepping 20 times a second
     */
    public static float[] cameraOffset(float partialTick) {
        if (!shakeActive()) return NO_OFFSET;
        float scale = Math.max(0.0f, XenoClientConfig.bt3ScreenShakeStrength);
        if (scale <= 0.0f) return NO_OFFSET;

        float phase = (shakeAge + partialTick) * SHAKE_FREQUENCY;
        // Interpolate the envelope across the tick too, or the shake visibly steps down.
        float envelope = shake * (float) Math.pow(SHAKE_DECAY, partialTick) * scale;

        // Three different frequencies so the axes never line up into a clean circular sway.
        float yaw = (float) Math.sin(phase) * SHAKE_DEGREES * envelope;
        float pitch = (float) Math.sin(phase * 1.37f + 1.1f) * SHAKE_DEGREES * envelope;
        float roll = (float) Math.sin(phase * 0.83f + 2.3f) * SHAKE_ROLL_DEGREES * envelope;

        // Bias the kick along the blow: a hit from the side should throw the view sideways.
        yaw += (float) shakeDir.x * SHAKE_DEGREES * envelope * 0.35f;
        pitch += (float) shakeDir.y * SHAKE_DEGREES * envelope * 0.35f;

        return new float[]{yaw, pitch, roll};
    }

    private static final float[] NO_OFFSET = {0.0f, 0.0f, 0.0f};

    /** Flash opacity for this frame, 0 when off or disabled. */
    public static float flashAlpha(float partialTick) {
        if (flash <= 0.0f || !XenoClientConfig.bt3ImpactFlash) return 0.0f;
        float envelope = flash * (float) Math.pow(FLASH_DECAY, partialTick);
        return Math.min(FLASH_MAX_ALPHA, envelope * FLASH_MAX_ALPHA);
    }

    public static int flashColor() {
        return flashColor;
    }

    /**
     * How each impact kind translates into shake, flash and colour.
     *
     * <p>The colours are the point as much as the magnitudes: white for damage taken or dealt,
     * gold reserved for an ultimate, cyan for anything the guard absorbed. A player learns those
     * three in about a minute and then reads the fight peripherally.
     */
    private record Profile(float shake, float flash, int color) {
        static Profile of(CombatFxKind kind) {
            return switch (kind) {
                case IMPACT_LIGHT -> new Profile(0.45f, 0.0f, 0xFFFFFF);
                case IMPACT_HEAVY -> new Profile(1.0f, 0.55f, 0xFFF3D6);
                case IMPACT_ULTIMATE -> new Profile(1.7f, 1.0f, 0xFFD98A);
                case GUARD_BLOCK -> new Profile(0.6f, 0.4f, 0x8FD8FF);
                case COUNTER_FLASH -> new Profile(0.9f, 0.9f, 0xB8F1FF);
                case VANISH_CLAP -> new Profile(0.5f, 0.35f, 0xE8E8FF);
                case DASH_LAUNCH -> new Profile(0.35f, 0.0f, 0xFFFFFF);
                // A wash plus a hard kick at 200% is what made overcharge look foggy.
                // Keep a small gold edge so the tier is readable; leave the centre clear.
                case CHARGE_PEAK -> new Profile(0.28f, 0.08f, 0xFFE08A);
                // Slow violet flash, minimal shake -- erasure reads as final, not as a hit.
                case HAKAI_ERASE -> new Profile(0.5f, 0.85f, 0xB266FF);
            };
        }
    }
}
