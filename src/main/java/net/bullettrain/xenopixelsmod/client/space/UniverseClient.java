package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseMapLod;
import net.bullettrain.xenopixelsmod.space.universe.UniverseSnapshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.List;

/** Client-side bounded view of the server's known universe; star-system details are computed only for capped views. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class UniverseClient {
    private static final int MAX_SKY_SYSTEMS = 8_192;
    public record Known(UniverseField.Cell cell, UniverseField.StarSystem system) {}

    private static volatile boolean hasSnapshot;
    private static volatile long fieldSeed;
    private static volatile List<Known> skySystems = List.of();
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
        knownByCell = grid;
        knownCount = count;
        warpAllowed = packet.warpAllowed();
        skySystems = List.copyOf(sky);
        revision++;
        skyRevision++;
    }

    public static void apply(net.bullettrain.xenopixelsmod.space.universe.UniverseCellReveal packet) {
        if (!hasSnapshot || fieldSeed != packet.fieldSeed()) return;
        boolean[] grid = knownByCell;
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
        if (skySystems.size() < MAX_SKY_SYSTEMS) {
            var updatedSky = new ArrayList<>(skySystems);
            updatedSky.add(new Known(packet.cell(), system));
            skySystems = List.copyOf(updatedSky);
            skyRevision++;
        }
        revision++;
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
            var system = UniverseField.systemAt(fieldSeed, cell);
            if (system != null) result.add(new Known(cell, system));
        }
        return List.copyOf(result);
    }

    private static int clampCell(double value) {
        return (int) Math.max(-UniverseField.MAX_CELL, Math.min(UniverseField.MAX_CELL, value));
    }

    public static boolean hasSnapshot() { return hasSnapshot; }
    public static int knownSystemCount() { return knownCount; }
    public static boolean warpAllowed() { return warpAllowed; }
    public static long revision() { return revision; }
    public static long skyRevision() { return skyRevision; }
    public static List<Known> skySystems() { return skySystems; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        hasSnapshot = false;
        fieldSeed = 0;
        skySystems = List.of();
        knownByCell = new boolean[UniverseField.GRID_CELL_COUNT];
        knownCount = 0;
        warpAllowed = false;
        revision++;
        skyRevision++;
    }
}
