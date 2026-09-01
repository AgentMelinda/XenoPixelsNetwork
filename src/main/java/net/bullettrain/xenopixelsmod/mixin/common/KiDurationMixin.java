package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.bullettrain.xenopixelsmod.combat.technique.KiDuration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@code TechniqueDispatcher#resolvePlayerMaxLifeTicks}
 * Reason: every player technique fire window is {@code base × charge} here,
 *         then {@code fireHability(window)}. One RETURN inject covers all types.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common.
 */
@Mixin(value = TechniqueDispatcher.class, remap = false)
public abstract class KiDurationMixin {

    @Inject(method = "resolvePlayerMaxLifeTicks", at = @At("RETURN"), cancellable = true)
    private static void xenopixels$kiDuration(KiAttackData data, float chargeMultiplier,
                                              CallbackInfoReturnable<Integer> cir) {
        if (data == null) return;
        int stock = cir.getReturnValueI();
        int next = KiDuration.windowOrStock(data.getKiType(), stock);
        if (next != stock) {
            cir.setReturnValue(next);
        }
    }
}
