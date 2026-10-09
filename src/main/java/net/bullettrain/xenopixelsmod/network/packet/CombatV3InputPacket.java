package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3Input;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
import java.util.function.Supplier;

/** C2S intent only: no charge percentage, damage, movement or target entity id authority. */
public record CombatV3InputPacket(V3Input input, UUID session, UUID target, V3Direction direction, int sequence) {
    public CombatV3InputPacket {
        if (input == null || session == null || direction == null || sequence < 0) {
            throw new IllegalArgumentException("Invalid V3 input intent");
        }
    }
    public CombatV3InputPacket(FriendlyByteBuf buf) {
        this(V3Input.decode(buf.readVarInt()), buf.readUUID(), buf.readBoolean() ? buf.readUUID() : null,
                V3Direction.decode(buf.readVarInt()), buf.readVarInt());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(input.ordinal());
        buf.writeUUID(session);
        buf.writeBoolean(target != null);
        if (target != null) buf.writeUUID(target);
        buf.writeVarInt(direction.ordinal());
        buf.writeVarInt(sequence);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) V3CombatServer.handleInput(player, input, session, target, direction, sequence);
        });
        ctx.setPacketHandled(true);
    }
}
