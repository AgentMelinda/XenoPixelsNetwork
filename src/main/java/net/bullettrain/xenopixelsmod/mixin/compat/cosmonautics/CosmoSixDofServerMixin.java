package net.bullettrain.xenopixelsmod.mixin.compat.cosmonautics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.cosmonautics.CosmoSixDofPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: Cosmonautics {@code LivingEntityMixin#rocketnautics$6DOFMovement}
 *         invoke of {@code FreeMotionHandler.apply6DOFPhysics}.
 * Reason: that client class imports {@code LocalPlayer}. On a dedicated server
 *         {@code travel} then DistCleans and kicks the player.
 * Version: Cosmonautics 6DOF (post 26.08.307). Gated by rocketnautics.
 * Side: common mixin, dedicated-server branch only. Client keeps Cosmo's handler.
 */
@Mixin(targets = "dev.devce.rocketnautics.mixin.LivingEntityMixin", remap = false)
public abstract class CosmoSixDofServerMixin {

    @WrapOperation(
            method = "rocketnautics$6DOFMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/devce/rocketnautics/client/FreeMotionHandler;apply6DOFPhysics(Lorg/joml/Vector3f;Lnet/minecraft/world/entity/LivingEntity;)Z",
                    remap = false),
            require = 0)
    private boolean xenopixels$serverSafeSixDof(Vector3f motion, LivingEntity entity,
                                                Operation<Boolean> original) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            return CosmoSixDofPhysics.apply(motion, entity);
        }
        return original.call(motion, entity);
    }
}
