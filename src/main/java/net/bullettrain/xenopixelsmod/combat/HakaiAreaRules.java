package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The arithmetic of area Hakai (pure, for tests): which Hakai is cast, what is inside the sphere,
 * the order blocks are erased in, and how far a block's crack has got.
 */
public final class HakaiAreaRules {
    private HakaiAreaRules() {
    }

    /** Which Hakai the J key casts ({@code /xenoset hakaiMode}). */
    public enum Mode {
        /** The original: one target, looked at or locked on. */
        SINGLE,
        /** Everything in a sphere where the caster looks, no lock-on; blocks too. */
        AREA
    }

    /** Anything that is not {@code area} is the proven single-target Hakai. */
    public static Mode parseMode(String raw) {
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).equals("area") ? Mode.AREA : Mode.SINGLE;
    }

    /** Which blocks an area Hakai takes ({@code /xenoset hakaiBlockShape}). */
    public enum Shape {
        /** The sphere around the look point. */
        SPHERE,
        /** Every column around the look point, roof down to the ground: the building, not the land. */
        RAZE
    }

    public static Shape parseShape(String raw) {
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).equals("raze") ? Shape.RAZE : Shape.SPHERE;
    }

    /**
     * The raze shape (2026-09-29 owner: "i need a way to destroy building to their floor"): each
     * column within {@code radius} of the look point, across the ground, scanned from {@code top}
     * down; its non-air blocks are taken until the first {@code ground} block, which stops that
     * column and stays. Roof first, at most {@code limit}.
     */
    public static List<BlockPos> razeColumns(Vec3 centre, double radius, int top, int bottom,
                                             java.util.function.Predicate<BlockPos> ground,
                                             java.util.function.Predicate<BlockPos> air, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (limit <= 0 || !(radius > 0.0)) return out;
        int r = (int) Math.ceil(radius);
        int cx = (int) Math.floor(centre.x);
        int cz = (int) Math.floor(centre.z);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double dx = x + 0.5 - centre.x;
                double dz = z + 0.5 - centre.z;
                if (dx * dx + dz * dz > radius * radius + 1.0e-9) continue;
                for (int y = top; y >= bottom; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (ground.test(p)) break;
                    if (!air.test(p)) out.add(p);
                }
            }
        }
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingDouble(p -> centre.distanceToSqr(p.getX() + 0.5, centre.y, p.getZ() + 0.5)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    public static boolean inSphere(Vec3 centre, Vec3 point, double radius) {
        return centre.distanceToSqr(point) <= radius * radius + 1.0e-9;
    }

    /**
     * Every block whose centre lies in the sphere, highest first - a building dissolves from the
     * roof down, as a body does - then nearest the centre first. At most {@code limit}.
     */
    public static List<BlockPos> sphereTopDown(Vec3 centre, double radius, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (limit <= 0 || !(radius > 0.0)) {
            return out;
        }
        int r = (int) Math.ceil(radius);
        int cx = (int) Math.floor(centre.x);
        int cy = (int) Math.floor(centre.y);
        int cz = (int) Math.floor(centre.z);
        for (int y = cy + r; y >= cy - r; y--) {
            List<BlockPos> layer = new ArrayList<>();
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (inSphere(centre, new Vec3(x + 0.5, y + 0.5, z + 0.5), radius)) {
                        layer.add(new BlockPos(x, y, z));
                    }
                }
            }
            layer.sort(Comparator.comparingDouble(p -> centre.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5,
                    p.getZ() + 0.5)));
            for (BlockPos p : layer) {
                out.add(p);
                if (out.size() >= limit) {
                    return out;
                }
            }
        }
        return out;
    }

    /** The vanilla crack stage (0-9) after {@code elapsed} of {@code fadeTicks}. */
    public static int crackStage(int elapsed, int fadeTicks) {
        if (fadeTicks <= 0) {
            return 9;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) fadeTicks));
        return Math.min(9, (int) (t * 9.0f + 1.0e-4f));
    }

    /**
     * The first {@code limit} positions, in order, that {@code erasable} accepts. The limit counts
     * blocks that exist: taking it before dropping air spent a big sphere's whole budget on the sky
     * above a building (2026-09-29, "no blocks to erase (4096 found...)").
     */
    public static List<BlockPos> takeErasable(Iterable<BlockPos> ordered,
                                              java.util.function.Predicate<BlockPos> erasable, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (limit <= 0) return out;
        for (BlockPos p : ordered) {
            if (!erasable.test(p)) continue;
            out.add(p);
            if (out.size() >= limit) break;
        }
        return out;
    }

    /**
     * Never air, never bedrock (2026-09-29 owner: "all blocks should be destructable beside
     * bedrock"). {@code unbreakableToo} false is the first rule, which also spared every block with
     * a destroy speed below zero (barriers, command blocks, end portal frames).
     */
    public static boolean erasable(float destroySpeed, boolean isAir, boolean isBedrock,
                                   boolean unbreakableToo) {
        if (isAir || isBedrock) return false;
        return unbreakableToo || destroySpeed >= 0.0f;
    }

    /**
     * Whether a structure's blocks are spared (2026-09-29 owner: "not to destroy structures of dmz"):
     * every DragonMineZ worldgen structure - Kami's Lookout, Goku's house, the Cell arena and the
     * rest - while {@code /xenoset hakaiSpareDmzStructures} is on.
     */
    public static boolean sparesStructure(net.minecraft.resources.ResourceLocation id, boolean spareDmz) {
        return spareDmz && id != null && "dragonminez".equals(id.getNamespace());
    }

    /**
     * Whether a whole dimension is off limits to Hakai's block erasure ({@code /xenoset
     * hakaiSparedDimensions}, a comma list of dimension ids). DragonMineZ's Otherworld, Time Chamber
     * and Sacred Kai planet are placed builds - King Yemma's palace, Snake Way - that the worldgen
     * structure check cannot see (2026-09-29 owner: "why can i destroy the otherworld????").
     */
    public static boolean sparedDimension(String dimensionId, String list) {
        return listed(dimensionId, list);
    }

    /** Whether {@code id} is in a comma list of ids (case and spaces ignored). */
    public static boolean listed(String id, String list) {
        if (id == null || list == null || list.isBlank()) return false;
        for (String entry : list.split(",")) {
            if (entry.trim().equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** The {@code max} nearest, nearest first. */
    public static <T> List<T> capTargets(List<T> all, ToDoubleFunction<T> distance, int max) {
        List<T> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(distance));
        return sorted.size() > max ? new ArrayList<>(sorted.subList(0, Math.max(0, max))) : sorted;
    }
}
