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
 * Version: NeoForge 1.21.1 / DMZ 2.2
 * Side: common. Destruction is server-only.
 */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveSableCenterMixin {

    @ModifyVariable(method = "destroyBlocksAt(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private BlockPos xenopixels$sableWaveCenter(BlockPos center) {
        KiWaveEntity wave = (KiWaveEntity) (Object) this;
        BlockPos resolved = KiBeamBore.waveCenter(wave, center);
        return resolved;
    }
}
