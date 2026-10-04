package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * S2C: an NPC said something, show a speech bubble above it.
 *
 * <p>The bubble renderer is client-side, so without this packet only the player who clicked would
 * ever see a line - which is wrong on a server, where a conversation should be visible to everyone
 * nearby. The server picks the line and broadcasts to the entity's trackers, so every viewer sees
 * the same text at the same time and the client never decides what an NPC said.
 *
 * <p>Sent with {@code ModNetwork.sendToTrackingAndSelf}, so exactly the players who can see the NPC
 * get the bubble.
 *
 * <p>{@code palette} and {@code shape} override the NPC's own bubble for this one line - a script's
 * {@code npc.say(text, "gold", "shout")}. Blank and {@code INHERIT} (ordinal 0) keep the NPC's.
 */
public record XenoNpcSpeechPacket(int entityId, String text, int durationTicks, String palette,
                                  int shape) {

    /** Roughly three seconds, matching the bubble renderer's own default. */
    public static final int DEFAULT_TICKS = 60;

    /** Text is capped so a datapack cannot push an unbounded string at every nearby client. */
    private static final int MAX_TEXT = 256;

    /** Palette names are short; blank means the NPC's own bubble palette. */
    private static final int MAX_PALETTE = 16;

    /** A line in the NPC's own palette and shape. */
    public XenoNpcSpeechPacket(int entityId, String text, int durationTicks) {
        this(entityId, text, durationTicks, "", 0);
    }

    public XenoNpcSpeechPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readUtf(MAX_PALETTE),
                buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(text == null ? "" : text, MAX_TEXT);
        buf.writeVarInt(Math.max(1, durationTicks));
        String p = palette == null ? "" : palette;
        buf.writeUtf(p.length() > MAX_PALETTE ? p.substring(0, MAX_PALETTE) : p, MAX_PALETTE);
        buf.writeVarInt(Math.max(0, shape));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientPacketHandlers.handleNpcSpeech(entityId, text, durationTicks,
                palette, shape));
        ctx.setPacketHandled(true);
    }
}
