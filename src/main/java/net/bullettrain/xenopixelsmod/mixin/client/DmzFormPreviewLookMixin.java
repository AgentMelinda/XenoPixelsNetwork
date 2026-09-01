package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.gui.radial.ModelFormPreview;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Same look-rotation leak as {@link DmzMenuPreviewLookMixin}, for the static form preview.
 */
@Mixin(value = ModelFormPreview.class, remap = false)
public abstract class DmzFormPreviewLookMixin {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setYRot(F)V", remap = true),
            require = 0
    )
    private static void xenopixels$keepLookYaw(Entity player, float yaw) {
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setYRot(F)V", remap = true),
            require = 0
    )
    private static void xenopixels$keepLookYawLiving(LivingEntity player, float yaw) {
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setXRot(F)V", remap = true),
            require = 0
    )
    private static void xenopixels$keepLookPitch(Entity player, float pitch) {
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setXRot(F)V", remap = true),
            require = 0
    )
    private static void xenopixels$keepLookPitchLiving(LivingEntity player, float pitch) {
    }
}
