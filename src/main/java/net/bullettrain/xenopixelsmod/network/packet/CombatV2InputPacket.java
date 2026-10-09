package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.v2.V2CombatServer;
import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;
import net.bullettrain.xenopixelsmod.combat.v2.V2Input;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * C2S: one v2 combat input.
 *
 * <p>Intent only. It names what was pressed, which entity the client was aiming at, which
 * direction was held and how long an attack was charged; the server decides everything else and
 * re-validates even these (the target must exist, be alive and be in range; the charge is
 * clamped). An ordinal this build does not know is dropped.
 */
public record CombatV2InputPacket(int input, int targetId, int direction, int chargePercent) {

    public CombatV2InputPacket(V2Input input, int targetId, V2Direction direction, int chargePercent) {
        this(input.ordinal(), targetId, direction == null ? 0 : direction.ordinal(),
                Math.max(0, Math.min(100, chargePercent)));
    }

    public CombatV2InputPacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readVarInt(), buf.readByte(), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(input);
        buf.writeVarInt(targetId);
        buf.writeByte(direction);
        buf.writeByte(chargePercent);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            V2Input decoded = V2Input.byOrdinal(input);
            if (player == null || decoded == null) return;
            V2CombatServer.handleInput(player, decoded, targetId,
                    V2Direction.byOrdinal(direction), Math.max(0, Math.min(100, chargePercent)));
        });
        ctx.setPacketHandled(true);
    }
}
