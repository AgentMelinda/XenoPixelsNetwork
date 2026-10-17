package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic address layout for S11 C3's shared {@code planet_surfaces} dimension.
 * Each universe-system cell owns four profile lanes, each containing four planet slots. Encoding
 * the chosen profile in the address lets a stateless chunk generator reproduce the planet's
 * selected surface without consulting runtime SavedData during asynchronous chunk generation.
 */
public final class PlanetSurfaceCells {
    public static final int CELL_SIZE = 8_192;
    public static final int PLANET_SLOTS_PER_SYSTEM = SmallPlanetField.MAX_PLANETS_PER_SYSTEM;
    // Frozen at five: adding a special profile must not shift previously assigned small-planet cells.
    private static final int PROFILE_LANES = 5;
    private static final int CELLS_PER_SYSTEM_X = PROFILE_LANES * 2;
    private static final int CELLS_PER_SYSTEM_Z = 2;
    private static final int MIN_CELL_X = -UniverseField.MAX_CELL * CELLS_PER_SYSTEM_X;
    private static final int MAX_CELL_X = UniverseField.MAX_CELL * CELLS_PER_SYSTEM_X + CELLS_PER_SYSTEM_X - 1;
    private static final int MIN_CELL_Z = -UniverseField.MAX_CELL * CELLS_PER_SYSTEM_Z;
    private static final int MAX_CELL_Z = UniverseField.MAX_CELL * CELLS_PER_SYSTEM_Z + CELLS_PER_SYSTEM_Z - 1;
    private static final Cell MOON_SURFACE_CELL = new Cell(0, MAX_CELL_Z + 1);

    private PlanetSurfaceCells() {}

    /** Fixed terrain cell for the charted Moon, outside all ordinary system/profile/slot addresses. */
    public static Cell moonSurfaceCell() { return MOON_SURFACE_CELL; }

    /** Surface-grid coordinates. Bounds are limited to the cells assigned to the finite universe field. */
    public record Cell(int x, int z) {
        public Cell {
            boolean moonCell = x == 0 && z == MAX_CELL_Z + 1;
            if (x < MIN_CELL_X || x > MAX_CELL_X || z < MIN_CELL_Z || (z > MAX_CELL_Z && !moonCell))
                throw new IllegalArgumentException("surface cell is outside the generated universe");
        }

        public long minBlockX() { return (long) x * CELL_SIZE; }
        public long minBlockZ() { return (long) z * CELL_SIZE; }
        public long maxBlockX() { return minBlockX() + CELL_SIZE - 1; }
        public long maxBlockZ() { return minBlockZ() + CELL_SIZE - 1; }
        public long centerBlockX() { return minBlockX() + CELL_SIZE / 2L; }
        public long centerBlockZ() { return minBlockZ() + CELL_SIZE / 2L; }
    }

    /** Planet identity encoded by a surface cell; a caller still checks that the system and slot exist. */
    public record Address(Cell surfaceCell, UniverseField.Cell systemCell, int planetSlot,
                          SmallPlanetField.Profile profile) {
        public Address {
            Objects.requireNonNull(surfaceCell, "surfaceCell");
            Objects.requireNonNull(systemCell, "systemCell");
            Objects.requireNonNull(profile, "profile");
            if (planetSlot < 0 || planetSlot >= PLANET_SLOTS_PER_SYSTEM)
                throw new IllegalArgumentException("planet slot is outside the small-planet field");
        }
    }

    /** Stable, collision-free cell for one generated small-planet identity. */
    public static Cell forPlanet(UniverseField.Cell systemCell, int planetSlot, SmallPlanetField.Profile profile) {
        Objects.requireNonNull(systemCell, "systemCell");
        Objects.requireNonNull(profile, "profile");
        if (planetSlot < 0 || planetSlot >= PLANET_SLOTS_PER_SYSTEM)
            throw new IllegalArgumentException("planet slot is outside the small-planet field");
        if (profile == SmallPlanetField.Profile.LUNAR)
            throw new IllegalArgumentException("the lunar profile is reserved for the charted Moon cell");
        int cellX = systemCell.x() * CELLS_PER_SYSTEM_X + profileLane(profile) * 2 + (planetSlot & 1);
        int cellZ = systemCell.z() * CELLS_PER_SYSTEM_Z + (planetSlot >>> 1);
        return new Cell(cellX, cellZ);
    }

    /** Resolves a grid coordinate back to its system and slot. Empty cells may represent absent systems/slots. */
    public static Address addressAt(Cell surfaceCell) {
        Objects.requireNonNull(surfaceCell, "surfaceCell");
        if (surfaceCell.equals(MOON_SURFACE_CELL))
            return new Address(surfaceCell, new UniverseField.Cell(0, 0), 0, SmallPlanetField.Profile.LUNAR);
        int systemX = Math.floorDiv(surfaceCell.x(), CELLS_PER_SYSTEM_X);
        int systemZ = Math.floorDiv(surfaceCell.z(), CELLS_PER_SYSTEM_Z);
        int xWithinSystem = Math.floorMod(surfaceCell.x(), CELLS_PER_SYSTEM_X);
        int profileIndex = xWithinSystem >>> 1;
        int planetSlot = (Math.floorMod(surfaceCell.z(), CELLS_PER_SYSTEM_Z) << 1) | (xWithinSystem & 1);
        return new Address(surfaceCell, new UniverseField.Cell(systemX, systemZ), planetSlot,
                profileAtLane(profileIndex));
    }

    /** Resolves a block location by floor division, including negative coordinates and exact cell edges. */
    public static Optional<Address> addressAtBlock(long blockX, long blockZ) {
        long cellX = Math.floorDiv(blockX, CELL_SIZE);
        long cellZ = Math.floorDiv(blockZ, CELL_SIZE);
        boolean moonCell = cellX == MOON_SURFACE_CELL.x() && cellZ == MOON_SURFACE_CELL.z();
        if (cellX < MIN_CELL_X || cellX > MAX_CELL_X || cellZ < MIN_CELL_Z || (cellZ > MAX_CELL_Z && !moonCell))
            return Optional.empty();
        Cell cell = new Cell((int) cellX, (int) cellZ);
        return Optional.of(addressAt(cell));
    }

    private static int profileLane(SmallPlanetField.Profile profile) {
        return switch (profile) {
            case ROCK -> 0;
            case ICE -> 1;
            case DESERT -> 2;
            case JUNGLE -> 3;
            case LAVA -> 4;
            case LUNAR -> throw new IllegalArgumentException("the lunar profile has no procedural system lane");
        };
    }

    private static SmallPlanetField.Profile profileAtLane(int lane) {
        return switch (lane) {
            case 0 -> SmallPlanetField.Profile.ROCK;
            case 1 -> SmallPlanetField.Profile.ICE;
            case 2 -> SmallPlanetField.Profile.DESERT;
            case 3 -> SmallPlanetField.Profile.JUNGLE;
            case 4 -> SmallPlanetField.Profile.LAVA;
            default -> throw new IllegalArgumentException("planet profile lane is outside the address format");
        };
    }
}
