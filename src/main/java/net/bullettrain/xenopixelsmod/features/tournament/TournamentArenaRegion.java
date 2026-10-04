package net.bullettrain.xenopixelsmod.features.tournament;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Objects;

/**
 * One tournament fight cell: teleport spawn + axis-aligned bounds.
 *
 * <p>v1 (2026-10-03): set from WorldEdit selection or a default box around the DMZ
 * {@code cell_arena} structure locate point. Out-of-bounds auto-lose is a follow-on
 * ({@code tournamentOutOfBoundsLose}); this type only stores geometry for teleport + checks.
 */
public final class TournamentArenaRegion {
    /** Same pad {@code XenoStructureCommands.clearOld} uses around cell_arena. */
    public static final int CELL_PAD_XZ = 28;
    public static final int CELL_PAD_Y_DOWN = 12;
    public static final int CELL_PAD_Y_UP = 28;

    private static final String DIM_KEY = "Dim";
    private static final String LABEL_KEY = "Label";
    private static final String SX = "Sx";
    private static final String SY = "Sy";
    private static final String SZ = "Sz";
    private static final String MIN_X = "MinX";
    private static final String MIN_Y = "MinY";
    private static final String MIN_Z = "MinZ";
    private static final String MAX_X = "MaxX";
    private static final String MAX_Y = "MaxY";
    private static final String MAX_Z = "MaxZ";

    private final String dimension;
    private final String label;
    private final BlockPos spawn;
    private final BlockPos min;
    private final BlockPos max;

    public TournamentArenaRegion(String dimension, String label, BlockPos spawn,
                                 BlockPos cornerA, BlockPos cornerB) {
        this.dimension = dimension == null || dimension.isBlank()
                ? "minecraft:overworld"
                : dimension.trim().toLowerCase(Locale.ROOT);
        this.label = label == null || label.isBlank() ? "arena" : label.trim();
        BlockPos a = Objects.requireNonNull(cornerA, "cornerA");
        BlockPos b = Objects.requireNonNull(cornerB, "cornerB");
        this.min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        BlockPos sp = spawn == null
                ? new BlockPos(
                (this.min.getX() + this.max.getX()) / 2,
                this.min.getY() + 1,
                (this.min.getZ() + this.max.getZ()) / 2)
                : spawn;
        this.spawn = clampSpawn(sp);
    }

    /** Default fight box around a DMZ {@code cell_arena} locate/place origin. */
    public static TournamentArenaRegion aroundCellArena(String dimension, BlockPos origin) {
        BlockPos centre = Objects.requireNonNull(origin, "origin");
        BlockPos min = centre.offset(-CELL_PAD_XZ, -CELL_PAD_Y_DOWN, -CELL_PAD_XZ);
        BlockPos max = centre.offset(CELL_PAD_XZ, CELL_PAD_Y_UP, CELL_PAD_XZ);
        BlockPos spawn = centre.above();
        return new TournamentArenaRegion(dimension, "cell_arena", spawn, min, max);
    }

    public static TournamentArenaRegion fromWorldEdit(String dimension, String label,
                                                      BlockPos spawn, BlockPos min, BlockPos max) {
        return new TournamentArenaRegion(dimension, label, spawn, min, max);
    }

    public String dimension() {
        return dimension;
    }

    public String label() {
        return label;
    }

    public BlockPos spawn() {
        return spawn;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public AABB aabb() {
        return new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }

    public boolean contains(Vec3 pos) {
        return pos != null && aabb().contains(pos);
    }

    public boolean containsBlock(BlockPos pos) {
        if (pos == null) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public TournamentArenaRegion withSpawn(BlockPos nextSpawn) {
        return new TournamentArenaRegion(dimension, label, nextSpawn, min, max);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIM_KEY, dimension);
        tag.putString(LABEL_KEY, label);
        tag.putInt(SX, spawn.getX());
        tag.putInt(SY, spawn.getY());
        tag.putInt(SZ, spawn.getZ());
        tag.putInt(MIN_X, min.getX());
        tag.putInt(MIN_Y, min.getY());
        tag.putInt(MIN_Z, min.getZ());
        tag.putInt(MAX_X, max.getX());
        tag.putInt(MAX_Y, max.getY());
        tag.putInt(MAX_Z, max.getZ());
        return tag;
    }

    public static TournamentArenaRegion load(CompoundTag tag) {
        if (tag == null) return null;
        String dim = tag.getString(DIM_KEY);
        String label = tag.getString(LABEL_KEY);
        BlockPos spawn = new BlockPos(tag.getInt(SX), tag.getInt(SY), tag.getInt(SZ));
        BlockPos min = new BlockPos(tag.getInt(MIN_X), tag.getInt(MIN_Y), tag.getInt(MIN_Z));
        BlockPos max = new BlockPos(tag.getInt(MAX_X), tag.getInt(MAX_Y), tag.getInt(MAX_Z));
        return new TournamentArenaRegion(dim, label, spawn, min, max);
    }

    public String describe(int index) {
        return "[" + index + "] " + label
                + " dim=" + dimension
                + " spawn=" + spawn.getX() + "," + spawn.getY() + "," + spawn.getZ()
                + " bounds=" + min.getX() + "," + min.getY() + "," + min.getZ()
                + " .. " + max.getX() + "," + max.getY() + "," + max.getZ();
    }

    public static boolean sameDimension(String a, ResourceLocation b) {
        if (b == null) return false;
        String left = a == null ? "minecraft:overworld" : a.trim().toLowerCase(Locale.ROOT);
        return left.equals(b.toString());
    }

    private BlockPos clampSpawn(BlockPos sp) {
        int x = Math.min(max.getX(), Math.max(min.getX(), sp.getX()));
        int y = Math.min(max.getY(), Math.max(min.getY(), sp.getY()));
        int z = Math.min(max.getZ(), Math.max(min.getZ(), sp.getZ()));
        return new BlockPos(x, y, z);
    }
}
