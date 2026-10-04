package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import xenoapi.npcs.api.IPos;

import java.util.Objects;

/** An immutable block position as XenoAPI's {@link IPos}; every operation returns a new value. */
public final class XenoPosAdapter implements IPos {
    final BlockPos pos;

    public XenoPosAdapter(BlockPos pos) {
        this.pos = Objects.requireNonNull(pos).immutable();
    }

    private static BlockPos of(IPos pos) {
        if (pos == null) throw new IllegalArgumentException("IPos cannot be null");
        return pos instanceof XenoPosAdapter nativePos ? nativePos.pos : new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Direction direction(int direction) {
        if (direction < 0 || direction > 5) throw new IllegalArgumentException("Direction must be 0-5, got " + direction);
        return Direction.from3DDataValue(direction);
    }

    @Override public int getX() { return pos.getX(); }
    @Override public int getY() { return pos.getY(); }
    @Override public int getZ() { return pos.getZ(); }
    @Override public IPos up() { return new XenoPosAdapter(pos.above()); }
    @Override public IPos up(int n) { return new XenoPosAdapter(pos.above(n)); }
    @Override public IPos down() { return new XenoPosAdapter(pos.below()); }
    @Override public IPos down(int n) { return new XenoPosAdapter(pos.below(n)); }
    @Override public IPos north() { return new XenoPosAdapter(pos.north()); }
    @Override public IPos north(int n) { return new XenoPosAdapter(pos.north(n)); }
    @Override public IPos east() { return new XenoPosAdapter(pos.east()); }
    @Override public IPos east(int n) { return new XenoPosAdapter(pos.east(n)); }
    @Override public IPos south() { return new XenoPosAdapter(pos.south()); }
    @Override public IPos south(int n) { return new XenoPosAdapter(pos.south(n)); }
    @Override public IPos west() { return new XenoPosAdapter(pos.west()); }
    @Override public IPos west(int n) { return new XenoPosAdapter(pos.west(n)); }
    @Override public IPos add(int x, int y, int z) { return new XenoPosAdapter(pos.offset(x, y, z)); }
    @Override public IPos add(IPos other) { return new XenoPosAdapter(pos.offset(of(other))); }
    @Override public IPos subtract(int x, int y, int z) { return new XenoPosAdapter(pos.offset(-x, -y, -z)); }
    @Override public IPos subtract(IPos other) { return new XenoPosAdapter(pos.subtract(of(other))); }

    /** This position as a unit vector; the zero vector stays zero. */
    @Override
    public double[] normalize() {
        double length = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getY() * pos.getY()
                + (double) pos.getZ() * pos.getZ());
        if (length == 0) return new double[] {0, 0, 0};
        return new double[] {pos.getX() / length, pos.getY() / length, pos.getZ() / length};
    }

    /** Positions are immutable values, so the handle grants nothing beyond this adapter. */
    @Override public BlockPos getMCBlockPos() { return pos; }

    /** {@code direction}: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east. */
    @Override public IPos offset(int direction) { return new XenoPosAdapter(pos.relative(direction(direction))); }
    @Override public IPos offset(int direction, int n) { return new XenoPosAdapter(pos.relative(direction(direction), n)); }

    @Override
    public double distanceTo(IPos other) {
        return Math.sqrt(pos.distSqr(of(other)));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XenoPosAdapter that && that.pos.equals(pos);
    }

    @Override public int hashCode() { return pos.hashCode(); }
    @Override public String toString() { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
}
