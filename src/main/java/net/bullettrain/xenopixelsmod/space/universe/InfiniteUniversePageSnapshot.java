package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.Objects;

/** Compact occupancy response for a bounded logical Atlas page. Clients derive system details from the shared seed. */
public record InfiniteUniversePageSnapshot(long fieldSeed, boolean available, boolean warpAllowed,
                                           long originX, long originZ, int width, int height,
                                           byte[] occupied, byte[] revealed) {
    public InfiniteUniversePageSnapshot {
        var request = new InfiniteUniversePageRequest(originX, originZ, width, height);
        Objects.requireNonNull(request);
        int bytes = (width * height + 7) >>> 3;
        if (occupied == null || occupied.length != bytes)
            throw new IllegalArgumentException("logical page occupancy byte count mismatch");
        if (revealed == null || revealed.length != bytes)
            throw new IllegalArgumentException("logical page discovery byte count mismatch");
        occupied = occupied.clone();
        revealed = revealed.clone();
        int finalBits = (width * height) & 7;
        if (finalBits != 0 && (((occupied[bytes - 1] | revealed[bytes - 1]) & 0xff) >>> finalBits) != 0)
            throw new IllegalArgumentException("logical page bitset has data outside its window");
        for (int i = 0; i < bytes; i++)
            if ((revealed[i] & ~occupied[i]) != 0)
                throw new IllegalArgumentException("logical page cannot reveal an unoccupied cell");
        if (!available && (!allZero(occupied) || !allZero(revealed)))
            throw new IllegalArgumentException("unavailable logical page cannot contain systems or discoveries");
    }

    @Override public byte[] occupied() { return occupied.clone(); }
    @Override public byte[] revealed() { return revealed.clone(); }

    public boolean hasSystemAt(int index) {
        if (index < 0 || index >= width * height) return false;
        return (occupied[index >>> 3] & (1 << (index & 7))) != 0;
    }

    public boolean isRevealedAt(int index) {
        if (index < 0 || index >= width * height) return false;
        return (revealed[index >>> 3] & (1 << (index & 7))) != 0;
    }

    /** Returns this page with one already-occupied cell marked discovered, or this page if it is outside the window. */
    public InfiniteUniversePageSnapshot withRevealedCell(long x, long z) {
        if (!available || x < originX || z < originZ
                || x > originX + width - 1L || z > originZ + height - 1L)
            return this;
        int index = Math.toIntExact((x - originX) * height + (z - originZ));
        if (!hasSystemAt(index) || isRevealedAt(index)) return this;
        byte[] updated = revealed.clone();
        updated[index >>> 3] |= (byte) (1 << (index & 7));
        return new InfiniteUniversePageSnapshot(fieldSeed, true, warpAllowed, originX, originZ, width, height,
                occupied, updated);
    }

    public static InfiniteUniversePageSnapshot occupancy(long seed, boolean warpAllowed,
                                                          InfiniteUniverseField.Page page) {
        return occupancy(seed, warpAllowed, page, new byte[(page.queriedCells() + 7) >>> 3]);
    }

    public static InfiniteUniversePageSnapshot occupancy(long seed, boolean warpAllowed,
                                                          InfiniteUniverseField.Page page, byte[] revealed) {
        byte[] bits = new byte[(page.queriedCells() + 7) >>> 3];
        for (var system : page.systems()) {
            int dx = Math.toIntExact(system.cell().x() - page.originX());
            int dz = Math.toIntExact(system.cell().z() - page.originZ());
            int index = dx * page.height() + dz;
            bits[index >>> 3] |= (byte) (1 << (index & 7));
        }
        return new InfiniteUniversePageSnapshot(seed, true, warpAllowed,
                page.originX(), page.originZ(), page.width(), page.height(), bits, revealed);
    }

    /** Builds the network response directly from the page job's accumulated bitset, without a final whole-page scan. */
    public static InfiniteUniversePageSnapshot occupancy(long seed, boolean warpAllowed,
                                                          InfiniteUniverseField.PageJob job) {
        return new InfiniteUniversePageSnapshot(seed, true, warpAllowed, job.originX(), job.originZ(),
                job.width(), job.height(), job.occupancyBits(), job.revealedBits());
    }

    /** An explicit unavailable response with a correctly sized empty occupancy bitset. */
    public static InfiniteUniversePageSnapshot unavailable(long seed, InfiniteUniversePageRequest request) {
        byte[] empty = new byte[(request.width() * request.height() + 7) >>> 3];
        return new InfiniteUniversePageSnapshot(seed, false, false, request.originX(), request.originZ(),
                request.width(), request.height(), empty, empty);
    }

    public static void encode(InfiniteUniversePageSnapshot packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.fieldSeed);
        buf.writeBoolean(packet.available);
        buf.writeBoolean(packet.warpAllowed);
        buf.writeLong(packet.originX);
        buf.writeLong(packet.originZ);
        buf.writeVarInt(packet.width);
        buf.writeVarInt(packet.height);
        buf.writeVarInt(packet.occupied.length);
        buf.writeBytes(packet.occupied);
        buf.writeVarInt(packet.revealed.length);
        buf.writeBytes(packet.revealed);
    }

    public static InfiniteUniversePageSnapshot decode(FriendlyByteBuf buf) {
        long seed = buf.readLong();
        boolean available = buf.readBoolean();
        boolean warp = buf.readBoolean();
        long originX = buf.readLong();
        long originZ = buf.readLong();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        InfiniteUniversePageRequest request;
        try {
            request = new InfiniteUniversePageRequest(originX, originZ, width, height);
        } catch (IllegalArgumentException invalidWindow) {
            throw new IllegalArgumentException("invalid logical page window at " + originX + "," + originZ
                    + " size=" + width + "x" + height, invalidWindow);
        }
        int bytes = buf.readVarInt();
        int expected = (request.width() * request.height() + 7) >>> 3;
        if (bytes != expected) throw new IllegalArgumentException("invalid logical page payload at " + originX + ","
                + originZ + " size=" + width + "x" + height + ": expected " + expected + " bytes, got " + bytes);
        byte[] occupied = new byte[expected];
        buf.readBytes(occupied);
        int revealedBytes = buf.readVarInt();
        if (revealedBytes != expected) throw new IllegalArgumentException("invalid logical page discovery payload at "
                + originX + "," + originZ + " size=" + width + "x" + height + ": expected " + expected
                + " bytes, got " + revealedBytes);
        byte[] revealed = new byte[expected];
        buf.readBytes(revealed);
        return new InfiniteUniversePageSnapshot(seed, available, warp, originX, originZ, width, height,
                occupied, revealed);
    }

    private static boolean allZero(byte[] data) {
        for (byte value : data) if (value != 0) return false;
        return true;
    }

    @Override public boolean equals(Object other) {
        return other instanceof InfiniteUniversePageSnapshot page && fieldSeed == page.fieldSeed
                && available == page.available && warpAllowed == page.warpAllowed && originX == page.originX
                && originZ == page.originZ && width == page.width && height == page.height
                && Arrays.equals(occupied, page.occupied) && Arrays.equals(revealed, page.revealed);
    }

    @Override public int hashCode() {
        int result = Objects.hash(fieldSeed, available, warpAllowed, originX, originZ, width, height);
        result = 31 * result + Arrays.hashCode(occupied);
        return 31 * result + Arrays.hashCode(revealed);
    }
}
