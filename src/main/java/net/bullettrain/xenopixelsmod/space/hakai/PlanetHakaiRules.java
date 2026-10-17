package net.bullettrain.xenopixelsmod.space.hakai;

import java.util.Collection;
import java.util.Locale;

/**
 * Pure rules for planet Hakai (design-doc 16b / 22). No Minecraft types, so every decision is unit tested.
 */
public final class PlanetHakaiRules {
    private PlanetHakaiRules() {}

    /** Server policy, {@code XenoSpaceConfig.hakaiPlanets}. */
    public enum Mode {
        OFF, ERASABLE_ONLY, ALL;

        public String key() { return name().toLowerCase(Locale.ROOT); }

        public static Mode parse(String value) {
            if (value == null) return OFF;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "erasable_only", "erasable" -> ERASABLE_ONLY;
                case "all", "on", "true" -> ALL;
                default -> OFF;
            };
        }
    }

    /** Why a body may or may not be erased. */
    public enum Verdict { ALLOWED, MODE_OFF, NOT_ERASABLE, PROTECTED, NO_PERMISSION, ALREADY_ERASED }

    public static Verdict verdict(Mode mode, String bodyId, boolean bodyErasableFlag, Collection<String> protectedIds,
                                  int casterPermission, int requiredPermission, boolean alreadyErased) {
        if (mode == Mode.OFF) return Verdict.MODE_OFF;
        if (alreadyErased) return Verdict.ALREADY_ERASED;
        if (protectedIds.contains(bodyId)) return Verdict.PROTECTED;
        if (mode == Mode.ERASABLE_ONLY && !bodyErasableFlag) return Verdict.NOT_ERASABLE;
        if (casterPermission < requiredPermission) return Verdict.NO_PERMISSION;
        return Verdict.ALLOWED;
    }

    /**
     * Distance along the ray (origin o, unit direction d) to the first hit of the sphere (centre c, radius r), or
     * {@code -1} when the ray misses it, starts past it, or the hit is beyond {@code reach}. An origin inside the sphere
     * counts as a hit at distance 0.
     */
    public static double raySphere(double ox, double oy, double oz, double dx, double dy, double dz,
                                   double cx, double cy, double cz, double r, double reach) {
        if (!(r > 0) || !Double.isFinite(ox + oy + oz + dx + dy + dz + cx + cy + cz)) return -1;
        double lx = cx - ox, ly = cy - oy, lz = cz - oz;
        double c2 = lx * lx + ly * ly + lz * lz;
        if (c2 <= r * r) return 0;
        double along = lx * dx + ly * dy + lz * dz;
        if (along < 0) return -1;
        double perp2 = c2 - along * along;
        if (perp2 > r * r) return -1;
        double hit = along - Math.sqrt(r * r - perp2);
        return hit <= reach ? hit : -1;
    }

    /** Fraction of the burn done, 0..1, for the erase FX. */
    public static double progress(long startTick, long now, int eraseTicks) {
        if (eraseTicks <= 0) return 1;
        return Math.max(0, Math.min(1, (now - startTick) / (double) eraseTicks));
    }

    /** True when an erased body with this timestamp should come back on its own. */
    public static boolean dueForRestore(long erasedAtMillis, long nowMillis, int restoreMinutes) {
        return restoreMinutes > 0 && nowMillis - erasedAtMillis >= restoreMinutes * 60_000L;
    }
}
