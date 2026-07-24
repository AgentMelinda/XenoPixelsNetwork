package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class XenoHudRegistration {
    /** Last DMZ overlay in their registration order — we draw above all of them. */
    private static final ResourceLocation DMZ_TOP = new ResourceLocation("dragonminez", "beam_clash_hud");

    private XenoHudRegistration() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        try {
            event.registerAbove(DMZ_TOP, "xeno_hud", new XenoHudOverlay());
            event.registerAbove(DMZ_TOP, "xeno_technique_hotbar", new XenoTechniqueHotbarOverlay());
            event.registerAbove(DMZ_TOP, "xeno_party_hud", new XenoPartyOverlay());
        } catch (Exception e) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "xeno_hud", new XenoHudOverlay());
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "xeno_technique_hotbar", new XenoTechniqueHotbarOverlay());
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "xeno_party_hud", new XenoPartyOverlay());
        }
    }
}
