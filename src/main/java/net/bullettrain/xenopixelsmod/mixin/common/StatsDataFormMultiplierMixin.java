package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies this server's global / per-form / per-stat power scales to DMZ form
 * and stack-form multipliers ({@link XenoServerConfig}).
 */
@Mixin(value = StatsData.class, remap = false)
public class StatsDataFormMultiplierMixin {

    @Inject(method = "getFormMultiplier", at = @At("RETURN"), cancellable = true)
    private void xenopixels$scaleFormMultiplier(String stat, CallbackInfoReturnable<Double> cir) {
        Double raw = cir.getReturnValue();
        if (raw == null) return;
        double scaled = XenoServerConfig.scaleFormMultiplier(raw, resolveActiveFormKey(false), stat);
        if (scaled != raw) {
            cir.setReturnValue(scaled);
        }
    }

    @Inject(method = "getStackFormMultiplier", at = @At("RETURN"), cancellable = true)
    private void xenopixels$scaleStackFormMultiplier(String stat, CallbackInfoReturnable<Double> cir) {
        Double raw = cir.getReturnValue();
        if (raw == null) return;
        double scaled = XenoServerConfig.scaleFormMultiplier(raw, resolveActiveFormKey(true), stat);
        if (scaled != raw) {
            cir.setReturnValue(scaled);
        }
    }

    /** {@code group.form} for the active (or stack) form, or null if base / unavailable. */
    private String resolveActiveFormKey(boolean stack) {
        try {
            StatsData self = (StatsData) (Object) this;
            Character ch = self.getCharacter();
            if (ch == null) return null;
            String group;
            String form;
            if (stack) {
                group = ch.getActiveStackFormGroup();
                form = ch.getActiveStackForm();
            } else {
                group = ch.getActiveFormGroup();
                form = ch.getActiveForm();
            }
            if (form == null || form.isEmpty() || "base".equalsIgnoreCase(form)) {
                return null;
            }
            if (group != null && !group.isEmpty()) {
                return group + "." + form;
            }
            return form;
        } catch (Throwable t) {
            return null;
        }
    }
}
