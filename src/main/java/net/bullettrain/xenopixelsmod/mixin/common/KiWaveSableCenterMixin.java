package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.combat.beam.KiBeamBore;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Target: {@code KiWaveEntity#destroyBlocksAt}
 * Reason: ship clips are plot-local while a world-side wave edits {@code this.level()}.
 *         Blasts already get this correction; waves did not.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. Destruction is server-only.
 */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveSableCenterMixin {

    @ModifyVariable(method = "destroyBlocksAt", at = @At("HEAD"), argsOnly = true)
    private BlockPos xenopixels$sableWaveCenter(BlockPos center) {
        return KiBeamBore.waveCenter((KiWaveEntity) (Object) this, center);
    }
}
