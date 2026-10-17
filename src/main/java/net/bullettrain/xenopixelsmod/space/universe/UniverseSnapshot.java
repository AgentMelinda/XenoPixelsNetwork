package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Clientbound known-universe description for the Galaxy Atlas (channel {@code xenopixelsmod:universe_snapshot_v3}): the
 * derived field seed and a bitset over the finite, world-border-safe universe grid. Keeping the bitset as the decoded
 * representation avoids allocating one {@link UniverseField.Cell} object per revealed system on client login.
 */
public final class UniverseSnapshot {
    public static final int MAX_CELLS = UniverseField.GRID_CELL_COUNT;
    private static final int BITSET_BYTES = (MAX_CELLS + 7) >>> 3;

    private final long fieldSeed;
    private final boolean warpAllowed;
    private final byte[] bits;
    private final int revealedCount;

    /** Builds an immutable packed snapshot from the server's or a caller's collection. */
    public UniverseSnapshot(long fieldSeed, boolean warpAllowed, Collection<UniverseField.Cell> revealed) {
        Objects.requireNonNull(revealed, "revealed");
        if (revealed.size() > MAX_CELLS) throw new IllegalArgumentException("invalid universe snapshot");
        this.fieldSeed = fieldSeed;
        this.warpAllowed = warpAllowed;
        this.bits = new byte[BITSET_BYTES];
        int count = 0;
        for (var cell : revealed) {
            int index = UniverseField.index(Objects.requireNonNull(cell, "cell"));
            int mask = 1 << (index & 7);
            int byteIndex = index >>> 3;
            if ((bits[byteIndex] & mask) != 0) throw new IllegalArgumentException("duplicate universe cell");
            bits[byteIndex] |= (byte) mask;
            count++;
        }
        this.revealedCount = count;
    }

    private UniverseSnapshot(long fieldSeed, boolean warpAllowed, byte[] packedBits) {
        if (packedBits.length != BITSET_BYTES) throw new IllegalArgumentException("invalid universe bitset size: " + packedBits.length);
        int finalBits = MAX_CELLS & 7;
        if (finalBits != 0 && ((packedBits[BITSET_BYTES - 1] & 0xff) >>> finalBits) != 0)
            throw new IllegalArgumentException("universe bitset has out-of-grid cells");
        this.fieldSeed = fieldSeed;
        this.warpAllowed = warpAllowed;
        // decode() is the sole caller and passes a newly allocated private array, so it can be owned directly.
        this.bits = packedBits;
        int count = 0;
        for (byte value : bits) count += Integer.bitCount(value & 0xff);
        this.revealedCount = count;
    }

    public long fieldSeed() { return fieldSeed; }
    public boolean warpAllowed() { return warpAllowed; }
    public int revealedCount() { return revealedCount; }

    /** Tests a row-major cell index without materializing the revealed-cell collection. */
    public boolean isRevealed(int index) {
        if (index < 0 || index >= MAX_CELLS) return false;
        return (bits[index >>> 3] & (1 << (index & 7))) != 0;
    }

    /** Visits set row-major indices in ascending order, without allocating per-cell objects. */
    public void forEachRevealedIndex(IntConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (int byteIndex = 0; byteIndex < bits.length; byteIndex++) {
            int value = bits[byteIndex] & 0xff;
            while (value != 0) {
                int bit = Integer.numberOfTrailingZeros(value);
                consumer.accept((byteIndex << 3) + bit);
                value &= value - 1;
            }
        }
    }

    /** Compatibility view for callers that actually need cell objects; the network/client hot path uses indices. */
    public List<UniverseField.Cell> revealed() {
        var cells = new ArrayList<UniverseField.Cell>(revealedCount);
        forEachRevealedIndex(index -> cells.add(cellAt(index)));
        return List.copyOf(cells);
    }

    public static UniverseField.Cell cellAt(int index) {
        if (index < 0 || index >= MAX_CELLS) throw new IllegalArgumentException("index outside universe grid: " + index);
        return new UniverseField.Cell(index / UniverseField.GRID_DIAMETER - UniverseField.MAX_CELL,
                index % UniverseField.GRID_DIAMETER - UniverseField.MAX_CELL);
    }

    public static void encode(UniverseSnapshot packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.fieldSeed);
        buf.writeBoolean(packet.warpAllowed);
        buf.writeVarInt(BITSET_BYTES);
        buf.writeBytes(packet.bits);
    }

    public static UniverseSnapshot decode(FriendlyByteBuf buf) {
        long seed = buf.readLong();
        boolean warp = buf.readBoolean();
        int byteCount = buf.readVarInt();
        if (byteCount != BITSET_BYTES) throw new IllegalArgumentException("invalid universe bitset size: " + byteCount);
        byte[] bits = new byte[BITSET_BYTES];
        buf.readBytes(bits);
        return new UniverseSnapshot(seed, warp, bits);
    }

    @Override public boolean equals(Object other) {
        return other instanceof UniverseSnapshot snapshot && fieldSeed == snapshot.fieldSeed
                && warpAllowed == snapshot.warpAllowed && java.util.Arrays.equals(bits, snapshot.bits);
    }

    @Override public int hashCode() {
        int result = Long.hashCode(fieldSeed);
        result = 31 * result + Boolean.hashCode(warpAllowed);
        result = 31 * result + java.util.Arrays.hashCode(bits);
        return result;
    }

    @Override public String toString() {
        return "UniverseSnapshot[fieldSeed=" + fieldSeed + ", warpAllowed=" + warpAllowed
                + ", revealedCount=" + revealedCount + "]";
    }
}
