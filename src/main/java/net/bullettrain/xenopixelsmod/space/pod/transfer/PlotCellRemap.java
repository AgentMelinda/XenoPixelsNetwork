package net.bullettrain.xenopixelsmod.space.pod.transfer;

import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.BitSet;
import java.util.Optional;

/**
 * Moves a serialized Sable body into a different plot cell of its destination container. Sable 2.0.3
 * {@code SubLevelSerializer.fullyLoad} allocates at the serialized {@code plot_x/plot_z} (LOCAL cells:
 * {@code ServerLevelPlot.save} writes {@code plotPos - container.getOrigin()}), so a cross-container copy is
 * refused whenever that cell is already taken in the destination. Chunk keys in the plot tag are plot-relative
 * ({@code toLocal}/{@code toGlobal}) and relocate with the cell; three storage-frame values are absolute and are
 * shifted here by the same number of blocks:
 * <ul>
 *   <li>{@code pose.rotation_point} (storage frame; {@code pose.position} is world and stays put, so the world
 *       placement {@code R*(storage - rotationPoint) + position} is unchanged);</li>
 *   <li>{@code block_entities[*].x/z} ({@code BlockEntity.saveMetadata} writes the absolute position);</li>
 *   <li>{@code block_ticks/fluid_ticks[*].x/z} ({@code SavedTick} stores absolute x/y/z and
 *       {@code loadTickList} DROPS any tick whose position is outside the loaded chunk).</li>
 * </ul>
 * Fails closed: a tag whose shape is not exactly this is not remapped, and the transfer transaction refuses before
 * releasing the source body. Any partial edits are confined to the deep copy and discarded on refusal. The
 * {@link SubLevelData#bounds()} value is a world-space bound; the storage shift and matching rotation-point shift
 * preserve the body's world geometry, so that bound remains unchanged.
 */
public final class PlotCellRemap {
    private static final int MAX_STALE_OCCUPANCY_PROBES = 16_384;

    private PlotCellRemap() {}

    /** A chosen destination cell and the storage-frame block shift that goes with it. */
    public record Cell(int x, int z, int shiftX, int shiftZ) {}

    /** Whether cell (x, z) of the destination grid can take a new body. */
    @FunctionalInterface
    public interface CellCheck {
        boolean free(int x, int z);
    }

    /**
     * Finds a free local cell from the occupancy index. Sable 2.0.3 uses index = x + (z &lt;&lt; logSideLength),
     * so the inverse is x = index % side, z = index / side. The callback checks the live plot array as a second
     * guard against stale occupancy state. A bounded number of such mismatches prevents a corrupt/full grid from
     * turning a transfer into an unbounded server-thread scan.
     */
    public static Optional<Vector2i> freeCell(int logSideLength, BitSet occupancy, CellCheck check) {
        if (logSideLength < 0 || logSideLength > 15) return Optional.empty();
        if (occupancy == null || check == null) return Optional.empty();
        int side = 1 << logSideLength;
        long cells = (long) side * side;
        int liveMismatches = 0;
        for (int index = occupancy.nextClearBit(0); index < cells;
             index = occupancy.nextClearBit(index + 1)) {
            int x = index % side;
            int z = index / side;
            if (check.free(x, z)) return Optional.of(new Vector2i(x, z));
            if (++liveMismatches >= MAX_STALE_OCCUPANCY_PROBES) return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Block shift along one axis of the storage frame when a body serialized from cell {@code oldLocal} of a
     * container with origin {@code oldOrigin} is loaded into cell {@code newLocal} of a container with origin
     * {@code newOrigin}. Sable 2.0.3: {@code ServerLevelPlot.save} writes {@code plot_x = plotPos.x - origin.x}
     * (a LOCAL cell, :13-30); {@code SubLevelContainer.allocateSubLevel} adds {@code originX/originZ} before
     * {@code createSubLevel} (:70-83), so {@code plotPos = local + origin}; {@code LevelPlot.toGlobal} then adds
     * {@code plotPos << logSize} chunks. Within one container the origins cancel and this is
     * {@code (newLocal - oldLocal) << logPlotSize << 4}; across containers with different origins the difference
     * also moves the frame. Computed in long and range-checked before it becomes an NBT int.
     */
    public static int shiftBlocks(int oldLocal, int oldOrigin, int newLocal, int newOrigin, int logPlotSize) {
        if (logPlotSize < 0 || logPlotSize >= Long.SIZE - 1)
            throw new IllegalArgumentException("invalid Sable log plot size: " + logPlotSize);
        long cells = ((long) newLocal + newOrigin) - ((long) oldLocal + oldOrigin);
        final long blocks;
        try {
            blocks = Math.multiplyExact(Math.multiplyExact(cells, 1L << logPlotSize), 16L);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("plot cell shift overflows block coordinates", overflow);
        }
        if (blocks > Integer.MAX_VALUE || blocks < Integer.MIN_VALUE)
            throw new IllegalArgumentException("plot cell shift overflows block coordinates: " + blocks);
        return (int) blocks;
    }

    /**
     * A copy of {@code data} placed in {@code cell}. The original tag is never modified (rollback reloads it).
     *
     * @return empty when the tag does not have the verified shape, so the caller fails closed
     */
    public static Optional<SubLevelData> remap(SubLevelData data, Cell cell) {
        CompoundTag full = data.fullTag().copy();
        if (!full.contains("plot", Tag.TAG_COMPOUND) || !full.contains("pose", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag plot = full.getCompound("plot");
        if (!plot.contains("plot_x", Tag.TAG_INT) || !plot.contains("plot_z", Tag.TAG_INT)
                || !plot.contains("chunks", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag pose = full.getCompound("pose");
        if (!pose.contains("rotation_point", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag rotationPointTag = pose.getCompound("rotation_point");
        if (!rotationPointTag.contains("x", Tag.TAG_DOUBLE) || !rotationPointTag.contains("y", Tag.TAG_DOUBLE)
                || !rotationPointTag.contains("z", Tag.TAG_DOUBLE)) return Optional.empty();

        plot.putInt("plot_x", cell.x());
        plot.putInt("plot_z", cell.z());
        Vector3d rotationPoint = SableNBTUtils.readVector3d(rotationPointTag);
        if (!finite(rotationPoint)) return Optional.empty();
        rotationPoint.add(cell.shiftX(), 0, cell.shiftZ());
        if (!finite(rotationPoint)) return Optional.empty();
        pose.put("rotation_point", SableNBTUtils.writeVector3d(rotationPoint));

        CompoundTag chunks = plot.getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            if (!chunks.contains(key, Tag.TAG_COMPOUND)) return Optional.empty();
            CompoundTag chunk = chunks.getCompound(key);
            for (String list : new String[]{"block_entities", "block_ticks", "fluid_ticks"}) {
                if (!chunk.contains(list)) continue;
                if (!(chunk.get(list) instanceof ListTag entries)) return Optional.empty();
                for (int i = 0; i < entries.size(); i++) {
                    if (!(entries.get(i) instanceof CompoundTag entry)) return Optional.empty();
                    if (!entry.contains("x", Tag.TAG_INT) || !entry.contains("y", Tag.TAG_INT)
                            || !entry.contains("z", Tag.TAG_INT)) return Optional.empty();
                    final int shiftedX;
                    final int shiftedZ;
                    try {
                        shiftedX = Math.toIntExact((long) entry.getInt("x") + cell.shiftX());
                        shiftedZ = Math.toIntExact((long) entry.getInt("z") + cell.shiftZ());
                    } catch (ArithmeticException overflow) {
                        return Optional.empty();
                    }
                    entry.putInt("x", shiftedX);
                    entry.putInt("z", shiftedZ);
                }
            }
        }
        var shiftedPose = SableNBTUtils.readPose3d(pose);
        return Optional.of(new SubLevelData(data.uuid(), data.bounds(), shiftedPose, data.dependencies(), full));
    }

    private static boolean finite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
