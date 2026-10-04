package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** S2C server-confirmed quest completion details for the custom Xeno popup. */
public record QuestCompletionPopupPacket(String title, String description, String reward,
                                         String palette, String frame) {
    public QuestCompletionPopupPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(128), buf.readUtf(1024), buf.readUtf(256), buf.readUtf(8), buf.readUtf(16));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(trim(title, 128), 128);
        buf.writeUtf(trim(description, 1024), 1024);
        buf.writeUtf(trim(reward, 256), 256);
        buf.writeUtf(trim(palette, 8), 8);
        buf.writeUtf(trim(frame, 16), 16);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> net.bullettrain.xenopixelsmod.client.ClientPacketHandlers
                .handleQuestCompletionPopup(title, description, reward, palette, frame));
        ctx.setPacketHandled(true);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
