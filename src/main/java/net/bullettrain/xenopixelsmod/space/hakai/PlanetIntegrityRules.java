package net.bullettrain.xenopixelsmod.space.hakai;

/** Pure integrity state math for §22. Tuning is supplied by the caller; this class has no gameplay defaults. */
public final class PlanetIntegrityRules {
    public enum Stage { INTACT, SCARRED, CRITICAL, DESTROYED }

    /** Optional thresholds are percentages; a null stage is disabled. Thresholds are inclusive. */
    public record Thresholds(Double scarredAt, Double criticalAt) {
        public Thresholds {
            if (scarredAt == null && criticalAt == null)
                throw new IllegalArgumentException("at least one visible integrity stage must be configured");
            if (scarredAt != null && (!Double.isFinite(scarredAt) || scarredAt <= 0.0 || scarredAt >= 100.0))
                throw new IllegalArgumentException("scarred threshold must be finite and strictly between 0 and 100");
            if (criticalAt != null && (!Double.isFinite(criticalAt) || criticalAt <= 0.0 || criticalAt >= 100.0))
                throw new IllegalArgumentException("critical threshold must be finite and strictly between 0 and 100");
            if (scarredAt != null && criticalAt != null && criticalAt > scarredAt)
                throw new IllegalArgumentException("critical threshold cannot be greater than scarred threshold");
        }
    }

    private PlanetIntegrityRules() {}

    public static Stage stage(double integrity, Thresholds thresholds) {
        requireIntegrity(integrity);
        if (isDestroyed(integrity)) return Stage.DESTROYED;
        if (thresholds.criticalAt() != null && integrity <= thresholds.criticalAt()) return Stage.CRITICAL;
        if (thresholds.scarredAt() != null && integrity <= thresholds.scarredAt()) return Stage.SCARRED;
        return Stage.INTACT;
    }

    /** The crack transition is dispatched only at exactly zero; this predicate is shared by all state paths. */
    public static boolean isDestroyed(double integrity) {
        requireIntegrity(integrity);
        return integrity == 0.0;
    }

    /** Applies an already-resolved damage amount. Impact-to-damage tuning belongs to the configured caller. */
    public static double damage(double integrity, double loss) {
        requireIntegrity(integrity);
        if (!Double.isFinite(loss) || loss < 0.0)
            throw new IllegalArgumentException("integrity loss must be finite and non-negative");
        return Math.max(0.0, integrity - loss);
    }

    /**
     * Resolves one accepted free-mode impact. Damage declines with remaining integrity but has a
     * configured positive floor so an impacted body can still reach the exact crack threshold.
     */
    public static double diminishingImpactLoss(double integrity, double nominalLoss, double floorFraction) {
        requireIntegrity(integrity);
        if (!Double.isFinite(nominalLoss) || nominalLoss < 0.0)
            throw new IllegalArgumentException("nominal impact loss must be finite and non-negative");
        if (!Double.isFinite(floorFraction) || floorFraction <= 0.0 || floorFraction > 1.0)
            throw new IllegalArgumentException("impact loss floor must be in (0, 1]");
        if (nominalLoss == 0.0) return 0.0;
        return nominalLoss * Math.max(floorFraction, integrity / 100.0);
    }

    /** Regeneration is measured in integrity points per tick; zero disables it. Destroyed bodies need explicit restore. */
    public static double regenerate(double integrity, double pointsPerTick, long elapsedTicks) {
        requireIntegrity(integrity);
        if (!Double.isFinite(pointsPerTick) || pointsPerTick < 0.0 || elapsedTicks < 0)
            throw new IllegalArgumentException("regeneration and elapsed ticks must be non-negative and finite");
        if (isDestroyed(integrity) || pointsPerTick == 0.0 || elapsedTicks == 0) return integrity;
        return Math.min(100.0, integrity + pointsPerTick * elapsedTicks);
    }

    private static void requireIntegrity(double integrity) {
        if (!Double.isFinite(integrity) || integrity < 0.0 || integrity > 100.0)
            throw new IllegalArgumentException("integrity must be finite and in [0, 100]");
    }
}
