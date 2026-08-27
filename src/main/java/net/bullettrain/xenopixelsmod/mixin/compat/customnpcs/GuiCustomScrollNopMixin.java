package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.mixin.compat.shared.XenoMouseHandlerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** See {@link XenoMouseHandlerAccessor} and {@link GuiCustomScrollingPanelMixin}. */
@Mixin(targets = "noppes.npcs.shared.client.gui.components.GuiCustomScrollNop", remap = false)
public abstract class GuiCustomScrollNopMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/mixin/MouseHelperMixin;getActiveButton()I",
                    remap = false),
            require = 1
    )
    private int xenopixels$getActiveButton(@Coerce Object mouseHandler) {
        return ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$getActiveButton();
    }
}
