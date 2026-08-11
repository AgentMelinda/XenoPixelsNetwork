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
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_speed_lines"),
                new net.bullettrain.xenopixelsmod.client.combat.fx.SpeedLinesOverlay()::render);
        // Last, so the impact flash washes over the HUD rather than under it — a flash the
        // health bars punch through reads as a rendering glitch instead of a hit.
        event.registerAbove(DMZ_TOP, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "xeno_combat_flash"),
                new net.bullettrain.xenopixelsmod.client.combat.fx.CombatFlashOverlay()::render);
    }
}
