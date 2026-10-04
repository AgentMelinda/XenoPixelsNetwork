package net.bullettrain.xenopixelsmod.npc.transport;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A transporter's destination list, shown as a conversation.
 *
 * <p>Built on the dialogue bubbles rather than a screen of its own. They already float above the
 * NPC, already take a click without locking the mouse, and are already the thing a player expects
 * when an NPC has something to offer — a second, near-identical list widget would be a second
 * thing to keep working.
 *
 * <p>The menu is <b>built per player</b>, because which destinations exist is a per-player fact: a
 * {@code VISITED} one is not shown to somebody who has not found it. The client is sent only what
 * it may see, and {@code XenoNpcTravelPacket} checks the same rule again on the way back — the
 * list it was sent is a display, never a permission.
 */
public final class TransportMenu {

    /** The node the synthesised conversation opens on. */
    private static final String ROOT = "root";

    private TransportMenu() {
    }

    /**
     * Opens the list for one player.
     *
     * @return true when something was opened, so the caller does not also speak
     */
    public static boolean open(ServerPlayer player, XenoNpcEntity npc) {
        if (player == null || npc == null) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        Set<String> unlocked = net.bullettrain.xenopixelsmod.capability.XenoCapabilities
                .get(player).map(data -> data.unlockedTransports()).orElse(Set.of());

        // Resolved through the store rather than read off the NPC. A network an operator has
        // since deleted resolves to nothing, and the NPC talks instead of opening an empty menu.
        TransportNetwork network = TransportNetworks.get(profile.transportNetwork);
        List<TransportDestination> visible =
                network == null ? List.of() : network.visibleTo(unlocked);
        if (visible.isEmpty()) {
            // Nothing to offer. Falling through lets the NPC talk instead, which beats opening an
            // empty menu that reads as broken.
            return false;
        }
        // A synthesised id, not one from the store: this conversation exists only for this
        // player, for this moment, and nothing should be able to look it up again.
        ModNetwork.sendToPlayer(player, new OpenXenoNpcDialoguePacket(
                npc.getId(), "xeno:transport_menu", npc.getName().getString(), build(visible)));
        return true;
    }

    /**
     * One node, one option per destination.
     *
     * <p>Each option carries the destination's <em>id</em> as its target. Nothing about where that
     * is travels to the client: the coordinates stay on the server, so the worst a crafted reply
     * can ask for is a destination this NPC genuinely offers.
     */
    static XenoDialogue build(List<TransportDestination> visible) {
        List<XenoDialogue.Option> options = new ArrayList<>();
        for (TransportDestination destination : visible) {
            options.add(new XenoDialogue.Option(destination.name(),
                    XenoDialogue.OptionType.TRANSPORT, destination.id(), "", ""));
        }
        // A way out that is not "walk away": the bubbles also close on sneak, but an explicit
        // option is what a player looks for in a list.
        options.add(new XenoDialogue.Option("Never mind",
                XenoDialogue.OptionType.QUIT, "", "", ""));

        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put(ROOT, new XenoDialogue.Node("Where would you like to go?",
                List.copyOf(options)));
        return new XenoDialogue(ROOT, nodes);
    }
}
