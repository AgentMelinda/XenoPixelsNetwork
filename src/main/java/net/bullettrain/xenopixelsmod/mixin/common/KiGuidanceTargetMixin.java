package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: {@code TechniqueDispatcher#resolveHomingTarget}
 * Reason: Ki Guidance 2+ homes a look-target when no lock-on is set, for thrown ki only.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. The method already returns null on the client.
 */
@Mixin(value = TechniqueDispatcher.class, remap = false)
public abstract class KiGuidanceTargetMixin {

    @Inject(method = "resolveHomingTarget", at = @At("RETURN"), cancellable = true)
    private static void xenopixels$lookHome(LivingEntity owner, Level level, KiAttackData data,
                                            StatsData statsData,
                                            CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity resolved = KiGuidance.lookHomeFallback(owner, level, data, cir.getReturnValue());
        if (resolved != cir.getReturnValue()) {
            cir.setReturnValue(resolved);
        }
    }
}
