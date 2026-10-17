package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

/** Client request for one bounded logical Atlas window. */
public record InfiniteUniversePageRequest(long originX, long originZ, int width, int height) {
    public static final int VIEWPORT_EDGE = 64;

    public InfiniteUniversePageRequest {
        if (width <= 0 || height <= 0 || (long) width * height > InfiniteUniverseField.MAX_PAGE_CELLS)
            throw new IllegalArgumentException("logical page must query 1.." + InfiniteUniverseField.MAX_PAGE_CELLS + " cells");
        try {
            Math.addExact(originX, width - 1L);
            Math.addExact(originZ, height - 1L);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("logical page exceeds signed-long coordinates", overflow);
        }
    }

    /** A bounded 64x64 Atlas page containing the requested signed-long logical cell. */
    public static InfiniteUniversePageRequest viewportAt(long cellX, long cellZ) {
        long originX = Math.multiplyExact(Math.floorDiv(cellX, VIEWPORT_EDGE), VIEWPORT_EDGE);
        long originZ = Math.multiplyExact(Math.floorDiv(cellZ, VIEWPORT_EDGE), VIEWPORT_EDGE);
        return new InfiniteUniversePageRequest(originX, originZ, VIEWPORT_EDGE, VIEWPORT_EDGE);
    }

    public static void encode(InfiniteUniversePageRequest packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.originX);
        buf.writeLong(packet.originZ);
        buf.writeVarInt(packet.width);
        buf.writeVarInt(packet.height);
    }

    public static InfiniteUniversePageRequest decode(FriendlyByteBuf buf) {
        return new InfiniteUniversePageRequest(buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt());
    }
}
