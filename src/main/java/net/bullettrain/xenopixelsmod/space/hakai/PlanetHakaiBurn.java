package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Clientbound: a charted body started (or stopped) burning in planet Hakai (channel {@code xenopixelsmod:planet_hakai_burn_v2}).
 * The client cuts the body's HD sphere outward from {@code impactX/Y/Z} over {@code durationTicks}, with a violet rim at the cut.
 */
public record PlanetHakaiBurn(String bodyId, int durationTicks, boolean active, float impactX, float impactY, float impactZ) {
    public PlanetHakaiBurn {
        if (bodyId == null || bodyId.isEmpty() || bodyId.length() > 256 || durationTicks < 1 || durationTicks > 20 * 60 * 10)
            throw new IllegalArgumentException("invalid planet hakai burn");
        float length = (float) Math.sqrt(impactX * impactX + impactY * impactY + impactZ * impactZ);
        if (!Float.isFinite(length) || length < 1.0e-5f) { impactX = 0; impactY = 1; impactZ = 0; }
        else { impactX /= length; impactY /= length; impactZ /= length; }
    }

    public PlanetHakaiBurn(String bodyId, int durationTicks, boolean active) {
        this(bodyId, durationTicks, active, 0, 1, 0);
    }

    public static void encode(PlanetHakaiBurn packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.bodyId, 256); buf.writeVarInt(packet.durationTicks); buf.writeBoolean(packet.active);
        buf.writeFloat(packet.impactX); buf.writeFloat(packet.impactY); buf.writeFloat(packet.impactZ);
    }

    public static PlanetHakaiBurn decode(FriendlyByteBuf buf) {
        return new PlanetHakaiBurn(buf.readUtf(256), buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
