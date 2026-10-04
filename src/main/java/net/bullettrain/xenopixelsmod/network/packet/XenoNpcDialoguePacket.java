package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueText;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogues;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRoleDefinitions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * C2S: a player chose a dialogue option that has a consequence.
 *
 * <p>Walking between text nodes stays on the client - that is presentation. This packet carries
 * only the choices that do something, and it carries <em>which</em> option was picked rather than
 * what it should do: the server re-reads the same dialogue from its own datapack and decides. A
 * client that lies about the option index gets a different option or nothing, never an arbitrary
 * command.
 *
 * <p>Commands are off unless an operator enables them, because a datapack dialogue running commands
 * on a player's behalf is a real escalation path.
 */
public record XenoNpcDialoguePacket(int entityId, String nodeId, int optionIndex) {

    private static final int MAX_NODE_ID = 128;

    /** Same reach as every other NPC packet. */
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    public XenoNpcDialoguePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(MAX_NODE_ID), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(nodeId == null ? "" : nodeId, MAX_NODE_ID);
        buf.writeVarInt(optionIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // A conversation a script opened is answered from the tree the script sent, which the
            // server remembered; the client still only supplies the index.
            var shown = net.bullettrain.xenopixelsmod.npc.dialog.ScriptShownDialogues.active(player, entityId);
            var host = player.level().getEntity(entityId);
            if (host == null || player.distanceToSqr(host) > MAX_DISTANCE_SQ) {
                return;
            }
            XenoNpcEntity npc = host instanceof XenoNpcEntity xeno ? xeno : null;
            if (shown == null && npc == null) {
                return;
            }

            // Re-read from the server's own data rather than trusting anything but the index.
            XenoDialogue dialogue = shown != null ? shown.dialogue() : dialogueFor(npc);
            if (dialogue == null) {
                return;
            }
            XenoDialogue.Node node = dialogue.nodes().get(nodeId);
            if (node == null || optionIndex < 0 || optionIndex >= node.options().size()) {
                return;
            }
            XenoDialogue.Option option = node.options().get(optionIndex);
            if (npc != null) {
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fire(npc, "dialogOption", player, null, null, optionIndex);
            }

            switch (option.type()) {
                case COMMAND -> runCommand(player, host, option.command());
                case QUEST -> offerQuest(player, npc, option.quest());
                case TEXT -> net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents
                        .onDialogOption(player, option.target());
                default -> {
                    // TEXT is navigation and is resolved on the client; QUIT closes there too.
                    // ROLE stays inert - there are no economy roles yet, and the screen shows it
                    // disabled, so reaching here means a crafted packet.
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Resolves the same dialogue the player is looking at.
     *
     * <p>Delegates rather than repeating the role lookup: an NPC's own dialogue now wins over its
     * role's, and if this side resolved it differently from the side that opened the conversation,
     * choosing option 2 would run option 2 of a different tree.
     */
    private static XenoDialogue dialogueFor(XenoNpcEntity npc) {
        return XenoNpcEntity.dialogueFor(npc);
    }

    /**
     * Offers a quest from a dialogue option, or reports why it cannot be taken.
     *
     * <p>Routed through {@link ParallelQuests#start} rather than starting the quest here, so an
     * NPC hands out a quest by exactly the same path {@code /xenoquest start} uses - one set of
     * rules about already being on a quest, and one place that knows what the ids are.
     *
     * <p>Every outcome is reported. A dialogue option that looks like it did nothing is the thing
     * this whole screen is written to avoid.
     */
    private static void offerQuest(ServerPlayer player, XenoNpcEntity giver, String questId) {
        if (questId == null || questId.isBlank()) {
            player.sendSystemMessage(Component.literal(
                    "That option names no quest."));
            return;
        }
        if (!net.bullettrain.xenopixelsmod.features.progression.QuestDialogueFilter.canOffer(
                player, net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player).orElse(null),
                questId)) {
            player.sendSystemMessage(Component.literal("You cannot take that quest yet."));
            return;
        }
        String refusal = ParallelQuests.start(player, questId, giver);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal));
        }
    }

    private static void runCommand(ServerPlayer player, net.minecraft.world.entity.Entity npc, String command) {
        if (!XenoServerConfig.xenoNpcDialogueCommands) {
            player.sendSystemMessage(Component.literal(
                    "Dialogue commands are disabled on this server."));
            return;
        }
        String resolved = XenoDialogueText.resolveCommand(command, player.getName().getString());
        if (resolved.isBlank()) {
            return;
        }
        // Run as the NPC at server permission, not as the player: the dialogue is server-authored
        // content, and a player should not gain their own permission level from talking to an NPC.
        CommandSourceStack source = npc.createCommandSourceStack()
                .withPermission(2)
                .withSuppressedOutput();
        player.server.getCommands().performPrefixedCommand(source, resolved);
    }
}
