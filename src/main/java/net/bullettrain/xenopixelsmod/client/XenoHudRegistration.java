package net.bullettrain.xenopixelsmod.client;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class XenoHudRegistration {
    /** Last DMZ overlay in their registration order — we draw above all of them. */
    private static final ResourceLocation DMZ_TOP = ResourceLocation.fromNamespaceAndPath("dragonminez", "beam_clash_hud");

    private XenoHudRegistration() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_hud"), new XenoHudOverlay()::render);
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_technique_hotbar"), new XenoTechniqueHotbarOverlay()::render);
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_party_hud"), new XenoPartyOverlay()::render);
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_cooldown_hud"), new XenoCooldownHudOverlay()::render);
    }
}
