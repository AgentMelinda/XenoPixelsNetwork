package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.combat.aura.XenoAuraScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the aura grow with the character and tower while they power up.
 *
 * <p>{@code AuraRenderer.getAuraScale} is the one method every aura render path asks for a size --
 * the world aura, the first-person aura, the pulse and the character-screen preview all call it --
 * so overriding its result covers all of them at once. It is also already handed the player's
 * {@link StatsData}, which carries both things this needs: battle power, and whether they are
 * charging.
 *
 * <p>{@code require = 0} so a DragonMineZ update that renames or reshapes this simply leaves the
 * stock sizing in place instead of stopping the mod from loading.
 */
@Mixin(targets = "com.dragonminez.client.render.effects.AuraRenderer", remap = false)
public abstract class DmzAuraStatScaleMixin {

    @Inject(
            method = "getAuraScale(Lcom/dragonminez/common/stats/StatsData;[F)[F",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void xenopixels$scaleWithPower(StatsData stats, float[] modelScale,
                                                  CallbackInfoReturnable<float[]> cir) {
        float[] scaled = XenoAuraScaling.apply(cir.getReturnValue(), stats);
        if (scaled != cir.getReturnValue()) {
            cir.setReturnValue(scaled);
        }
    }
}
