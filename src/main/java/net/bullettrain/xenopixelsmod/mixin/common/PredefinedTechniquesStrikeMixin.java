package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DMZ 2.1.3 checks only its KI map in {@code isPredefinedTechniqueId}; include its own strike map
 * so registered Xeno strike definitions can use the native unlock/equip path.
 */
@Mixin(value = PredefinedTechniques.class, remap = false)
public abstract class PredefinedTechniquesStrikeMixin {
    @Inject(method = "isPredefinedTechniqueId", at = @At("RETURN"), cancellable = true)
    private static void xenopixels$includeStrikeIds(String id, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && id != null
                && PredefinedTechniques.STRIKE_REGISTRY.containsKey(id.toLowerCase(java.util.Locale.ROOT))) {
            cir.setReturnValue(true);
        }
    }
}
