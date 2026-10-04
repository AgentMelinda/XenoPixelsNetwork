package net.bullettrain.xenopixelsmod.npc;

/**
 * What each native role actually does, as opposed to what it is called.
 *
 * <p>Role has decided an NPC's entity type and its starting appearance since the six were added,
 * and {@code COMPANION} followed its owner - but {@code TRADER}, {@code GUARD} and {@code QUEST}
 * behaved exactly like {@code HUMANOID}. A quest giver would chase an attacker across the map and
 * never come back, which makes it a poor quest giver.
 *
 * <p>Kept as pure predicates rather than as behaviour of their own, so the brain and the targeting
 * both ask the same questions and cannot disagree about what a Guard is.
 */
public final class XenoNpcRoleBehaviour {

    /**
     * How far a guard will go from its post before giving up and returning.
     *
     * <p>Chosen against {@code NpcCombatRanges.OUT} (28): far enough to finish a fight it started,
     * short enough that it is still guarding something.
     */
    public static final double GUARD_LEASH = 24.0;

    /** How far a non-combatant strays from where it was placed. */
    public static final double PASSIVE_LEASH = 8.0;

    private XenoNpcRoleBehaviour() {
    }

    /**
     * Whether this role starts fights and chases.
     *
     * <p>Traders and quest givers do not. They exist to be talked to, and an NPC that wanders off
     * after a zombie is not where the player left it - which is the whole problem with treating
     * every role as a humanoid.
     */
    public static boolean fights(XenoNpcRole role) {
        return role != XenoNpcRole.TRADER && role != XenoNpcRole.QUEST;
    }

    /**
     * Whether this role defends the spot it was placed on.
     *
     * <p>A guard picks up hostiles near its home rather than only answering when struck, which is
     * the difference between a guard and a bystander.
     */
    public static boolean guardsHome(XenoNpcRole role) {
        return role == XenoNpcRole.GUARD;
    }

    /** Whether this role follows the player who placed it. */
    public static boolean followsOwner(XenoNpcRole role) {
        return role == XenoNpcRole.COMPANION;
    }

    /**
     * How far this role may get from its home before it walks back, or 0 for no limit.
     *
     * <p>A companion has no home leash on purpose: its anchor is its owner, and pulling it back to
     * a spawn point would fight the following.
     */
    public static double homeLeash(XenoNpcRole role) {
        if (followsOwner(role)) {
            return 0.0;
        }
        if (guardsHome(role)) {
            return GUARD_LEASH;
        }
        return fights(role) ? 0.0 : PASSIVE_LEASH;
    }

    /** One line for the editor, so the role cycler says what it will do. */
    public static String describe(XenoNpcRole role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case TRADER -> "Will not fight. Stays near where it was placed.";
            case QUEST -> "Will not fight. Link dialogue with QUEST answers; hand-ins use the quest's configured completer.";
            case GUARD -> "Attacks hostiles near its home and returns to it.";
            case COMPANION -> "Follows the player who placed it.";
            case CREATURE -> "Fights. No leash.";
            default -> "Fights. No leash.";
        };
    }
}
