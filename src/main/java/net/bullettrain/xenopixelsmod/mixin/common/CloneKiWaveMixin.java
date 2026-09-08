package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors a successfully released DragonMineZ wave from a split fighter's other bodies. */
@Mixin(value = TechniqueDispatcher.class, remap = false)
public abstract class CloneKiWaveMixin {
    @Inject(method = "executeKiAttack", at = @At("RETURN"))
    private static void xenopixels$mirrorWave(LivingEntity caster, Level level, KiAttackData attack,
                                               StatsData stats, float chargeMultiplier,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && caster instanceof ServerPlayer player && attack != null
                && attack.getKiType() == KiAttackData.KiType.WAVE) {
            XenoCloneSystem.mirrorKiWave(player, attack, chargeMultiplier);
        }
    }
}
