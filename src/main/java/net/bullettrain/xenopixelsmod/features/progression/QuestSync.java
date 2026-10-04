package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncQuestsPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncStandingsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends a player their own quest log.
 *
 * <p>Quest state is a server capability and quest definitions are a server-side reload listener.
 * The log screen runs on the client, so without this it shows nothing forever - which reads as a
 * broken screen rather than as data that has not arrived.
 *
 * <p>Per-player rather than broadcast: a quest log is the player's own business, and sending
 * everybody's to everybody would leak progress across a server.
 *
 * <p>{@link OnDatapackSyncEvent} covers the two moments a whole log needs resending - on join, and
 * after a {@code /reload} that may have redefined every quest's title and journal text. Changes
 * between those moments go through {@link #push(ServerPlayer)} at each mutation site.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class QuestSync {

    private QuestSync() {
    }

    /**
     * Sends this player their current active quests, and their faction standings with them.
     *
     * <p>Call after every quest state change. A log that is right only until the next kill is
     * worse than no log, because it looks authoritative.
     */
    public static void push(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, SyncQuestsPacket.forPlayer(player));
        // Standings ride the same push. The only thing that moves them today is a quest reward
        // paying faction points inside completeQuest - which is one of this method's callers - so
        // a separate trigger would fire at exactly the same moments and open a window where one
        // had arrived and the other had not.
        ModNetwork.sendToPlayer(player, SyncStandingsPacket.forPlayer(player));
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            push(joining);
            return;
        }
        // Null means a /reload rather than a join. Quest definitions may all have changed, so
        // every online player's log needs rebuilding - and this is precisely the case an operator
        // is exercising when they run the command.
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            push(player);
        }
    }
}
