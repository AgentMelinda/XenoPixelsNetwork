package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3KeyframeHandlers;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimatableManager;

/** Adds Xeno cosmetic keyframe callbacks to DragonMineZ's existing attack controller. */
@Mixin(value = AbstractClientPlayer.class, priority = 900)
public abstract class DmzAttackControllerKeyframeMixin {

    @Inject(method = "registerControllers", at = @At("RETURN"), remap = false, require = 0)
    private void xeno$attachBt3KeyframeHandlers(AnimatableManager.ControllerRegistrar controllers,
                                                CallbackInfo ci) {
        controllers.controllers().forEach(Bt3KeyframeHandlers::attach);
    }
}
