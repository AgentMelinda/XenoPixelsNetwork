package net.bullettrain.xenopixelsmod.compat.npc;

/**
 * When an NPC may ki-deflect relative to its current victim.
 *
 * <p>{@code minRange <= 0} is the old behaviour: deflect at any distance. Otherwise a living
 * victim closer than {@code minRange} suppresses deflect (clash-wave answer is a different path).
 */
public final class NpcKiDeflectPolicy {
    private NpcKiDeflectPolicy() {}

    public static boolean allow(double victimDistance, double minRange) {
        if (!Double.isFinite(minRange) || minRange <= 0.0) {
            return true;
        }
        if (!Double.isFinite(victimDistance)) {
            return true;
        }
        return victimDistance >= minRange;
    }
}
