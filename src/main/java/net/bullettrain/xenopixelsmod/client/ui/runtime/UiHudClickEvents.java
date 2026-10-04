package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiLayoutEngine;
import net.bullettrain.xenopixelsmod.ui.UiNodeType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class UiHudClickEvents {
    private UiHudClickEvents() {
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui
                || !UiRuntime.enabled()) {
            return;
        }
        UiDocument document = UiRuntime.hudDocument();
        if (document == null || !"hud".equals(document.kind)) {
            return;
        }
        double mx = minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth() / Math.max(1, minecraft.getWindow().getWidth());
        double my = minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight() / Math.max(1, minecraft.getWindow().getHeight());
        UiLaidOut root = UiLayoutEngine.layout(document,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        UiLaidOut hit = UiLayoutEngine.hit(root, (int) mx, (int) my);
        if (hit != null && hit.source != null) {
            UiNodeType type = UiNodeType.byName(hit.source.type);
            if (type != null && type.firesClickAction()) {
                UiActions.fire(hit.source.action);
            }
        }
    }
}
