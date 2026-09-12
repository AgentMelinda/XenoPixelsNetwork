package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractRadialNode.class, remap = false)
public abstract class DmzFormTypeIconMixin {
    @Inject(method = "iconForFormType", at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$formTypeIcon(String type, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation icon = DmzFormMetadataRegistry.formTypeIcon(type);
        if (icon != null) cir.setReturnValue(icon);
    }
}
