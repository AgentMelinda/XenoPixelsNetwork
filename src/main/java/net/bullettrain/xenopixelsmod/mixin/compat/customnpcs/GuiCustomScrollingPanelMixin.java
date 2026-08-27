package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.mixin.compat.shared.XenoMouseHandlerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * See {@link XenoMouseHandlerAccessor}. This class's {@code render} method calls CustomNPCs'
 * own (sometimes-broken) {@code MouseHelperMixin.getActiveButton()}; redirected to Xenopixels'
 * own accessor for the same field. {@code require = 1} so a missed invoke fails this mixin
 * loudly; the compat config stays {@code required: false} so a CustomNPCs-less boot still works.
 */
@Mixin(targets = "noppes.npcs.client.gui.custom.GuiCustomScrollingPanel", remap = false)
public abstract class GuiCustomScrollingPanelMixin {

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
