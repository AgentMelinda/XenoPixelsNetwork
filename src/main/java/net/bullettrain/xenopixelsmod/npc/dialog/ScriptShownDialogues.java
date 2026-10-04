package net.bullettrain.xenopixelsmod.npc.dialog;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversations a script opened (XenoAPI {@code IPlayer.showDialog}, {@code ICustomNpc} dialog
 * calls), rather than a player clicking an NPC.
 *
 * <p>The option packet re-reads the tree on the server and trusts only the option index. For a
 * clicked NPC that tree is the NPC's own; for a script-shown one there is no NPC to ask, so the
 * tree the script sent is remembered here, per player, against the entity id the screen was opened
 * with. The next conversation the player opens replaces it, and logging out forgets it.
 */
public final class ScriptShownDialogues {
    /** The entity the screen is anchored on, the tree it shows, and the store ref it came from. */
    public record Shown(int hostEntityId, XenoDialogue dialogue, String ref) {}

    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

    private ScriptShownDialogues() {}

    /**
     * Opens {@code dialogue} for {@code player}, anchored on {@code hostEntityId}, and remembers it
     * so the options the player picks run against this tree.
     */
    public static void show(ServerPlayer player, int hostEntityId, String name, XenoDialogue dialogue, String ref) {
        if (player == null || dialogue == null) return;
        SHOWN.put(player.getUUID(), new Shown(hostEntityId, dialogue, ref == null ? "" : ref));
        ModNetwork.sendToPlayer(player, new OpenXenoNpcDialoguePacket(hostEntityId, ref == null ? "" : ref,
                name == null ? "" : name, dialogue));
    }

    /** The script-shown conversation this player has open on {@code hostEntityId}, or null. */
    public static Shown active(ServerPlayer player, int hostEntityId) {
        Shown shown = player == null ? null : SHOWN.get(player.getUUID());
        return shown != null && shown.hostEntityId() == hostEntityId ? shown : null;
    }

    /** An ordinary conversation replaced any script-shown one. */
    public static void clear(ServerPlayer player) {
        if (player != null) SHOWN.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        if (player != null) SHOWN.remove(player);
    }
}
