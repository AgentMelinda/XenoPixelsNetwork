package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.combat.technique.DmzTechniqueCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Raises DMZ 2.1.3's hardcoded per-player unlock limit for the larger V3 strike catalog. */
@Mixin(value = Techniques.class, remap = false)
public abstract class TechniquesCapacityMixin {
    @ModifyConstant(method = "unlockTechnique", constant = @Constant(intValue = 256))
    private int xenopixels$boundedTechniqueCapacity(int original) {
        return DmzTechniqueCapacity.MAX_UNLOCKED;
    }
}
