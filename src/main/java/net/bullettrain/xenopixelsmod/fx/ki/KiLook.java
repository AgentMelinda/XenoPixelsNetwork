package net.bullettrain.xenopixelsmod.fx.ki;

import java.util.Locale;
import net.bullettrain.xenopixelsmod.fx.aura.AuraPalette;

/**
 * Which HD ki effect draws which kind of ki, in which colour and at what size. Pure.
 *
 * <p>The effects are {@code effeks/ki/ki_<part>_<hex>}, built by
 * {@code tools/effekseer/efkgen/effects/ki.py} for every colour of the aura palette. Combat V3's
 * shots and the client's redraw of DragonMineZ ki both ask here, so they look the same.
 */
public final class KiLook {
    /** Blocks between a beam's lengths at scale 1 (the effects are built 4.4 long, so they overlap). */
    public static final double SEGMENT = 4.0;
    /** A beam is refreshed this often, before its lengths would fade. */
    public static final int BODY_RESEND_TICKS = 6;
    public static final int MUZZLE_EVERY_TICKS = 3;
    public static final int GIANT_EVERY_TICKS = 2;
    public static final int HELD_EVERY_TICKS = 10;
    public static final int MAX_LENGTHS = 64;

    private KiLook() {}

    /** The kinds of ki, named as DragonMineZ's {@code KiAttackData.KiType}. */
    public enum Kind { SMALL_BALL, MEDIUM_BALL, GIANT_BALL, WAVE, LASER, BEAM, DISK, EXPLOSION, SHIELD, BARRAGE, AREA }

    public enum Part {
        CHARGE, BALL, GIANT, WAVE_BODY, WAVE_HEAD, WAVE_MUZZLE, LASER, SPIRAL, DISC, EXPLOSION, SHIELD, AREA, IMPACT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The effect that draws a kind while it flies, or stands. */
    public static Part flight(Kind kind) {
        return switch (kind) {
            case SMALL_BALL, MEDIUM_BALL, BARRAGE -> Part.BALL;
            case GIANT_BALL -> Part.GIANT;
            case WAVE -> Part.WAVE_BODY;
            case LASER, BEAM -> Part.LASER;
            case DISK -> Part.DISC;
            case EXPLOSION -> Part.EXPLOSION;
            case SHIELD -> Part.SHIELD;
            case AREA -> Part.AREA;
        };
    }

    /** Drawn as lengths laid end to end from the hands. */
    public static boolean beam(Kind kind) {
        return kind == Kind.WAVE || kind == Kind.LASER || kind == Kind.BEAM;
    }

    /** Where a shot lands: a giant ball ends in the full explosion, everything else in a burst. */
    public static Part landing(Kind kind) {
        return kind == Kind.GIANT_BALL ? Part.EXPLOSION : Part.IMPACT;
    }

    /** Effect name under {@code effeks/ki/}: {@code ki_wave_body_29b6f6}, the nearest colour built. */
    public static String asset(Part part, int rgb) {
        return "ki_" + part.id() + "_" + AuraPalette.hex(AuraPalette.nearest(rgb & 0xFFFFFF));
    }

    /** A second colour only counts when it is a different effect from the first. */
    public static boolean twoTone(int core, int edge) {
        return AuraPalette.nearest(core & 0xFFFFFF) != AuraPalette.nearest(edge & 0xFFFFFF);
    }

    /**
     * Effect scale for a DragonMineZ projectile of {@code size} (its own unit: a Kamehameha is 2,
     * a Death Beam 0.75, a Spirit Bomb 3). Chosen by reading DMZ's numbers, not measured in game.
     */
    public static float nativeScale(Kind kind, float size) {
        float s = Float.isFinite(size) && size > 0f ? size : 1f;
        return switch (kind) {
            case WAVE -> s * 0.5f;
            case LASER, BEAM -> s * 1.3f;
            case SMALL_BALL, MEDIUM_BALL, BARRAGE -> s;
            case GIANT_BALL -> s;
            case DISK -> s * 0.6f;
            case EXPLOSION -> s / 3f;
            case SHIELD, AREA -> s;
        };
    }

    /** How many beam lengths cover {@code reach} blocks. */
    public static int lengths(double reach, double spacing) {
        if (!(reach > 0) || !(spacing > 0)) return 0;
        return (int) Math.min(MAX_LENGTHS, Math.ceil(reach / spacing));
    }

    /**
     * The first length to draw this tick: all of them when the beam is due a refresh, otherwise
     * only the ones that have appeared since.
     *
     * @param age ticks since the beam started
     */
    public static int firstLength(long age, int alreadyDrawn, int lengths) {
        return age % BODY_RESEND_TICKS == 0 ? 0 : Math.min(Math.max(0, alreadyDrawn), lengths);
    }
}
