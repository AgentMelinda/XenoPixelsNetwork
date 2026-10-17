package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** User-bindable toggle for the far small-planet LOD. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SmallPlanetLodKeys {
    public static final KeyMapping TOGGLE = new KeyMapping("key.xenopixelsmod.small_planet_lod",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.xenopixelsmod.space");

    private SmallPlanetLodKeys() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            XenoClientConfig.smallPlanetLod = !XenoClientConfig.smallPlanetLod;
            XenoClientConfig.save();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable(
                    "message.xenopixelsmod.small_planet_lod", XenoClientConfig.smallPlanetLod ? "ON" : "OFF"), true);
        }
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(TOGGLE); }
    }
}
