package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState;
import net.bullettrain.xenopixelsmod.client.npc.quest.XenoInventoryTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Xeno-themed DragonMineZ menus readable without Minecraft's post-process blur. */
@Mixin(Screen.class)
public abstract class ScreenDmzMenuBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void xenopixels$renderSharpDmzBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        if (!DmzMenuThemeState.isThemed() && !XenoInventoryTabs.sidePanelOpen()) {
            return;
        }
        // Match vanilla's readable dim while avoiding its world-blur post process.
        Screen screen = (Screen) (Object) this;
        graphics.fill(0, 0, screen.width, screen.height, 0x90000000);
        ci.cancel();
    }
}
