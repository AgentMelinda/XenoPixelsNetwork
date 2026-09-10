package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.combat.SparkingAura;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paints a Sparking player's aura gold whatever form they are in.
 *
 * <p>{@code AuraRenderer.interpolateColor} is the single point every aura colour passes through, so
 * overriding its result covers base, form and stack auras alike without having to know which one is
 * being drawn — which is what "regardless of form" needs.
 *
 * <p>Whose aura it is comes from {@link DmzAuraLayerSparkingMixin}; this method is static and is
 * handed only two colour names, so it could not work that out for itself.
 */
@Mixin(targets = "com.dragonminez.client.render.effects.AuraRenderer", remap = false)
public abstract class DmzAuraColorSparkingMixin {

    @Inject(method = "interpolateColor(Ljava/lang/String;Ljava/lang/String;F)[F",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void xenopixels$sparkingGold(String from, String to, float progress,
                                                CallbackInfoReturnable<float[]> cir) {
        if (SparkingAura.shouldTint()) {
            cir.setReturnValue(SparkingAura.color());
        }
    }
}
