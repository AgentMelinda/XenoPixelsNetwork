package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.bullettrain.xenopixelsmod.combat.technique.XenoRushTechniques;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the four Xeno rush strikes a cost and cooldown of their own.
 *
 * <p>DragonMineZ derives a strike's ki cost from the caster's own power —
 * {@code strikeDamageNoForms × damage multipliers × 0.35 × kiCostMultiplier / 2} — and ignores the
 * {@code baseCost} a technique declares. At high power that reached thousands of ki per press, so
 * a rush could only be thrown when the bar happened to be full. Cooldown has the same problem from
 * the other side: {@code getActualCooldown} re-reads {@code getStrikeConfig(id)} every call, so a
 * cooldown set at registration never survives.
 *
 * <p>Only ids in {@link XenoRushTechniques} are affected. Every other strike, including anything a
 * player authors in DMZ's technique creator, keeps DMZ's own numbers.
 */
@Mixin(value = StrikeAttackData.class, remap = false)
public abstract class StrikeAttackCostMixin {

    @Inject(method = "getCalculatedCost", at = @At("HEAD"), cancellable = true)
    private void xenopixels$rushCost(StatsData statsData, CallbackInfoReturnable<Double> cir) {
        if (XenoRushTechniques.isRushId(((StrikeAttackData) (Object) this).getId())) {
            cir.setReturnValue(Math.max(0.0, XenoServerConfig.rushKiCost));
        }
    }

    @Inject(method = "getActualCooldown", at = @At("HEAD"), cancellable = true)
    private void xenopixels$rushCooldown(CallbackInfoReturnable<Integer> cir) {
        if (XenoRushTechniques.isRushId(((StrikeAttackData) (Object) this).getId())) {
            cir.setReturnValue(Math.max(1, XenoServerConfig.rushCooldownTicks));
        }
    }
}
