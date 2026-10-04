package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Forgets the quest log and the player's standings on disconnect.
 *
 * <p>{@link ClientQuests} and {@link ClientStandings} are static and outlive a world. Without this,
 * leaving world A and joining world B shows world A's quests until the new sync lands - and if the
 * new world has none, shows them indefinitely.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientQuestReset {

    private ClientQuestReset() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuests.clear();
        ClientStandings.clear();
    }
}
