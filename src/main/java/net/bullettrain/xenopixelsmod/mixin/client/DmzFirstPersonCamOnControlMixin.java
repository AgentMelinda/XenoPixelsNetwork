package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import net.bullettrain.xenopixelsmod.client.camera.ContraptionControlCamera;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: DMZ {@code FirstPersonManager#shouldRenderFirstPerson}.
 * Reason: FP eye/body offset is applied from {@code CameraMixin.dragonminez$modifyCamera}
 *         and the POV renderer. That steals Create/Sable control cameras.
 * Version: DMZ 2.1.3. Off only while a control camera is active.
 * Side: client.
 */
@Mixin(value = FirstPersonManager.class, remap = false)
public abstract class DmzFirstPersonCamOnControlMixin {

    @Inject(method = "shouldRenderFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$disableOnControlCam(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (ContraptionControlCamera.active()) {
            cir.setReturnValue(false);
        }
    }
}
