package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C: open a conversation with an NPC.
 *
 * <p>Carries the dialogue itself, not just its id. It used to send only the id on the reasoning
 * that "both sides load the same datapack" - which is not true. Datapacks are server data:
 * {@code XenoDialogues} registers through {@link
 * net.neoforged.neoforge.event.AddReloadListenerEvent}, which fires on the server only, so on a
 * dedicated server a client's copy is empty forever and the lookup returned null for every
 * conversation. It appeared to work solely in single-player, where the integrated server shares the
 * JVM and therefore the static map. The same trap the mark icons, the bubble palettes and the
 * faction list each hit.
 *
 * <p>Sending the tree does not weaken the server's authority: choosing an option still goes back as
 * its original <em>server index</em> through {@link XenoNpcDialoguePacket}, which re-reads the
 * dialogue from the server's own data before acting on it. The copy sent here is presentation -
 * text to draw and which node a text option walks to - so a tampered client can only lie to itself
 * about what the bubbles say.
 *
 * <p>Everything is bounded on read as well as on write, because a payload sized by data decides how
 * much the receiver allocates.
 */
public record OpenXenoNpcDialoguePacket(int entityId, String dialogueId, String npcName,
                                        XenoDialogue dialogue) {

    private static final int MAX_ID = 256;
    private static final int MAX_NAME = 64;

    /** A conversation larger than this is a datapack problem, not a payload to carry. */
    private static final int MAX_NODES = 128;
    private static final int MAX_OPTIONS = 16;
    private static final int MAX_NODE_ID = 128;
    private static final int MAX_TEXT = 512;
    private static final int MAX_OPTION_TEXT = 256;
    private static final int MAX_COMMAND = 256;
    private static final int MAX_PALETTE = 16;

    public OpenXenoNpcDialoguePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(MAX_ID), buf.readUtf(MAX_NAME), readDialogue(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(dialogueId == null ? "" : dialogueId, MAX_ID);
        buf.writeUtf(npcName == null ? "" : npcName, MAX_NAME);
        writeDialogue(buf, dialogue);
    }

    private static void writeDialogue(FriendlyByteBuf buf, XenoDialogue dialogue) {
        if (dialogue == null) {
            buf.writeUtf("", MAX_NODE_ID);
            buf.writeVarInt(0);
            return;
        }
        buf.writeUtf(dialogue.start() == null ? "" : dialogue.start(), MAX_NODE_ID);

        List<Map.Entry<String, XenoDialogue.Node>> nodes =
                new ArrayList<>(dialogue.nodes().entrySet());
        int count = Math.min(MAX_NODES, nodes.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Map.Entry<String, XenoDialogue.Node> entry = nodes.get(i);
            buf.writeUtf(entry.getKey(), MAX_NODE_ID);
            XenoDialogue.Node node = entry.getValue();
            buf.writeUtf(clamp(node.text(), MAX_TEXT), MAX_TEXT);
            buf.writeUtf(clamp(node.palette(), MAX_PALETTE), MAX_PALETTE);

            int options = Math.min(MAX_OPTIONS, node.options().size());
            buf.writeVarInt(options);
            for (int o = 0; o < options; o++) {
                XenoDialogue.Option option = node.options().get(o);
                buf.writeUtf(clamp(option.text(), MAX_OPTION_TEXT), MAX_OPTION_TEXT);
                buf.writeEnum(option.type() == null ? XenoDialogue.OptionType.QUIT
                        : option.type());
                buf.writeUtf(clamp(option.target(), MAX_NODE_ID), MAX_NODE_ID);
                buf.writeUtf(clamp(option.quest(), MAX_ID), MAX_ID);
                buf.writeUtf(clamp(option.command(), MAX_COMMAND), MAX_COMMAND);
                buf.writeVarInt(option.sourceIndex());
                buf.writeUtf(clamp(option.palette(), MAX_PALETTE), MAX_PALETTE);
            }
        }
    }

    private static XenoDialogue readDialogue(FriendlyByteBuf buf) {
        String start = buf.readUtf(MAX_NODE_ID);
        int count = Math.min(MAX_NODES, buf.readVarInt());
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_NODE_ID);
            String text = buf.readUtf(MAX_TEXT);
            String palette = buf.readUtf(MAX_PALETTE);

            int optionCount = Math.min(MAX_OPTIONS, buf.readVarInt());
            List<XenoDialogue.Option> options = new ArrayList<>(optionCount);
            for (int o = 0; o < optionCount; o++) {
                options.add(new XenoDialogue.Option(
                        buf.readUtf(MAX_OPTION_TEXT),
                        buf.readEnum(XenoDialogue.OptionType.class),
                        buf.readUtf(MAX_NODE_ID),
                        buf.readUtf(MAX_ID),
                        buf.readUtf(MAX_COMMAND),
                        buf.readVarInt(),
                        buf.readUtf(MAX_PALETTE)));
            }
            nodes.put(id, new XenoDialogue.Node(text, List.copyOf(options), palette));
        }
        if (nodes.isEmpty() || !nodes.containsKey(start)) {
            // Nothing to show. The handler treats null as "do not open", which is better than an
            // empty screen that reads as the NPC having nothing to say.
            return null;
        }
        return new XenoDialogue(start, java.util.Collections.unmodifiableMap(nodes));
    }

    private static String clamp(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(
                () -> ClientPacketHandlers.openNpcDialogue(entityId, dialogue, npcName));
        ctx.setPacketHandled(true);
    }
}
