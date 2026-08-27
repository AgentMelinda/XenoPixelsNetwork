package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.mixin.compat.shared.XenoMouseHandlerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * See {@link XenoMouseHandlerAccessor}. {@code grabMouse(boolean)} calls all three of the
 * remaining accessor methods My NPCs' own mixin exposes; each gets its own redirect to the
 * matching Xenopixels accessor for the same field.
 */
@Mixin(targets = "espi.mynpcs.client.gui.player.GuiDialogInteract", remap = false)
public abstract class GuiDialogInteractMixin {

    @Redirect(
            method = "grabMouse(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/mixin/MouseHelperMixin;setGrabbed(Z)V",
                    remap = false),
            require = 1
    )
    private void xenopixels$setGrabbed(@Coerce Object mouseHandler, boolean grabbed) {
        ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$setMouseGrabbed(grabbed);
    }

    @Redirect(
            method = "grabMouse(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/mixin/MouseHelperMixin;setX(D)V",
                    remap = false),
            require = 1
    )
    private void xenopixels$setX(@Coerce Object mouseHandler, double x) {
        ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$setXpos(x);
    }

    @Redirect(
            method = "grabMouse(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/mixin/MouseHelperMixin;setY(D)V",
                    remap = false),
            require = 1
    )
    private void xenopixels$setY(@Coerce Object mouseHandler, double y) {
        ((XenoMouseHandlerAccessor) mouseHandler).xenopixels$setYpos(y);
    }
}
