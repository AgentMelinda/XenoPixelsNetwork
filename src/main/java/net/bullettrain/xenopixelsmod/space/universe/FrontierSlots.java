package net.bullettrain.xenopixelsmod.space.universe;

import java.util.Set;

/**
 * Selects deterministic vacant finite-field cells that can host a bounded physical incarnation of a logical system.
 * The returned cell is a backend location only; it does not replace the logical cell identity or make physical
 * neighbours follow logical-coordinate adjacency.
 */
public final class FrontierSlots {
    private static final long X_SALT = 0x243F6A8885A308D3L;
    private static final long Z_SALT = 0x13198A2E03707344L;

    private FrontierSlots() {}

    /**
     * Finds a vacant, unreserved finite cell for this logical address. The saved-data owner must persist the returned
     * binding before exposing it to travel or physicalization; bindings are append-only because chunks may be saved.
     */
    public static UniverseField.Cell findVacant(long worldSeed, InfiniteUniverseField.Cell logical,
                                                 Set<UniverseField.Cell> reserved) {
        if (logical == null) throw new IllegalArgumentException("logical system address is required");
        if (reserved == null) throw new IllegalArgumentException("reserved frontier slots are required");

        int diameter = UniverseField.GRID_DIAMETER;
        int count = UniverseField.GRID_CELL_COUNT;
        long key = UniverseField.mix(worldSeed ^ UniverseField.mix(logical.x() * X_SALT)
                ^ UniverseField.mix(logical.z() * Z_SALT) ^ 0xA4093822299F31D0L);
        int start = (int) Math.floorMod(key, (long) count);
        for (int offset = 0; offset < count; offset++) {
            int index = start + offset;
            if (index >= count) index -= count;
            int x = index / diameter - UniverseField.MAX_CELL;
            int z = index % diameter - UniverseField.MAX_CELL;
            var candidate = new UniverseField.Cell(x, z);
            if (reserved.contains(candidate)) continue;
            if (UniverseField.systemAt(worldSeed, candidate) == null) return candidate;
        }
        return null;
    }
}
