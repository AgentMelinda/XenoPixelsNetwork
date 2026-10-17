package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.KiPlanetShellDestruction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Permits the DMZ Ki explosion crater scan to process opted-in Sable planet shell blocks. */
@Mixin(value = KiExplosionEntity.class, remap = false)
public abstract class KiExplosionPlanetShellResistanceMixin {
    @WrapOperation(method = "createCrater",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
            require = 1)
    private float xenopixels$allowPlanetShell(BlockState state, BlockGetter level, BlockPos pos,
                                               Operation<Float> original) {
        return KiPlanetShellDestruction.allowResistanceBypass((AbstractKiProjectile) (Object) this, state, pos)
                ? 1.0f : original.call(state, level, pos);
    }
}
