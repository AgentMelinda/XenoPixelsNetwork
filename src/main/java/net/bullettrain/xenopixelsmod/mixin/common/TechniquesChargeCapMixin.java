package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code Techniques#setTechniqueChargePercent} and {@code Techniques#load}
 * Reason: runClient has a LocalPlayer and a ServerPlayer. Charge sync and
 *         {@code ProgressionSyncS2C} both write through these 200 clamps. A
 *         missed ModifyConstant leaves the HUD at 200 even when the server
 *         hold ceiling was remapped.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common.
 */
@Mixin(value = Techniques.class, remap = false)
public abstract class TechniquesChargeCapMixin {

    @Shadow
    private float techniqueChargePercent;

    @ModifyConstant(method = {"setTechniqueChargePercent", "load"}, constant = @Constant(floatValue = 200.0F))
    private float xenopixels$chargeCap(float original) {
        return XenoServerConfig.chargeOverchargeClamp();
    }

    @Inject(method = "setTechniqueChargePercent", at = @At("HEAD"), cancellable = true)
    private void xenopixels$storeCharge(float percent, CallbackInfo ci) {
        float cap = XenoServerConfig.chargeOverchargeClamp();
        this.techniqueChargePercent = Math.max(0.0f, Math.min(cap, percent));
        ci.cancel();
    }
}
