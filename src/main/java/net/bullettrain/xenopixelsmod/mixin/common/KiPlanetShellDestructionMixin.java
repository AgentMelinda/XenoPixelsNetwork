package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.compat.sable.KiPlanetShellDestruction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opts the shell into DMZ's existing permission-checked Ki block removal path. */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiPlanetShellDestructionMixin {
    @Inject(method = "canKiDestroyBlock", at = @At("RETURN"), cancellable = true)
    private void xenopixels$allowPlanetShell(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && KiPlanetShellDestruction.canBreak((AbstractKiProjectile) (Object) this, pos))
            cir.setReturnValue(true);
    }
}
