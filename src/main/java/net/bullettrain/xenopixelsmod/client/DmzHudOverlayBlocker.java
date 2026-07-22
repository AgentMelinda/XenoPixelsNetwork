package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Cancels DragonMineZ Forge GUI overlays when the server-wide DMZ HUD flag is off.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class DmzHudOverlayBlocker {
    private static final Set<ResourceLocation> DMZ_OVERLAYS = Set.of(
            id("xenoversehud"),
            id("alternativehud"),
            id("technique_charge_hud"),
            id("scouterhud"),
            id("tracked_quest_hud"),
            id("techniquehud"),
            id("beam_clash_hud")
    );

    private DmzHudOverlayBlocker() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation("dragonminez", path);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (DmzHudClientState.isDmzHudEnabled()) return;
        if (DMZ_OVERLAYS.contains(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
