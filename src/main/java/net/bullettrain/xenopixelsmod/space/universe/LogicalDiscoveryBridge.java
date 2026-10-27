package net.bullettrain.xenopixelsmod.space.universe;

import java.util.Objects;
import java.util.function.Predicate;

/** Deterministic, bounded logical addresses unlocked by ordinary finite-field discoveries. */
public final class LogicalDiscoveryBridge {
    /** At most this many seeded logical cells are tested for each finite discovery. */
    public static final int MAX_ADDRESS_PROBES = 128;
    private static final long LOGICAL_X_BASE = 1L << 32;
    private static final long LOGICAL_Z = -(1L << 32);

    private LogicalDiscoveryBridge() {}

    /**
     * Maps a finite source cell into a disjoint signed-long address band, then probes a fixed number of cells for
     * an occupied system. The resulting address is outside the finite Minecraft-border-safe grid, while each finite
     * source owns a non-overlapping 128-address range. This is a bounded bridge; it does not make physical coordinates
     * or destinations infinite.
     */
    public static InfiniteUniverseField.Cell findOccupiedAddress(long fieldSeed, UniverseField.Cell source,
                                                                   Predicate<InfiniteUniverseField.Cell> available) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(available, "available");
        long index = ((long) source.x() + UniverseField.MAX_CELL) * UniverseField.GRID_DIAMETER
                + source.z() + UniverseField.MAX_CELL;
        long startX = LOGICAL_X_BASE + index * MAX_ADDRESS_PROBES;
        for (int probe = 0; probe < MAX_ADDRESS_PROBES; probe++) {
            var candidate = new InfiniteUniverseField.Cell(startX + probe, LOGICAL_Z);
            if (InfiniteUniverseField.systemAt(fieldSeed, candidate) != null && available.test(candidate))
                return candidate;
        }
        return null;
    }

    public enum TravelRefusal {
        NONE, LOGICAL_DISABLED, WARP_DISABLED, NOT_IN_VOIDSEA, NOT_DISCOVERED,
        CHART_ONLY, SEED_MISMATCH, UNBOUND, UNRESOLVED
    }

    /** Pure gate shared by the player-only route and its tests; it never allocates a binding. */
    static TravelRefusal travelRefusal(boolean logicalEnabled, boolean warpEnabled, boolean inVoidsea,
                                       InfiniteUniverseSavedData.Entry entry, boolean seedMatches,
                                       boolean bound, boolean resolved) {
        if (!logicalEnabled) return TravelRefusal.LOGICAL_DISABLED;
        if (!warpEnabled) return TravelRefusal.WARP_DISABLED;
        if (!inVoidsea) return TravelRefusal.NOT_IN_VOIDSEA;
        if (entry == null) return TravelRefusal.NOT_DISCOVERED;
        if (entry.source() != InfiniteUniverseSavedData.Source.DISCOVERY) return TravelRefusal.CHART_ONLY;
        if (!seedMatches) return TravelRefusal.SEED_MISMATCH;
        if (!bound) return TravelRefusal.UNBOUND;
        if (!resolved) return TravelRefusal.UNRESOLVED;
        return TravelRefusal.NONE;
    }

    /**
     * Readiness for the post-bind discovery notice. The notice is sent only to players in the Voidsea, so its
     * destination-side travel precondition is satisfied for every recipient.
     */
    static TravelRefusal discoveryAnnouncementReadiness(boolean logicalEnabled, boolean warpEnabled,
                                                         InfiniteUniverseSavedData.Entry entry,
                                                         boolean seedMatches, boolean bound, boolean resolved) {
        return travelRefusal(logicalEnabled, warpEnabled, true, entry, seedMatches, bound, resolved);
    }
}
