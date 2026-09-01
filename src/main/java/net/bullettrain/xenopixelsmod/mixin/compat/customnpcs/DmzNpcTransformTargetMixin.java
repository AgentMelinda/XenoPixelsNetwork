package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.util.TransformationsHelper;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets synthetic NPC players resolve their configured target without player skill/mastery data. */
@Mixin(value = TransformationsHelper.class, remap = false)
public abstract class DmzNpcTransformTargetMixin {
    @Inject(method = "getNextAvailableForm(Lcom/dragonminez/common/stats/StatsData;)Lcom/dragonminez/common/config/FormConfig$FormData;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$useNpcTransformTarget(
            StatsData stats, CallbackInfoReturnable<FormConfig.FormData> callback) {
        FormConfig.FormData target = NpcFullDmzRenderer.transformTarget(stats);
        if (target != null) callback.setReturnValue(target);
    }

    @Inject(method = "getNextAvailableStackForm(Lcom/dragonminez/common/stats/StatsData;)Lcom/dragonminez/common/config/FormConfig$FormData;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$useNpcStackTransformTarget(
            StatsData stats, CallbackInfoReturnable<FormConfig.FormData> callback) {
        FormConfig.FormData target = NpcFullDmzRenderer.transformTarget(stats);
        if (target != null) callback.setReturnValue(target);
    }
}
