package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Outbound handoff notice carrying the destination-frame pose needed for optional camera coasting. */
public record HandoffTransitionNoticeV4(int overlayMillis, boolean plasmaShell,
                                        ResourceLocation destinationDimension,
                                        Vec3 destinationPosition, Vec3 velocityPerTick) {
    public HandoffTransitionNoticeV4 {
        if (overlayMillis < 0 || overlayMillis > 60_000)
            throw new IllegalArgumentException("overlayMillis outside [0,60000]");
        if (destinationDimension == null || !finite(destinationPosition) || !finite(velocityPerTick))
            throw new IllegalArgumentException("handoff pose and velocity must be finite");
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static void encode(HandoffTransitionNoticeV4 packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.overlayMillis());
        buffer.writeBoolean(packet.plasmaShell());
        buffer.writeResourceLocation(packet.destinationDimension());
        writeVec3(buffer, packet.destinationPosition());
        writeVec3(buffer, packet.velocityPerTick());
    }

    public static HandoffTransitionNoticeV4 decode(FriendlyByteBuf buffer) {
        return new HandoffTransitionNoticeV4(buffer.readVarInt(), buffer.readBoolean(),
                buffer.readResourceLocation(), readVec3(buffer), readVec3(buffer));
    }

    private static void writeVec3(FriendlyByteBuf buffer, Vec3 vector) {
        buffer.writeDouble(vector.x);
        buffer.writeDouble(vector.y);
        buffer.writeDouble(vector.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
