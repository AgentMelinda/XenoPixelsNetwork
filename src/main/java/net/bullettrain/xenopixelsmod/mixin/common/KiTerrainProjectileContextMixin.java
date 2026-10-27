package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.KiTerrainProjectileContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve projectile authorization context across DMZ 2.2's queued terrain helper boundary. */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiTerrainProjectileContextMixin {
    @WrapOperation(method = "carveKiSphere(Lnet/minecraft/core/BlockPos;FFI)V",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/common/combat/util/KiTerrainDestruction;carve(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;FFILnet/minecraft/world/entity/Entity;)V"),
            require = 1)
    private void xenopixels$carveWithProjectile(ServerLevel level, BlockPos center, float innerRadius,
                                               float radius, int flags, Entity source, Operation<Void> original) {
        AbstractKiProjectile previous = KiTerrainProjectileContext.enter((AbstractKiProjectile) (Object) this);
        try {
            original.call(level, center, innerRadius, radius, flags, source);
        } finally {
            KiTerrainProjectileContext.restore(previous);
        }
    }

    @WrapOperation(method = "eatKiSphere(Lnet/minecraft/core/BlockPos;F)Z",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/common/combat/util/KiTerrainDestruction;eat(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;FLnet/minecraft/core/BlockPos;FLnet/minecraft/world/entity/Entity;)Z"),
            require = 1)
    private boolean xenopixels$eatWithProjectile(ServerLevel level, BlockPos center, float radius,
                                                BlockPos skipCenter, float skipRadius, Entity source,
                                                Operation<Boolean> original) {
        AbstractKiProjectile previous = KiTerrainProjectileContext.enter((AbstractKiProjectile) (Object) this);
        try {
            return original.call(level, center, radius, skipCenter, skipRadius, source);
        } finally {
            KiTerrainProjectileContext.restore(previous);
        }
    }
}
