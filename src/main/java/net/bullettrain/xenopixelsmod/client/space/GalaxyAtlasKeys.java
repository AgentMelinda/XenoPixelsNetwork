package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.screen.GalaxyAtlasScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * "Open Galaxy Atlas" key. Ships UNBOUND (keybind rule: audit vanilla/DMZ/Xeno bindings first, prefer unbound); players
 * pick a key in Controls.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class GalaxyAtlasKeys {
    public static final KeyMapping OPEN_ATLAS = new KeyMapping("key.xenopixelsmod.galaxy_atlas",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.xenopixelsmod.space");

    private GalaxyAtlasKeys() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_ATLAS.consumeClick())
            if (mc.player != null && mc.screen == null) mc.setScreen(new GalaxyAtlasScreen());
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN_ATLAS); }
    }
}
