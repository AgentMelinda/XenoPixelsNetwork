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
 * Backup wrap if Cosmo inlined 6DOF into {@code LivingEntity#travel}.
 * Same crash as {@link CosmoSixDofServerMixin}: {@code FreeMotionHandler} loads
 * {@code LocalPlayer} on the dedicated server.
 */
@Mixin(value = LivingEntity.class, priority = 500)
public abstract class CosmoSixDofTravelMixin {

    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/devce/rocketnautics/client/FreeMotionHandler;apply6DOFPhysics(Lorg/joml/Vector3f;Lnet/minecraft/world/entity/LivingEntity;)Z",
                    remap = false),
            require = 0)
    private boolean xenopixels$serverSafeSixDofTravel(Vector3f motion, LivingEntity entity,
                                                      Operation<Boolean> original) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            return CosmoSixDofPhysics.apply(motion, entity);
        }
        return original.call(motion, entity);
    }
}
