package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.combat.v3.ClientCastPresentation;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

/** Overrides DMZ's merged kiPredicate only while the server owns this cast's pose.
 * DMZ 2.1.3 otherwise clears every _fire after fourteen ticks. No standalone
 * native DMZ animation is changed. Lower priority applies after DMZ's mixin. */
@Mixin(value = AbstractClientPlayer.class, priority = 900)
public abstract class DmzOwnedKiLifetimeMixin {
    @Unique private String xenopixels$ownedKiPhase;
    @Inject(method = "kiPredicate", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void xenopixels$ownedKiLifetime(AnimationState<?> state, CallbackInfoReturnable<PlayState> cir) {
        String phase = ClientCastPresentation.phase(((AbstractClientPlayer)(Object)this).getUUID());
        if (phase == null) {
            if (xenopixels$ownedKiPhase != null) {
                xenopixels$ownedKiPhase = null;
                cir.setReturnValue(PlayState.STOP);
            }
            return;
        }
        if (!phase.equals(xenopixels$ownedKiPhase)) {
            state.getController().setAnimation(ClientCastPresentation.animation(phase));
            state.getController().forceAnimationReset();
            xenopixels$ownedKiPhase = phase;
        }
        cir.setReturnValue(PlayState.CONTINUE);
    }
}
