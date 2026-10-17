package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.KiPlanetShellDestruction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets only opted-in, permission-approved Ki waves pass the shell's intentional high resistance. */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWavePlanetShellResistanceMixin {
    @WrapOperation(method = {"explodeAndDie", "processDetonation", "destroyBlocksAt"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getExplosionResistance(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)F"),
            require = 0)
    private float xenopixels$allowPlanetShellResistance(BlockState state, BlockGetter level, BlockPos pos,
                                                       Explosion explosion, Operation<Float> original) {
        return KiPlanetShellDestruction.allowResistanceBypass((AbstractKiProjectile) (Object) this, state, pos)
                ? 0.0f : original.call(state, level, pos, explosion);
    }
}
