package net.bullettrain.xenopixelsmod.npc.path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A route one NPC walks, as an ordered list of block positions.
 *
 * <p>My NPCs calls this the Moving Path, and its own note is the whole brief: <em>path data is
 * consumed by NPC movement AI</em>. So this is deliberately only the data — walking it is
 * {@link NpcPathWalker}'s job, and the two ship together rather than leaving a list nothing reads.
 *
 * <p><b>Per-NPC, not shared.</b> The rule in {@code docs/xeno-npc-schema.md} §0 asks whether two
 * NPCs would want to share a fact. A patrol route is arguable — two guards could walk one wall —
 * but a route is normally drawn around where one particular NPC stands, and My NPCs stores it on
 * the NPC. This is the second deliberate counter-example to that rule, after ambient lines.
 *
 * <p>Points are block positions rather than doubles because the tool places them by clicking a
 * block, and a block is the resolution that actually matters to a walking mob.
 */
public final class NpcPath {

    /**
     * How many points one route may hold.
     *
     * <p>This rides an entity's NBT, which is written to disk and sent on spawn, so it is bounded
     * like every other list on the profile. Thirty-two is a long patrol — the reference's own tool
     * is a scroll list, and a route needing more than this wants two NPCs.
     */
    public static final int MAX_POINTS = 32;

    /** Slowest and fastest the NPC may walk its route. 1.0 is the navigation's ordinary speed. */
    public static final double MIN_SPEED = 0.1;
    public static final double MAX_SPEED = 2.0;
    public static final double DEFAULT_SPEED = 1.0;

    private static final String TAG_POINTS = "Points";
    private static final String TAG_MODE = "Mode";
    private static final String TAG_SPEED = "Speed";
    private static final String TAG_PAUSES = "Pauses";

    /** What happens when the NPC reaches the last point. */
    public enum Mode {
        /** Back to the first point and round again. The ordinary patrol. */
        LOOP,
        /** Walk back down the list. For a route that is a line rather than a circuit. */
        PING_PONG,
        /** Stop there. For a one-off walk to a post. */
        ONCE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Unknown ids fold to {@link #LOOP} rather than throwing, so an old save still loads. */
        public static Mode byId(String raw) {
            if (raw != null) {
                String id = raw.trim().toUpperCase(Locale.ROOT);
                for (Mode mode : values()) {
                    if (mode.name().equals(id)) {
                        return mode;
                    }
                }
            }
            return LOOP;
        }

        public Mode next() {
            Mode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    /** One stop on the route. */
    public record Point(int x, int y, int z) {

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", x);
            tag.putInt("Y", y);
            tag.putInt("Z", z);
            return tag;
        }

        public static Point load(CompoundTag tag) {
            return tag == null ? new Point(0, 0, 0)
                    : new Point(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }

        @Override
        public String toString() {
            return x + ", " + y + ", " + z;
        }
    }

    private final List<Point> points = new ArrayList<>();
    private Mode mode = Mode.LOOP;
    private double speed = DEFAULT_SPEED;
    private boolean pauses;

    public boolean pauses() { return pauses; }

    public void setPauses(boolean value) { pauses = value; }

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    public int size() {
        return points.size();
    }

    /**
     * Whether this route is worth walking.
     *
     * <p>A single point is a place to stand, not a route, and treating it as one would have the
     * NPC endlessly re-path to where it already is.
     */
    public boolean walkable() {
        return points.size() >= 2;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode replacement) {
        mode = replacement == null ? Mode.LOOP : replacement;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double value) {
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
    }

    /** One point, or null for an index the route does not have. */
    public Point get(int index) {
        return index >= 0 && index < points.size() ? points.get(index) : null;
    }

    /** @return false when the route is already full */
    public boolean add(Point point) {
        if (point == null || points.size() >= MAX_POINTS) {
            return false;
        }
        points.add(point);
        return true;
    }

    public void remove(int index) {
        if (index >= 0 && index < points.size()) {
            points.remove(index);
        }
    }

    /**
     * Swaps a point with its neighbour — the reference's Up and Down controls.
     *
     * <p>Out of range does nothing rather than throwing: this is driven by a button, and the ends
     * of the list are exactly where a player will press it.
     */
    public void swap(int index, int with) {
        if (index < 0 || with < 0 || index >= points.size() || with >= points.size()) {
            return;
        }
        Collections.swap(points, index, with);
    }

    public void clear() {
        points.clear();
    }

    /**
     * The index after {@code current}, given the mode.
     *
     * <p>{@code forward} is which way a {@link Mode#PING_PONG} route is currently travelling; for
     * the other modes it is ignored. Returns -1 when a {@link Mode#ONCE} route is finished, which
     * is how the walker knows to stop.
     */
    public int next(int current, boolean forward) {
        if (points.size() < 2) {
            return -1;
        }
        int last = points.size() - 1;
        return switch (mode) {
            case LOOP -> current >= last ? 0 : current + 1;
            case ONCE -> current >= last ? -1 : current + 1;
            case PING_PONG -> {
                if (forward) {
                    yield current >= last ? Math.max(0, last - 1) : current + 1;
                }
                yield current <= 0 ? Math.min(last, 1) : current - 1;
            }
        };
    }

    /**
     * Whether a ping-pong route is still travelling forward after stepping off {@code current}.
     *
     * <p>Separate from {@link #next} because the walker has to store the direction between ticks,
     * and deriving it from the index alone is impossible at the turning points.
     */
    public boolean nextForward(int current, boolean forward) {
        if (mode != Mode.PING_PONG || points.size() < 2) {
            return true;
        }
        if (forward && current >= points.size() - 1) {
            return false;
        }
        if (!forward && current <= 0) {
            return true;
        }
        return forward;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Point point : points) {
            list.add(point.save());
        }
        tag.put(TAG_POINTS, list);
        tag.putString(TAG_MODE, mode.id());
        tag.putDouble(TAG_SPEED, speed);
        tag.putBoolean(TAG_PAUSES, pauses);
        return tag;
    }

    public static NpcPath load(CompoundTag tag) {
        NpcPath path = new NpcPath();
        if (tag == null) {
            return path;
        }
        path.setMode(Mode.byId(tag.getString(TAG_MODE)));
        path.setPauses(tag.getBoolean(TAG_PAUSES));
        path.setSpeed(tag.contains(TAG_SPEED) ? tag.getDouble(TAG_SPEED) : DEFAULT_SPEED);
        ListTag list = tag.getList(TAG_POINTS, Tag.TAG_COMPOUND);
        // Truncated rather than trusted: a hand-edited or corrupted tag claiming a thousand points
        // must not become a thousand points in memory.
        for (int i = 0; i < Math.min(MAX_POINTS, list.size()); i++) {
            path.points.add(Point.load(list.getCompound(i)));
        }
        return path;
    }

    /** A deep copy, for the editor's working draft. */
    public NpcPath copy() {
        return load(save());
    }
}
