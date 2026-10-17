package net.bullettrain.xenopixelsmod.space.blackhole;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound escape meter (channel {@code xenopixelsmod:black_hole_status_v1}); empty id = no hole near. */
public record BlackHoleStatus(String holeId, float escape) {
    public static final BlackHoleStatus NONE = new BlackHoleStatus("", 1f);

    public BlackHoleStatus {
        if (holeId == null || holeId.length() > 256 || !Float.isFinite(escape)) throw new IllegalArgumentException("invalid black hole status");
        escape = Math.max(0f, Math.min(1f, escape));
    }

    public boolean active() { return !holeId.isEmpty(); }

    public static void encode(BlackHoleStatus packet, FriendlyByteBuf buf) { buf.writeUtf(packet.holeId, 256); buf.writeFloat(packet.escape); }

    public static BlackHoleStatus decode(FriendlyByteBuf buf) { return new BlackHoleStatus(buf.readUtf(256), buf.readFloat()); }
}
