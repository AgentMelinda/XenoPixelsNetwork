package net.bullettrain.xenopixelsmod.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.HudPartsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HudPartsEvents {
    private HudPartsEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HudPartsStore.load();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HudPartsNetwork.sendTo(player);
        }
    }
}
