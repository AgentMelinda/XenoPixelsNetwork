package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseMapLod;
import net.bullettrain.xenopixelsmod.space.universe.UniverseSnapshot;
import net.bullettrain.xenopixelsmod.space.universe.UniverseFrontierSnapshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side bounded view of the server's known universe; star-system details are computed only for capped views. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class UniverseClient {
    private static final int MAX_SKY_SYSTEMS = 8_192;
    public record Known(UniverseField.Cell cell, UniverseField.StarSystem system) {}

    private static volatile boolean hasSnapshot;
    private static volatile long fieldSeed;
    private static volatile List<Known> skySystems = List.of();
    private static volatile Map<UniverseField.Cell, UniverseField.StarSystem> frontierSystems = Map.of();
    private static volatile UniverseFrontierSnapshot latestFrontierSnapshot;
    private static volatile List<Known> naturalSkySystems = List.of();
    private static volatile boolean[] naturalKnownByCell = new boolean[UniverseField.GRID_CELL_COUNT];
    private static volatile int naturalKnownCount;
    private static volatile boolean[] knownByCell = new boolean[UniverseField.GRID_CELL_COUNT];
    private static volatile int knownCount;
    private static volatile boolean warpAllowed;
    private static volatile long revision, skyRevision;

    private UniverseClient() {}

    public static void apply(UniverseSnapshot packet) {
        var grid = new boolean[UniverseField.GRID_CELL_COUNT];
        int count = packet.revealedCount();
        var sky = new ArrayList<Known>(Math.min(MAX_SKY_SYSTEMS, count));
        int stride = count <= MAX_SKY_SYSTEMS ? 1 : UniverseMapLod.stride1d(count, MAX_SKY_SYSTEMS);
        int[] ordinal = {0};
        packet.forEachRevealedIndex(index -> {
            grid[index] = true;
            if (ordinal[0]++ % stride != 0 || sky.size() >= MAX_SKY_SYSTEMS) return;
            var cell = UniverseSnapshot.cellAt(index);
            var system = UniverseField.systemAt(packet.fieldSeed(), cell);
            if (system != null) sky.add(new Known(cell, system));
        });
        fieldSeed = packet.fieldSeed();
        hasSnapshot = true;
        naturalKnownByCell = grid;
        naturalKnownCount = count;
        naturalSkySystems = List.copyOf(sky);
        knownByCell = grid.clone();
        knownCount = count;
        warpAllowed = packet.warpAllowed();
        skySystems = naturalSkySystems;
        frontierSystems = Map.of();
        revision++;
        skyRevision++;
        var frontier = latestFrontierSnapshot;
        if (frontier != null && frontier.fieldSeed() == fieldSeed) applyFrontier(frontier);
    }

    /** Adds bounded logical systems hosted in finite vacant cells to the finite Atlas/sky marker set. */
    public static void apply(net.bullettrain.xenopixelsmod.space.universe.UniverseFrontierSnapshot packet) {
        latestFrontierSnapshot = packet;
        if (!hasSnapshot || fieldSeed != packet.fieldSeed()) return;
        applyFrontier(packet);
    }

    private static void applyFrontier(UniverseFrontierSnapshot packet) {
        var hosted = new LinkedHashMap<UniverseField.Cell, UniverseField.StarSystem>();
        boolean[] grid = naturalKnownByCell.clone();
        var sky = new ArrayList<>(naturalSkySystems);
        var skyCells = new HashSet<UniverseField.Cell>();
        for (Known known : sky) skyCells.add(known.cell());
        int additionalKnown = 0;
        for (var binding : packet.bindings()) {
            var source = net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseField.systemAt(
                    packet.fieldSeed(), binding.logical());
            if (source == null) continue;
            var physical = binding.physical();
            var system = new UniverseField.StarSystem(physical, source.seed(), source.name(), source.starClass(),
                    physical.x() * (double) UniverseField.CELL_SIZE + source.localX(), source.y(),
                    physical.z() * (double) UniverseField.CELL_SIZE + source.localZ(), source.planetSlots());
            hosted.put(physical, system);
            int index = UniverseField.index(physical);
            if (!grid[index]) { grid[index] = true; additionalKnown++; }
            if (sky.size() < MAX_SKY_SYSTEMS && skyCells.add(physical)) sky.add(new Known(physical, system));
        }
        frontierSystems = Map.copyOf(hosted);
        knownByCell = grid;
        knownCount = naturalKnownCount + additionalKnown;
        warpAllowed = packet.warpAllowed();
        skySystems = List.copyOf(sky);
        revision++;
        skyRevision++;
    }

    public static void apply(net.bullettrain.xenopixelsmod.space.universe.UniverseCellReveal packet) {
        if (!hasSnapshot || fieldSeed != packet.fieldSeed()) return;
        boolean[] grid = naturalKnownByCell;
        int index = UniverseField.index(packet.cell());
        if (grid[index]) {
            if (warpAllowed != packet.warpAllowed()) { warpAllowed = packet.warpAllowed(); revision++; }
            return;
        }
        grid[index] = true;
        knownCount++;
        if (warpAllowed != packet.warpAllowed()) warpAllowed = packet.warpAllowed();
        var system = UniverseField.systemAt(packet.fieldSeed(), packet.cell());
        if (system == null) {
            grid[index] = false;
            knownCount--;
            return;
        }
        naturalKnownCount++;
        if (naturalSkySystems.size() < MAX_SKY_SYSTEMS) {
            var updatedSky = new ArrayList<>(naturalSkySystems);
            updatedSky.add(new Known(packet.cell(), system));
            naturalSkySystems = List.copyOf(updatedSky);
        }
        if (latestFrontierSnapshot != null && latestFrontierSnapshot.fieldSeed() == fieldSeed)
            applyFrontier(latestFrontierSnapshot);
        else {
            knownByCell = grid.clone();
            knownCount = naturalKnownCount;
            skySystems = naturalSkySystems;
            warpAllowed = packet.warpAllowed();
            revision++;
            skyRevision++;
        }
    }

    /** Systems visible in a rectangular Atlas viewport, sampled to a caller-provided marker budget. */
    public static List<Known> atlasSystems(double centerX, double centerZ, double halfWidth, double halfHeight,
                                           int maxMarkers) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(halfWidth)
                || !Double.isFinite(halfHeight) || halfWidth < 0 || halfHeight < 0 || maxMarkers <= 0 || !hasSnapshot)
            return List.of();
        double left = centerX - halfWidth, right = centerX + halfWidth;
        double top = centerZ - halfHeight, bottom = centerZ + halfHeight;
        if (right < -UniverseField.MAX_CELL - 1 || left > UniverseField.MAX_CELL + 1
                || bottom < -UniverseField.MAX_CELL - 1 || top > UniverseField.MAX_CELL + 1) return List.of();
        int minX = clampCell(Math.floor(left - 1));
        int maxX = clampCell(Math.ceil(right + 1));
        int minZ = clampCell(Math.floor(top - 1));
        int maxZ = clampCell(Math.ceil(bottom + 1));
        int columns = maxZ - minZ + 1;
        int rows = maxX - minX + 1;
        int stride = UniverseMapLod.stride(columns, rows, maxMarkers);
        long area = (long) columns * rows;
        var result = new ArrayList<Known>(Math.min(maxMarkers, (int) Math.min(Integer.MAX_VALUE, area)));
        boolean[] grid = knownByCell;
        for (long offset = 0; offset < area; offset += stride) {
            int x = minX + (int) (offset / columns);
            int z = minZ + (int) (offset % columns);
            if (!grid[UniverseField.index(x, z)]) continue;
            var cell = new UniverseField.Cell(x, z);
            var system = frontierSystems.get(cell);
            if (system == null) system = UniverseField.systemAt(fieldSeed, cell);
            if (system != null) result.add(new Known(cell, system));
        }
        return List.copyOf(result);
    }

    private static int clampCell(double value) {
        return (int) Math.max(-UniverseField.MAX_CELL, Math.min(UniverseField.MAX_CELL, value));
    }

    public static boolean hasSnapshot() { return hasSnapshot; }
    /** Server-derived seed shared by all clients for deterministic ambient space visuals. */
    public static long fieldSeed() { return fieldSeed; }
    public static int knownSystemCount() { return knownCount; }
    public static boolean warpAllowed() { return warpAllowed; }
    public static long revision() { return revision; }
    public static long skyRevision() { return skyRevision; }
    public static List<Known> skySystems() { return skySystems; }

    /** Resolves natural finite systems first, then the currently synced frontier incarnation. */
    public static UniverseField.StarSystem systemAt(long seed, UniverseField.Cell cell) {
        if (hasSnapshot && fieldSeed == seed) return resolveSystem(seed, cell, frontierSystems);
        return UniverseField.systemAt(seed, cell);
    }

    static UniverseField.StarSystem resolveSystem(long seed, UniverseField.Cell cell,
                                                   Map<UniverseField.Cell, UniverseField.StarSystem> hosted) {
        UniverseField.StarSystem natural = UniverseField.systemAt(seed, cell);
        if (natural != null) return natural;
        return hosted.get(cell);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        hasSnapshot = false;
        fieldSeed = 0;
        skySystems = List.of();
        frontierSystems = Map.of();
        latestFrontierSnapshot = null;
        naturalSkySystems = List.of();
        naturalKnownByCell = new boolean[UniverseField.GRID_CELL_COUNT];
        naturalKnownCount = 0;
        knownByCell = new boolean[UniverseField.GRID_CELL_COUNT];
        knownCount = 0;
        warpAllowed = false;
        revision++;
        skyRevision++;
    }
}
