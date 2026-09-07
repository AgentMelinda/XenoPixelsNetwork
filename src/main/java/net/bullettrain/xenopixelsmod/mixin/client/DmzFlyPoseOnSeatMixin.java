package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.FlySkillEvent;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: DMZ {@code FlySkillEvent#isFlyingFast(AbstractClientPlayer)}.
 *
 * <p>Reason: when this returns true, {@code DMZPlayerRenderer.render} pivots the whole player
 * model about its mid-height into a "lie flat / dive" ki-flight pose (translate +bbHeight/2,
 * rotate by view pitch + roll, translate -bbHeight/2). If a pilot mounts a
 * {@link XenoPilotSeatEntity} while DMZ still considers them fast-flying, that pose keeps being
 * applied on top of a seated, feet-anchored ride position — the pilot renders tilted and floating
 * a block or so above the seat. A passenger is not fast-flying, so report false while riding our
 * seat; the model then sits normally.
 *
 * <p>Same "turn a DMZ client behaviour off while on our controls" shape as
 * {@link DmzFirstPersonCamOnControlMixin} / {@link DmzShoulderCamOnControlMixin}.
 *
 * <p>Version: DMZ 2.1.3. Side: client.
 */
@Mixin(value = FlySkillEvent.class, remap = false)
public abstract class DmzFlyPoseOnSeatMixin {

    @Inject(method = "isFlyingFast(Lnet/minecraft/client/player/AbstractClientPlayer;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$noFlyPoseWhileSeated(AbstractClientPlayer player,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (player != null && player.getVehicle() instanceof XenoPilotSeatEntity) {
            cir.setReturnValue(false);
        }
    }
}
