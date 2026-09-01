package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.gui.hud.TechniqueChargeOverlay;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Target: DMZ charge HUD lerp clamp
 * Reason: even with a stored percent above 200, the stock overlay caps the displayed
 *         fill at 200, so the bar fights the real value and looks like it is buzzing.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: client.
 */
@Mixin(value = TechniqueChargeOverlay.class, remap = false)
public abstract class TechniqueChargeOverlayCapMixin {

    @ModifyConstant(method = "*", constant = @Constant(floatValue = 200.0F))
    private static float xenopixels$overlayCap(float original) {
        return XenoServerConfig.chargeOverchargeClamp();
    }
}
