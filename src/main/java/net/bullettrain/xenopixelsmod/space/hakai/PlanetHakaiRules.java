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
    public enum Verdict { ALLOWED, MODE_OFF, NOT_ERASABLE, PROTECTED, NO_PERMISSION, POLICY_PATH_DENIED, ALREADY_ERASED }

    /** The source of a planet-destruction request; generic projectile impacts are not sanctioned Hakai. */
    public enum DestructionPath { ORDINARY_IMPACT, SANCTIONED_HAKAI, WISH, ADMIN, SCRIPTED_EVENT }

    /** Binary planet-Hakai switch; free-mode structural damage is controlled by the destructibility policy instead. */
    public enum Destructibility {
        NONE, GATED, FREE, ADMIN_ONLY;

        public String key() { return name().toLowerCase(Locale.ROOT); }

        public static Destructibility parse(String value) {
            if (value == null) return GATED;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "none" -> NONE;
                case "free" -> FREE;
                case "admin_only" -> ADMIN_ONLY;
                case "gated" -> GATED;
                // Fail closed if an invalid value reaches runtime despite YAML validation.
                default -> NONE;
            };
        }
    }

    /** A policy refusal, or {@code null} when the existing global Hakai rules should decide. */
    public static Verdict sidecarVerdict(String policy, int casterPermission, DestructionPath path) {
        return switch (Destructibility.parse(policy)) {
            case NONE -> Verdict.PROTECTED;
            case ADMIN_ONLY -> path == DestructionPath.SCRIPTED_EVENT || casterPermission >= 2
                    ? null : Verdict.NO_PERMISSION;
            case GATED -> switch (path) {
                case SANCTIONED_HAKAI, WISH, ADMIN, SCRIPTED_EVENT -> null;
                case ORDINARY_IMPACT -> Verdict.POLICY_PATH_DENIED;
            };
            case FREE -> null;
        };
    }

    /**
     * Applies the server-wide destructibility ceiling to a per-body override. More restrictive values have lower
     * ceilings: none < admin_only < gated < free. A missing body override inherits the server setting.
     */
    public static Destructibility effectiveDestructibility(String globalCeiling, String bodyOverride) {
        Destructibility ceiling = Destructibility.parse(globalCeiling);
        if (bodyOverride == null || bodyOverride.isBlank()) return ceiling;
        Destructibility requested = Destructibility.parse(bodyOverride);
        return rank(requested) <= rank(ceiling) ? requested : ceiling;
    }

    /** Existing global story protection and in-progress Hakai burns also fence the free-mode integrity sink. */
    public static boolean acceptsFreeImpact(String effectivePolicy, String bodyId, Collection<String> protectedIds,
                                            boolean alreadyErased, boolean alreadyBurning) {
        return Destructibility.parse(effectivePolicy) == Destructibility.FREE
                && bodyId != null && (protectedIds == null || !protectedIds.contains(bodyId))
                && !alreadyErased && !alreadyBurning;
    }

    private static int rank(Destructibility value) {
        return switch (value) {
            case NONE -> 0;
            case ADMIN_ONLY -> 1;
            case GATED -> 2;
            case FREE -> 3;
        };
    }

    /** Canonical command spelling for registry IDs; Minecraft resource locations are lowercase. */
    public static String normalizeBodyId(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) return "";
        String namespaced = trimmed.contains(":") ? trimmed : "xenopixelsmod:" + trimmed;
        return namespaced.toLowerCase(Locale.ROOT);
    }

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
