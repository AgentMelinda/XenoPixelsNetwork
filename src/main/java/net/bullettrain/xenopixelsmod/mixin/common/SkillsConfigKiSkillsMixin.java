package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.config.SkillsConfig;
import java.util.List;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ's ki attacks list and {@code /dmztech} only accept ids in {@code SkillsConfig.kiSkills}.
 * As {@link SkillsConfigStrikeSkillsMixin} does for strikes, this adds the catalog's ki attacks to
 * the live list, so they are listed and can be given.
 */
@Mixin(value = SkillsConfig.class, remap = false)
public abstract class SkillsConfigKiSkillsMixin {

    @Shadow
    @Final
    private List<String> kiSkills;

    @Inject(method = "getKiSkills", at = @At("HEAD"), require = 0)
    private void xenopixels$ensureCatalogKi(CallbackInfoReturnable<List<String>> cir) {
        V3NativeKi.installInto(this.kiSkills);
    }
}
