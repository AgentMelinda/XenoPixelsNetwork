package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.GuidanceV2Network;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class GuidanceV2Events {
    private GuidanceV2Events() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GuidanceConfig.load();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GuidanceV2Network.sendVersionTo(player);
        }
    }
}
