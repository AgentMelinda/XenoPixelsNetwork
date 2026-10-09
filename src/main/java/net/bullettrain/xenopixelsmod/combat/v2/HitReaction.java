package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * What a landed hit does to the body it lands on.
 *
 * <p>Attacks choose a reaction by name instead of writing a velocity, so a launcher is a launcher
 * wherever it is thrown from and the numbers live in one table. Minecraft-free; the impulse is
 * applied through {@code CombatKnockback}, which is what keeps DragonMineZ masters planted.
 *
 * <p>{@code replace} decides whether the impulse overwrites the victim's velocity (launchers and
 * throws, which must not inherit whatever the victim was doing) or is added to it (contact hits).
 *
 * <p><b>Kicks are their own family.</b> The three {@code KICK_*} reactions are the v1 kick: a
 * ball-arc launch, the W launcher and the S slam. Their real trajectory comes from the same
 * {@code Bt3Landing} functions v1 uses, so the server's existing kick tuning
 * ({@code kickKnockbackScale}, {@code comboLauncherUp}, {@code kickDownLaunch}...) and the charge
 * of the kick shape them here too. The numbers below are only what they fall back to where that
 * tuning is not available, which keeps this table testable on its own.
 */
public enum HitReaction {
    /** Contact only: the string stays in reach. */
    HIT_LIGHT(0.0, 0.0, false, 4, Kick.NONE),
    HIT_HEAVY(0.35, 0.18, false, 8, Kick.NONE),
    LAUNCH_UP(0.35, 1.45, true, 14, Kick.NONE),
    LAUNCH_FORWARD(1.35, 0.95, true, 12, Kick.NONE),
    LAUNCH_DOWN(0.3, -0.9, true, 10, Kick.NONE),
    KNOCKBACK_SHORT(0.9, 0.3, true, 8, Kick.NONE),
    KNOCKBACK_LONG(1.65, 0.55, true, 14, Kick.NONE),
    KNOCKDOWN(0.4, -0.5, true, 18, Kick.NONE),
    THROW_FORWARD(1.9, 0.6, true, 16, Kick.NONE),
    THROW_BACK(-1.9, 0.6, true, 16, Kick.NONE),
    THROW_UP(0.2, 1.6, true, 18, Kick.NONE),
    THROW_DOWN(0.3, -1.1, true, 16, Kick.NONE),
    /** A kick that sends the target away in a high ball arc, to be chased. */
    KICK_ARC(0.85, 1.7, true, 14, Kick.ARC),
    /** A rising kick: the launcher. */
    KICK_UP(0.55, 1.85, true, 14, Kick.UP),
    /** A falling kick: slams the target down. */
    KICK_DOWN(0.6, -1.15, true, 12, Kick.DOWN);

    /** Which v1 kick trajectory a reaction is, if any. */
    public enum Kick {
        NONE(0),
        ARC(0),
        UP(1),
        DOWN(-1);

        private final int verticalBias;

        Kick(int verticalBias) {
            this.verticalBias = verticalBias;
        }

        /** The legacy kick's vertical bias: +1 up, -1 down, 0 the neutral arc. */
        public int verticalBias() {
            return verticalBias;
        }
    }

    private final double away;
    private final double vertical;
    private final boolean replace;
    private final int stunTicks;
    private final Kick kick;

    HitReaction(double away, double vertical, boolean replace, int stunTicks, Kick kick) {
        this.away = away;
        this.vertical = vertical;
        this.replace = replace;
        this.stunTicks = stunTicks;
        this.kick = kick;
    }

    /** True when the impulse overwrites the victim's velocity instead of adding to it. */
    public boolean replacesVelocity() {
        return replace;
    }

    /** Ticks the victim cannot start an attack of their own. */
    public int stunTicks() {
        return stunTicks;
    }

    /** The kick trajectory this reaction uses, or {@link Kick#NONE}. */
    public Kick kick() {
        return kick;
    }

    public boolean isKick() {
        return kick != Kick.NONE;
    }

    /** True when this sends the target far enough that a chase can follow. */
    public boolean opensChase() {
        return this == LAUNCH_UP || this == LAUNCH_FORWARD || this == LAUNCH_DOWN
                || this == KNOCKBACK_LONG || this == THROW_FORWARD || this == THROW_BACK
                || this == THROW_UP || this == KICK_ARC || this == KICK_UP;
    }

    /** True when the impulse has any horizontal or vertical component at all. */
    public boolean moves() {
        return away != 0.0 || vertical != 0.0;
    }

    /**
     * The impulse as {@code [x, y, z]}.
     *
     * @param awayX attacker-to-victim X; need not be normalised
     * @param awayZ attacker-to-victim Z; a zero-length pair pushes along +Z so a stacked victim
     *              still gets a vector
     * @param scale server tuning multiplier on the horizontal part
     */
    public double[] impulse(double awayX, double awayZ, double scale) {
        double len = Math.hypot(awayX, awayZ);
        double nx = len < 1.0e-8 ? 0.0 : awayX / len;
        double nz = len < 1.0e-8 ? 1.0 : awayZ / len;
        double h = away * Math.max(0.0, scale);
        return new double[]{nx * h, vertical, nz * h};
    }

    /**
     * What a fully charged attack does instead: one tier harder. Kicks and launchers are already
     * as far as they go, and their charge shows in the trajectory instead.
     */
    public HitReaction charged() {
        return switch (this) {
            case HIT_LIGHT -> HIT_HEAVY;
            case HIT_HEAVY -> KNOCKBACK_SHORT;
            case KNOCKBACK_SHORT -> KNOCKBACK_LONG;
            default -> this;
        };
    }

    /** The throw for a held direction: forward by default, the side keys count as forward. */
    public static HitReaction throwFor(V2Direction direction) {
        if (direction == null) return THROW_FORWARD;
        return switch (direction) {
            case BACK -> THROW_BACK;
            case UP -> THROW_UP;
            case DOWN -> THROW_DOWN;
            default -> THROW_FORWARD;
        };
    }
}
