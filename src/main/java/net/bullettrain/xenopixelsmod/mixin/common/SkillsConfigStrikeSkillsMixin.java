package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.config.SkillsConfig;
import net.bullettrain.xenopixelsmod.combat.technique.XenoStrikeSkills;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * DMZ's Attacks tab and {@code /dmztech} only list ids in {@code SkillsConfig.strikeSkills}.
 * Registry-only Xeno strikes never appear there. Mutate the live list so config sync and
 * Gson copies keep {@code xenopixelsmod:lift_combo} / rush combo.
 */
@Mixin(value = SkillsConfig.class, remap = false)
public abstract class SkillsConfigStrikeSkillsMixin {

    @Shadow
    @Final
    private List<String> strikeSkills;

    @Inject(method = "getStrikeSkills", at = @At("HEAD"))
    private void xenopixels$ensureXenoStrikes(CallbackInfoReturnable<List<String>> cir) {
        XenoStrikeSkills.installInto(this.strikeSkills);
    }
}
