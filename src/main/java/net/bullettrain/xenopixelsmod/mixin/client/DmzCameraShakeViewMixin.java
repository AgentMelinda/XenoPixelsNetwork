package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.EffectsEvents;
import net.bullettrain.xenopixelsmod.client.camera.DmzCameraShakeControl;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code EffectsEvents#onCameraSetup}.
 * Reason: DMZ adds yaw/pitch/roll every frame (stagger, form charge, nearby casts).
 *         Off in every camera mode; {@code /xenoclient set dmzshake true} restores it.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x.
 * Side: client. {@code require = 0} so a DMZ rename does not crash boot.
 */
@Mixin(value = EffectsEvents.class, remap = false)
public abstract class DmzCameraShakeViewMixin {

    @Inject(method = "onCameraSetup", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$skipDmzShake(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (DmzCameraShakeControl.suppress()) {
            ci.cancel();
        }
    }
}
