package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.universe.UniverseField;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic address layout for S11 C3's shared {@code planet_surfaces} dimension.
 * Slots 0..3 keep their original in-grid cells. Slots 4..7 use a separate border-bounded stripe,
 * so extending a system's generated planets cannot move any existing surface address. Encoding the
 * selected profile in the address lets a stateless chunk generator reproduce terrain without
 * consulting runtime SavedData during asynchronous chunk generation.
 */
public final class PlanetSurfaceCells {
    public static final int CELL_SIZE = 8_192;
    public static final int PLANET_SLOTS_PER_SYSTEM = SmallPlanetField.MAX_PLANETS_PER_SYSTEM;
    private static final int LEGACY_INLINE_SLOTS_PER_SYSTEM = 4;
    // Frozen at five: adding a special profile must not shift previously assigned small-planet cells.
    private static final int PROFILE_LANES = 5;
    private static final int CELLS_PER_SYSTEM_X = PROFILE_LANES * 2;
    private static final int CELLS_PER_SYSTEM_Z = 2;
    private static final int MIN_CELL_X = -UniverseField.MAX_CELL * CELLS_PER_SYSTEM_X;
    private static final int MAX_CELL_X = UniverseField.MAX_CELL * CELLS_PER_SYSTEM_X + CELLS_PER_SYSTEM_X - 1;
    private static final int MIN_CELL_Z = -UniverseField.MAX_CELL * CELLS_PER_SYSTEM_Z;
    private static final int MAX_CELL_Z = UniverseField.MAX_CELL * CELLS_PER_SYSTEM_Z + CELLS_PER_SYSTEM_Z - 1;
    private static final int DRAGON_BALL_SURFACE_COUNT = 7;
    private static final String DRAGON_BALL_SURFACE_KEY_PREFIX = "xenopixelsmod:planet_surfaces/sdb/slot-";
    /** New addresses live to the right of every legacy cell, and each complete cell remains inside Minecraft's border. */
    private static final int EXTRA_MIN_CELL_X = MAX_CELL_X + 1;
    private static final int LAST_BORDER_CELL = Math.floorDiv(
            (int) UniverseField.WORLD_BORDER_LIMIT - (CELL_SIZE - 1), CELL_SIZE);
    private static final int EXTRA_CELL_WIDTH = LAST_BORDER_CELL - EXTRA_MIN_CELL_X + 1;
    private static final int EXTRA_SLOTS_PER_SYSTEM = PLANET_SLOTS_PER_SYSTEM - LEGACY_INLINE_SLOTS_PER_SYSTEM;
    private static final long EXTRA_CELL_COUNT = (long) UniverseField.GRID_CELL_COUNT * PROFILE_LANES * EXTRA_SLOTS_PER_SYSTEM;
    private static final int EXTRA_ROW_COUNT = Math.toIntExact((EXTRA_CELL_COUNT + EXTRA_CELL_WIDTH - 1) / EXTRA_CELL_WIDTH);
    private static final int EXTRA_MIN_CELL_Z = -(EXTRA_ROW_COUNT / 2);
    private static final int EXTRA_MAX_CELL_Z = EXTRA_MIN_CELL_Z + EXTRA_ROW_COUNT - 1;
    private static final int EXTRA_MAX_CELL_X = EXTRA_MIN_CELL_X + EXTRA_CELL_WIDTH - 1;
    private static final Cell MOON_SURFACE_CELL = new Cell(0, MAX_CELL_Z + 1);
    private static final int FIRST_DRAGON_BALL_CELL_Z = MAX_CELL_Z + 2;
    private static final int FIRST_CHART_BODY_CELL_Z = FIRST_DRAGON_BALL_CELL_Z + DRAGON_BALL_SURFACE_COUNT;
    private record ChartSurface(String bodyId, SmallPlanetField.Profile profile) {}
    // Append entries only: assigned cell coordinates are stable addresses and may be saved by players.
    private static final java.util.List<ChartSurface> CHART_SURFACES = java.util.List.of(
            new ChartSurface("xenopixelsmod:moon", SmallPlanetField.Profile.LUNAR),
            new ChartSurface("xenopixelsmod:phobos", SmallPlanetField.Profile.LUNAR),
            new ChartSurface("xenopixelsmod:deimos", SmallPlanetField.Profile.LUNAR),
            new ChartSurface("xenopixelsmod:io", SmallPlanetField.Profile.LAVA),
            new ChartSurface("xenopixelsmod:europa", SmallPlanetField.Profile.ICE),
            new ChartSurface("xenopixelsmod:ganymede", SmallPlanetField.Profile.ICE),
            new ChartSurface("xenopixelsmod:callisto", SmallPlanetField.Profile.ICE),
            new ChartSurface("xenopixelsmod:triton", SmallPlanetField.Profile.ICE),
            new ChartSurface("xenopixelsmod:namek_moon_1", SmallPlanetField.Profile.ROCK),
            new ChartSurface("xenopixelsmod:namek_moon_2", SmallPlanetField.Profile.ROCK),
            new ChartSurface("xenopixelsmod:namek_moon_3", SmallPlanetField.Profile.ROCK));

    static {
        if (PLANET_SLOTS_PER_SYSTEM < LEGACY_INLINE_SLOTS_PER_SYSTEM || EXTRA_CELL_WIDTH <= 0
                || EXTRA_MIN_CELL_Z < -LAST_BORDER_CELL || EXTRA_MAX_CELL_Z > LAST_BORDER_CELL
                || EXTRA_MAX_CELL_X > LAST_BORDER_CELL
                || (long) EXTRA_CELL_WIDTH * EXTRA_ROW_COUNT < EXTRA_CELL_COUNT)
            throw new ExceptionInInitializerError("planet-surface extension stripe exceeds the world-border address space");
    }

    private PlanetSurfaceCells() {}

    /** Fixed terrain cell for the charted Moon, outside all ordinary system/profile/slot addresses. */
    public static Cell moonSurfaceCell() { return MOON_SURFACE_CELL; }

    /** Stable cell for a charted body which uses the shared planet-surface dimension. */
    public static Cell forChartBody(String bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        int index = chartSurfaceIndex(bodyId);
        if (index < 0) throw new IllegalArgumentException("no reserved chart surface cell for " + bodyId);
        return index == 0 ? MOON_SURFACE_CELL : new Cell(0, FIRST_CHART_BODY_CELL_Z + index - 1);
    }

    /** The chart identity encoded by a cell, empty for generated small-planet and SDB cells. */
    public static Optional<String> chartBodyIdAt(Cell cell) {
        Objects.requireNonNull(cell, "cell");
        if (cell.equals(MOON_SURFACE_CELL)) return Optional.of(CHART_SURFACES.get(0).bodyId());
        if (cell.x() == 0) {
            int index = cell.z() - FIRST_CHART_BODY_CELL_Z + 1;
            if (index > 0 && index < CHART_SURFACES.size())
                return Optional.of(CHART_SURFACES.get(index).bodyId());
        }
        return Optional.empty();
    }

    /** Fixed, collision-free cells for the seven persistent Super Dragon Ball worlds. */
    public static Cell forDragonBall(int slot) {
        if (slot < 0 || slot >= DRAGON_BALL_SURFACE_COUNT)
            throw new IllegalArgumentException("Super Dragon Ball slot must be in [0, 7)");
        return new Cell(0, FIRST_DRAGON_BALL_CELL_Z + slot);
    }

    /** Resolves the stable key stored by {@code DragonBallIdentity} while preserving its existing save format. */
    public static Cell forDragonBallKey(String surfaceCellKey) {
        Objects.requireNonNull(surfaceCellKey, "surfaceCellKey");
        if (!surfaceCellKey.startsWith(DRAGON_BALL_SURFACE_KEY_PREFIX))
            throw new IllegalArgumentException("not a Super Dragon Ball surface-cell key");
        String suffix = surfaceCellKey.substring(DRAGON_BALL_SURFACE_KEY_PREFIX.length());
        if (suffix.length() != 1 || suffix.charAt(0) < '1' || suffix.charAt(0) > '0' + DRAGON_BALL_SURFACE_COUNT)
            throw new IllegalArgumentException("invalid Super Dragon Ball surface-cell key");
        return forDragonBall(suffix.charAt(0) - '1');
    }

    /** Identifies a reserved Super Dragon Ball cell without consulting mutable SavedData. */
    public static Optional<Integer> dragonBallSlotAt(Cell surfaceCell) {
        Objects.requireNonNull(surfaceCell, "surfaceCell");
        if (!isDragonBallCell(surfaceCell.x(), surfaceCell.z())) return Optional.empty();
        return Optional.of(surfaceCell.z() - FIRST_DRAGON_BALL_CELL_Z);
    }

    /** Surface-grid coordinates. Bounds are limited to the cells assigned to the finite universe field. */
    public record Cell(int x, int z) {
        public Cell {
            boolean legacyCell = x >= MIN_CELL_X && x <= MAX_CELL_X && z >= MIN_CELL_Z && z <= MAX_CELL_Z;
            boolean moonCell = x == 0 && z == MAX_CELL_Z + 1;
            if (!legacyCell && !moonCell && !isDragonBallCell(x, z) && !isChartBodyCell(x, z) && !isExtraCell(x, z))
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
                          SmallPlanetField.Profile profile, String chartBodyId) {
        public Address(Cell surfaceCell, UniverseField.Cell systemCell, int planetSlot,
                       SmallPlanetField.Profile profile) {
            this(surfaceCell, systemCell, planetSlot, profile, null);
        }
        public Address {
            Objects.requireNonNull(surfaceCell, "surfaceCell");
            Objects.requireNonNull(systemCell, "systemCell");
            Objects.requireNonNull(profile, "profile");
            if (planetSlot < 0 || (chartBodyId == null && planetSlot >= PLANET_SLOTS_PER_SYSTEM))
                throw new IllegalArgumentException("planet slot is outside the small-planet field");
            if (chartBodyId != null && (chartBodyId.isBlank() || chartSurfaceIndex(chartBodyId) < 0))
                throw new IllegalArgumentException("chart body id has no reserved surface address");
        }
    }

    /** Stable, collision-free cell for one generated system-planet identity. */
    public static Cell forPlanet(UniverseField.Cell systemCell, int planetSlot, SmallPlanetField.Profile profile) {
        Objects.requireNonNull(systemCell, "systemCell");
        Objects.requireNonNull(profile, "profile");
        if (planetSlot < 0 || planetSlot >= PLANET_SLOTS_PER_SYSTEM)
            throw new IllegalArgumentException("planet slot is outside the small-planet field");
        if (profile == SmallPlanetField.Profile.LUNAR)
            throw new IllegalArgumentException("the lunar profile is reserved for the charted Moon cell");
        if (planetSlot >= LEGACY_INLINE_SLOTS_PER_SYSTEM) return extraPlanetCell(systemCell, planetSlot, profile);
        int cellX = systemCell.x() * CELLS_PER_SYSTEM_X + profileLane(profile) * 2 + (planetSlot & 1);
        int cellZ = systemCell.z() * CELLS_PER_SYSTEM_Z + (planetSlot >>> 1);
        return new Cell(cellX, cellZ);
    }

    /** Resolves a grid coordinate back to its system and slot. Empty cells may represent absent systems/slots. */
    public static Address addressAt(Cell surfaceCell) {
        Objects.requireNonNull(surfaceCell, "surfaceCell");
        var chartBody = chartBodyIdAt(surfaceCell);
        if (chartBody.isPresent()) {
            int index = chartSurfaceIndex(chartBody.get());
            return new Address(surfaceCell, new UniverseField.Cell(0, 0), index,
                    CHART_SURFACES.get(index).profile(), chartBody.get());
        }
        var dragonBallSlot = dragonBallSlotAt(surfaceCell);
        if (dragonBallSlot.isPresent())
            return new Address(surfaceCell, new UniverseField.Cell(0, 0), dragonBallSlot.get(),
                    SmallPlanetField.Profile.ROCK);
        if (isExtraCell(surfaceCell.x(), surfaceCell.z())) return addressInExtraStripe(surfaceCell);
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
        boolean dragonBallCell = cellX >= Integer.MIN_VALUE && cellX <= Integer.MAX_VALUE
                && cellZ >= Integer.MIN_VALUE && cellZ <= Integer.MAX_VALUE
                && isDragonBallCell((int) cellX, (int) cellZ);
        boolean legacyCell = cellX >= MIN_CELL_X && cellX <= MAX_CELL_X && cellZ >= MIN_CELL_Z && cellZ <= MAX_CELL_Z;
        boolean chartBodyCell = cellX == 0 && cellZ >= FIRST_CHART_BODY_CELL_Z
                && cellZ < FIRST_CHART_BODY_CELL_Z + CHART_SURFACES.size() - 1;
        if (!legacyCell && !moonCell && !dragonBallCell && !chartBodyCell && (cellX < Integer.MIN_VALUE || cellX > Integer.MAX_VALUE
                || cellZ < Integer.MIN_VALUE || cellZ > Integer.MAX_VALUE
                || !isExtraCell((int) cellX, (int) cellZ)))
            return Optional.empty();
        Cell cell = new Cell((int) cellX, (int) cellZ);
        return Optional.of(addressAt(cell));
    }

    private static Cell extraPlanetCell(UniverseField.Cell systemCell, int planetSlot,
                                        SmallPlanetField.Profile profile) {
        long systemIndex = UniverseField.index(systemCell);
        long profileAndSlot = (long) profileLane(profile) * EXTRA_SLOTS_PER_SYSTEM
                + planetSlot - LEGACY_INLINE_SLOTS_PER_SYSTEM;
        long linear = (systemIndex * PROFILE_LANES * EXTRA_SLOTS_PER_SYSTEM) + profileAndSlot;
        int x = EXTRA_MIN_CELL_X + (int) (linear % EXTRA_CELL_WIDTH);
        int z = EXTRA_MIN_CELL_Z + (int) (linear / EXTRA_CELL_WIDTH);
        return new Cell(x, z);
    }

    private static Address addressInExtraStripe(Cell surfaceCell) {
        long linear = ((long) surfaceCell.z() - EXTRA_MIN_CELL_Z) * EXTRA_CELL_WIDTH
                + surfaceCell.x() - EXTRA_MIN_CELL_X;
        int extraSlot = (int) (linear % EXTRA_SLOTS_PER_SYSTEM);
        long systemAndProfile = linear / EXTRA_SLOTS_PER_SYSTEM;
        int profile = (int) (systemAndProfile % PROFILE_LANES);
        long systemIndex = systemAndProfile / PROFILE_LANES;
        int systemX = (int) (systemIndex / UniverseField.GRID_DIAMETER) - UniverseField.MAX_CELL;
        int systemZ = (int) (systemIndex % UniverseField.GRID_DIAMETER) - UniverseField.MAX_CELL;
        return new Address(surfaceCell, new UniverseField.Cell(systemX, systemZ),
                LEGACY_INLINE_SLOTS_PER_SYSTEM + extraSlot, profileAtLane(profile));
    }

    private static boolean isExtraCell(int x, int z) {
        if (EXTRA_CELL_WIDTH <= 0 || x < EXTRA_MIN_CELL_X || x > EXTRA_MAX_CELL_X
                || z < EXTRA_MIN_CELL_Z || z > EXTRA_MAX_CELL_Z) return false;
        long linear = ((long) z - EXTRA_MIN_CELL_Z) * EXTRA_CELL_WIDTH + x - EXTRA_MIN_CELL_X;
        return linear >= 0 && linear < EXTRA_CELL_COUNT;
    }

    private static boolean isDragonBallCell(int x, int z) {
        return x == 0 && z >= FIRST_DRAGON_BALL_CELL_Z
                && z < FIRST_DRAGON_BALL_CELL_Z + DRAGON_BALL_SURFACE_COUNT;
    }

    private static boolean isChartBodyCell(int x, int z) {
        return x == 0 && z >= FIRST_CHART_BODY_CELL_Z
                && z < FIRST_CHART_BODY_CELL_Z + CHART_SURFACES.size() - 1;
    }

    private static int chartSurfaceIndex(String bodyId) {
        for (int i = 0; i < CHART_SURFACES.size(); i++)
            if (CHART_SURFACES.get(i).bodyId().equals(bodyId)) return i;
        return -1;
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
