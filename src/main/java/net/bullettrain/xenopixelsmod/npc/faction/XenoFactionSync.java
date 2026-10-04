package net.bullettrain.xenopixelsmod.npc.faction;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncFactionsPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncBanksPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the faction list to clients.
 *
 * <p>Factions are a datapack reload listener, so they exist only on the server. The editor runs on
 * the client and would otherwise show an empty faction list forever - which reads as a broken
 * screen rather than as data that has not arrived.
 *
 * <p>{@link OnDatapackSyncEvent} is the right hook rather than a login listener: it fires on join
 * <em>and</em> after every {@code /reload}, with the joining player when there is one and with null
 * for a reload that should reach everybody. So an operator who writes a faction and reloads sees it
 * without rejoining, which is how packs are actually iterated on.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoFactionSync {

    private XenoFactionSync() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncFactionsPacket factions = SyncFactionsPacket.current();
        // The store index rides the same hook. It is not datapack content, but it reaches a client
        // for the same reason and at the same two moments - on join, and after a reload an operator
        // ran - so giving it a second event would only create a window where one had arrived and
        // the other had not.
        SyncNpcStoreIndexPacket index = SyncNpcStoreIndexPacket.current();
        // Banks ride the same hook for the same reason, and at the same two moments. They are
        // world-store content rather than datapack content, but a client that has one list and not
        // the other is a client whose editor disagrees with itself.
        SyncBanksPacket banks = SyncBanksPacket.current();
        // Natural spawn rules ride the same hook for the same reason: store content the server holds
        // alone, which the editor cannot list until something pushes it.
        SyncNaturalSpawnsPacket spawns = SyncNaturalSpawnsPacket.current();

        ServerPlayer joining = event.getPlayer();
        if (joining != null) {
            ModNetwork.sendToPlayer(joining, factions);
            ModNetwork.sendToPlayer(joining, index);
            ModNetwork.sendToPlayer(joining, banks);
            ModNetwork.sendToPlayer(joining, spawns);
            return;
        }
        ModNetwork.sendToAll(factions);
        ModNetwork.sendToAll(index);
        ModNetwork.sendToAll(banks);
        ModNetwork.sendToAll(spawns);
    }
}
