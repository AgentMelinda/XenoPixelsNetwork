package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.client.combat.DmzMeleeHeadGate;
import net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Keeps active V3 head/arm keyframes intact when DMZ applies procedural look and walking.
 * The older R-mash path still zeros head look so hair inherits the root turn.
 */
@Mixin(value = DMZPlayerModel.class, remap = false)
public abstract class DmzMeleeHeadLookMixin {

    // DMZ 2.1.3 writes head X/Y, shooting-arm X/Y, and walking-arm X here after
    // GeckoLib evaluates the authored clip. Gate by the actual bone and active
    // registered occurrence, rather than fragile instruction ordinals.
    @Redirect(
            method = "setCustomAnimations(Lnet/minecraft/client/player/AbstractClientPlayer;JLsoftware/bernie/geckolib/animation/AnimationState;)V",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/cache/object/GeoBone;setRotX(F)V"),
            require = 1
    )
    private void xenopixels$preserveAuthoredX(GeoBone bone, float rotation, AbstractClientPlayer player,
                                            long instanceId, AnimationState<?> state) {
        if (!xenopixels$ownsAuthoredBone(player, bone)) bone.setRotX(rotation);
    }

    @Redirect(
            method = "setCustomAnimations(Lnet/minecraft/client/player/AbstractClientPlayer;JLsoftware/bernie/geckolib/animation/AnimationState;)V",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/cache/object/GeoBone;setRotY(F)V"),
            require = 1
    )
    private void xenopixels$preserveAuthoredY(GeoBone bone, float rotation, AbstractClientPlayer player,
                                            long instanceId, AnimationState<?> state) {
        if (!xenopixels$ownsAuthoredBone(player, bone)) bone.setRotY(rotation);
    }

    @Unique
    private static boolean xenopixels$ownsAuthoredBone(AbstractClientPlayer player, GeoBone bone) {
        if (!(player instanceof GeoAnimatable animation)) return false;
        // DMZ's getCurrentPlayingAnimation() reads the movement "controller";
        // POSE/MeleeAnimationS2C plays on "attack_controller" instead (2.1.3).
        var manager = animation.getAnimatableInstanceCache().getManagerForId(player.getId());
        for (String name : new String[]{"attack_controller", "ki_controller"}) {
            AnimationController<?> controller = manager.getAnimationControllers().get(name);
            if (controller == null || controller.getAnimationState() == AnimationController.State.STOPPED
                    || controller.hasAnimationFinished()) continue;
            var current = controller.getCurrentAnimation();
            if (current != null && current.animation() != null && V3AnimationCatalog.preservesBonePose(
                    current.animation().name(), true, bone.getName())) return true;
        }
        return false;
    }

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
            if (head != null && !xenopixels$ownsAuthoredBone(player, head)) {
                head.setRotX(0.0f);
                head.setRotY(0.0f);
            }
        } catch (Throwable ignored) {
        }
    }
}
