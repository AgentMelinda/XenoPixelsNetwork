package net.bullettrain.xenopixels.example;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = ApiExampleAddon.MOD_ID, value = Dist.CLIENT)
public final class ApiExampleClientEvents {
    private ApiExampleClientEvents() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        AddonNetwork.sendToServer(new ApiExampleAddon.ExamplePingPacket(System.nanoTime()));
    }
}
