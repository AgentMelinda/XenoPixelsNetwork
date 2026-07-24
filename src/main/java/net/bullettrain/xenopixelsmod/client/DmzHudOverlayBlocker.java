package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Cancels DragonMineZ HUD overlays according to XenoPixels settings.
 * When our technique hotbar is enabled, DMZ technique/charge HUDs are blocked too.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class DmzHudOverlayBlocker {
    // Note: scouterhud, tracked_quest_hud, and beam_clash_hud are intentionally NOT blocked here —
    // they're separate HUD elements (scouter readout, quest tracker, beam clash minigame) that should
    // keep rendering alongside our custom HUD, not be replaced by it.
    private static final Set<ResourceLocation> DMZ_MAIN_HUD = Set.of(
            id("xenoversehud"),
            id("alternativehud")
    );

    /** Replaced by {@link XenoTechniqueHotbarOverlay}. */
    private static final Set<ResourceLocation> DMZ_TECHNIQUE_UI = Set.of(
            id("techniquehud"),
            id("technique_charge_hud")
    );

    private DmzHudOverlayBlocker() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation("dragonminez", path);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();

        // Always replace DMZ technique bars with ours when enabled
        if (XenoClientConfig.techniqueHotbarEnabled && DMZ_TECHNIQUE_UI.contains(id)) {
            event.setCanceled(true);
            return;
        }

        if (DmzHudClientState.isDmzHudEnabled()) return;
        if (DMZ_MAIN_HUD.contains(id)) {
            event.setCanceled(true);
        }
    }
}
