package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.XenoNpcScriptScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * S2C: the answer to one {@link NpcScriptPacket}.
 *
 * <p>Either a stored entry to edit ({@code payload} non-null, {@code ran} false) or the result of a
 * run ({@code ran} true, {@code status} and {@code output} filled). One packet for both because the
 * screen that asked cannot tell them apart in advance - it just waits for the reply to its request.
 *
 * <p>Delivered to whichever script screen is open, the way {@link SyncNpcStoreIndexPacket} delivers
 * itself to the NPC editor. Routing through a client-side handler instead would mean mutable state
 * that outlives the screen, and the request is only interesting while that screen is up.
 */
public record NpcScriptResultPacket(String id, String status, String output, CompoundTag payload,
                                    boolean ran, boolean ok) {

    private static final int MAX_ID = 64;
    private static final int MAX_STATUS = 256;
    /**
     * Room for a stack trace, which is the most useful thing a failing script can print. Bounded
     * because the text comes from a script an operator wrote, and an unbounded string on the wire is
     * a memory problem for every client, not just the one that asked.
     */
    private static final int MAX_OUTPUT = 4096;

    public NpcScriptResultPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_ID), buf.readUtf(MAX_STATUS), buf.readUtf(MAX_OUTPUT),
                buf.readNbt(), buf.readBoolean(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id == null ? "" : id, MAX_ID);
        buf.writeUtf(cut(status, MAX_STATUS), MAX_STATUS);
        buf.writeUtf(cut(output, MAX_OUTPUT), MAX_OUTPUT);
        buf.writeNbt(payload);
        buf.writeBoolean(ran);
        buf.writeBoolean(ok);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof XenoNpcScriptScreen screen) {
                screen.receive(id, status, output, payload, ran, ok);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
