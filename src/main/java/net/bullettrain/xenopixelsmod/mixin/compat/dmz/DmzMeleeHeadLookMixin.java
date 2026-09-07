package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.client.combat.DmzMeleeHeadGate;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * After DMZ writes camera look onto {@code head}, zero that local rot so the bone inherits
 * {@code root} and hair (parented to head) spins with the body.
 */
@Mixin(value = DMZPlayerModel.class, remap = false)
public abstract class DmzMeleeHeadLookMixin {

    @Inject(
            method = "setCustomAnimations(Lnet/minecraft/client/player/AbstractClientPlayer;JLsoftware/bernie/geckolib/animation/AnimationState;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void xenopixels$zeroHeadLook(AbstractClientPlayer player, long instanceId,
                                         AnimationState<?> state, CallbackInfo ci) {
        if (!DmzMeleeHeadGate.active(player)) return;
        try {
            AnimationProcessor<?> processor = ((DMZPlayerModel<?>) (Object) this).getAnimationProcessor();
            if (processor == null) return;
            GeoBone head = processor.getBone("head");
            if (head != null) {
                head.setRotX(0.0f);
                head.setRotY(0.0f);
            }
        } catch (Throwable ignored) {
        }
    }
}
