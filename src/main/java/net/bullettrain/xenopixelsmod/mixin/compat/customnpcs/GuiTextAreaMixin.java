package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.mixin.compat.shared.XenoMouseHandlerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * See {@link XenoMouseHandlerAccessor}. {@code render} calls the broken accessor twice
 * (once for the click edge, once for the click-drag/scroll edge); a single {@code @Redirect}
 * without an {@code ordinal} applies to every matching call site within the method, so one
 * handler covers both.
 */
@Mixin(targets = "noppes.npcs.shared.client.gui.components.GuiTextArea", remap = false)
public abstract class GuiTextAreaMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/mixin/MouseHelperMixin;getActiveButton()I",
                    remap = false),
            require = 1
    )
    private int xenopixels$getActiveButton(@Coerce Object mouseHandler) {
        return ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$getActiveButton();
    }
}
