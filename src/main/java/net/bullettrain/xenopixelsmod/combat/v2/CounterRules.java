package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * The super-counter window. Minecraft-free.
 *
 * <p>Being hit opens a short window; pressing vanish inside it turns the vanish into a counter.
 * The window opens on damage that actually landed, closes when used, and cannot be re-opened
 * faster than the lockout, or two fighters trade counters forever.
 */
public final class CounterRules {

    private CounterRules() {}

    /** Whether a hit of {@code damage} opens a window at all. */
    public static boolean opens(float damage, boolean enabled, int nowTick, int lockedUntilTick) {
        return enabled && damage > 0.05f && nowTick >= lockedUntilTick;
    }

    /** Whether a counter pressed at {@code nowTick} is inside the window. */
    public static boolean live(int nowTick, int openUntilTick) {
        return openUntilTick > 0 && nowTick <= openUntilTick;
    }
}
