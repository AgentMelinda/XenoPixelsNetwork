package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.server.events.players.TickHandler;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Target: {@code TickHandler#handleTechniqueCharge}
 * Reason: DMZ inlines {@code OVERCHARGE_MAX_PERCENT} as {@code 175.0f} for the
 *         hold ceiling. That is also the value it writes into
 *         {@code TechniqueChargeSyncS2C}. On a dedicated server the extra Xeno
 *         increment still lives on the server copy so the ball grows; on
 *         {@code runClient} the next sync packet writes 175 back onto the
 *         client (same process, same HUD) and the cap looks broken.
 *         {@code handleTechniqueCharge} has two 175s. The third 175 is the fire
 *         snapshot in {@code resolveKiAttackOnRelease} ({@code percent/100} for
 *         cooldown and cost). Leave that one alone — {@code ChargeOverchargeManager}
 *         scales the live projectile.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common.
 */
@Mixin(value = TickHandler.class, remap = false)
public abstract class TickHandlerChargeCeilingMixin {

    @ModifyConstant(method = "handleTechniqueCharge", constant = @Constant(floatValue = 175.0F), require = 2)
    private static float xenopixels$holdCeiling(float original) {
        return XenoServerConfig.chargeOverchargeClamp();
    }
}
