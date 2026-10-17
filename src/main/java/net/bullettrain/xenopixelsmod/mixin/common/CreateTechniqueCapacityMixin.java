package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.network.C2S.CreateTechniqueC2S;
import net.bullettrain.xenopixelsmod.combat.technique.DmzTechniqueCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps DMZ's custom-technique rejection threshold in step with Techniques#unlockTechnique. */
@Mixin(value = CreateTechniqueC2S.class, remap = false)
public abstract class CreateTechniqueCapacityMixin {
    @ModifyConstant(method = "lambda$handle$0", constant = @Constant(intValue = 256))
    private int xenopixels$boundedTechniqueCapacity(int original) {
        return DmzTechniqueCapacity.MAX_UNLOCKED;
    }
}
