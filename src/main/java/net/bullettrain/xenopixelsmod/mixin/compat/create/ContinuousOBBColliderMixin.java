package net.bullettrain.xenopixelsmod.mixin.compat.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Half two of the fix for the Create contraption collision crash (upstream PR #10301).
 *
 * <p>{@link ContinuousSeparationManifoldMixin} removes the case that leaves the manifold's
 * axes null in practice; this is the belt-and-braces guard for any remaining degenerate
 * manifold (zero-extent colliders, non-finite maths). A null axis is substituted with
 * {@link Vec3#ZERO}, which contributes no collision response or normal for that collider
 * instead of taking the server down with a {@link NullPointerException}.
 */
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider", remap = false)
public class ContinuousOBBColliderMixin {

    @ModifyExpressionValue(
            method = "collideMany",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;axis:Lnet/minecraft/world/phys/Vec3;"
            ),
            remap = false,
            require = 0
    )
    private static Vec3 xeno$nonNullAxis(Vec3 original) {
        return original == null ? Vec3.ZERO : original;
    }

    @ModifyExpressionValue(
            method = "collideMany",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;normalAxis:Lnet/minecraft/world/phys/Vec3;"
            ),
            remap = false,
            require = 0
    )
    private static Vec3 xeno$nonNullNormalAxis(Vec3 original) {
        return original == null ? Vec3.ZERO : original;
    }

    @ModifyExpressionValue(
            method = "collideMany",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;stepSeparationAxis:Lnet/minecraft/world/phys/Vec3;"
            ),
            remap = false,
            require = 0
    )
    private static Vec3 xeno$nonNullStepSeparationAxis(Vec3 original) {
        return original == null ? Vec3.ZERO : original;
    }
}
