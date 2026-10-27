package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.KiPlanetShellDestruction;
import net.bullettrain.xenopixelsmod.compat.sable.KiTerrainProjectileContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DMZ 2.2 jobs outlive their creation call. Capture once, then keep the original shell opt-in,
 * plot-only and owner permission checks without allocating context for each visited block.
 * DMZ's own job KiGriefGate still runs after replacementFor, for both probe and destruction.
 */
@Mixin(targets = "com.dragonminez.common.combat.util.KiTerrainDestruction$Job", remap = false)
public abstract class KiTerrainJobPlanetShellMixin {
    @Unique private AbstractKiProjectile xenopixels$initiatingProjectile;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void xenopixels$captureProjectile(CallbackInfo ci) {
        xenopixels$initiatingProjectile = KiTerrainProjectileContext.current();
    }

    @WrapOperation(method = "replacementFor",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/block/state/BlockState;getExplosionResistance(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)F"),
            require = 1)
    private float xenopixels$allowAuthorizedPlanetShell(BlockState state, BlockGetter level, BlockPos pos,
                                                       Explosion explosion, Operation<Float> original) {
        return KiPlanetShellDestruction.allowResistanceBypass(xenopixels$initiatingProjectile, state, pos)
                ? 0.0f : original.call(state, level, pos, explosion);
    }
}
