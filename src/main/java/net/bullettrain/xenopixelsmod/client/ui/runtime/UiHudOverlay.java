package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshotFactory;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiLayoutEngine;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Additive pack HUD. Default off. Not a replacement for {@code XenoHudOverlay},
 * not a Fabric runtime, and not the concept PNG editor.
 */
@OnlyIn(Dist.CLIENT)
public final class UiHudOverlay {
    private static boolean loggedDraw;

    private UiHudOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !UiRuntime.enabled()) {
            return;
        }
        // Both studio chromes suppress the overlay, so the switch cannot change this behaviour.
        if (minecraft.screen instanceof net.bullettrain.xenopixelsmod.client.ui.studio.DmzGuiStudioScreen
                || minecraft.screen instanceof net.bullettrain.xenopixelsmod.client.ui.studio.DmzGuiStudioAtlasScreen) {
            return;
        }
        UiDocument document = UiRuntime.hudDocument();
        if (document == null || !"hud".equals(document.kind)) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (!loggedDraw) {
            loggedDraw = true;
            XenoPixelsMod.LOGGER.info("UI pack overlay drawing {} (F1 hideGui still applies)", document.id);
        }
        UiLaidOut root = UiLayoutEngine.layout(document, width, height);
        UiRenderer.draw(graphics, root, new UiSnapshotBindings(XenoHudSnapshotFactory.capture(minecraft)), false);
    }
}
