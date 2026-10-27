package net.bullettrain.xenopixelsmod.space.hakai;

/** Allocates the bounded Atlas map slots so erased entries cannot crowd every live damaged body out. */
public final class PlanetIntegrityAtlasMarkerBudget {
    public record Limits(int erased, int damaged) {}

    private PlanetIntegrityAtlasMarkerBudget() {}

    public static Limits allocate(int erasedCount, int damagedCount) {
        if (erasedCount < 0 || damagedCount < 0) throw new IllegalArgumentException("marker counts must be non-negative");
        int maximum = PlanetIntegrityAtlasSnapshot.MAX_ENTRIES;
        int half = maximum / 2;
        int erased = Math.min(erasedCount, half);
        int damaged = Math.min(damagedCount, half);
        int remaining = maximum - erased - damaged;

        int extraDamaged = Math.min(damagedCount - damaged, remaining);
        damaged += extraDamaged;
        remaining -= extraDamaged;
        erased += Math.min(erasedCount - erased, remaining);
        return new Limits(erased, damaged);
    }
}
