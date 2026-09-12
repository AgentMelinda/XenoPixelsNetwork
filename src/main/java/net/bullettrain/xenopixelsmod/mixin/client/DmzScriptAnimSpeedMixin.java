package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.combat.ScriptAnimSpeedClient;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

/**
 * DragonMineZ's KI play-and-hold path hard-sets controller speed to 1.0. Scripted studio clips
 * carry a multiplier the KI API cannot; this writes it only while
 * {@link ScriptAnimSpeedClient} has an entry for this player.
 *
 * <p>Walk / run branches later overwrite speed when this is not a hold, so a leftover map entry
 * cannot retune locomotion.
 *
 * <p>{@code predicate} is merged by DragonMineZ {@code PlayerGeoAnimatableMixin} at priority 1000.
 * An {@code INVOKE} shift into that method is only legal when this mixin is strictly later.
 */
@Mixin(value = AbstractClientPlayer.class, remap = false, priority = 1100)
public abstract class DmzScriptAnimSpeedMixin {

    @Inject(
            method = "predicate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/animation/AnimationController;setAnimationSpeed(D)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            remap = false,
            require = 0)
    private <T extends GeoAnimatable> void xenopixels$scriptClipSpeed(
            AnimationState<T> state, CallbackInfoReturnable<PlayState> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Float speed = ScriptAnimSpeedClient.get(self.getUUID());
        if (speed != null) {
            state.getController().setAnimationSpeed(speed.doubleValue());
        }
    }
}
