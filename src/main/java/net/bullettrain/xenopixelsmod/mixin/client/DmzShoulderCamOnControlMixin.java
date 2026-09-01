package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.camera.OverShoulderCamera;
import net.bullettrain.xenopixelsmod.client.camera.ContraptionControlCamera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: DMZ {@code OverShoulderCamera#isActive}.
 * Reason: shoulder offset fights Create {@code ControlsHandler} and Sable
 *         {@code SUB_LEVEL_VIEW} / {@code SUB_LEVEL_VIEW_UNLOCKED}.
 * Version: DMZ 2.1.3. Off only while a control camera is active.
 * Side: client.
 */
@Mixin(value = OverShoulderCamera.class, remap = false)
public abstract class DmzShoulderCamOnControlMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$disableOnControlCam(Entity entity, boolean thirdPersonReverse,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (ContraptionControlCamera.active()) {
            cir.setReturnValue(false);
        }
    }
}
