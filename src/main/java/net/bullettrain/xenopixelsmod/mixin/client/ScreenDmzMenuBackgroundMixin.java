package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Xeno-themed DragonMineZ menus readable without Minecraft's post-process blur. */
@Mixin(Screen.class)
public abstract class ScreenDmzMenuBackgroundMixin {

    @Shadow
    protected abstract void renderTransparentBackground(GuiGraphics graphics);

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void xenopixels$renderSharpDmzBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        if (!DmzMenuThemeState.isThemed()) {
            return;
        }
        renderTransparentBackground(graphics);
        ci.cancel();
    }
}
