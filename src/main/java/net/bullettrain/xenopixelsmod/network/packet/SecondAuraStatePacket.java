package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.aura.SecondAuraClientState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Tells every client tracking a player whether that player's second aura (the HD aura) is switched
 * on. The switch is the player's own ({@code /secondaura}, or Ki Actions in DragonMineZ's X menu)
 * and everyone sees the result, so it cannot live in a client config.
 */
public record SecondAuraStatePacket(int entityId, boolean on) {

    public SecondAuraStatePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(on);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> SecondAuraClientState.set(entityId, on));
        ctx.setPacketHandled(true);
    }
}
