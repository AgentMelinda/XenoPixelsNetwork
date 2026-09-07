package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Server → nearby clients: a fighter left a copy of themselves standing here.
 *
 * <p>Carries the owner's entity id so each client can draw that fighter's real appearance — DMZ
 * hair, active form, aura and all — rather than a generic body. {@code CombatFxKind} could not
 * express this: its payload is a position and a kind, with no owner.
 */
public record AfterimageGhostPacket(int ownerId, double x, double y, double z,
                                    float yaw, float pitch, int lifetimeTicks) {

    public AfterimageGhostPacket(int ownerId, Vec3 pos, float yaw, float pitch, int lifetimeTicks) {
        this(ownerId, pos.x, pos.y, pos.z, yaw, pitch, lifetimeTicks);
    }

    public AfterimageGhostPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ownerId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(lifetimeTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> net.bullettrain.xenopixelsmod.client.combat.AfterimageGhostRenderer.add(
                ownerId, new Vec3(x, y, z), yaw, pitch, lifetimeTicks));
        ctx.setPacketHandled(true);
    }
}
