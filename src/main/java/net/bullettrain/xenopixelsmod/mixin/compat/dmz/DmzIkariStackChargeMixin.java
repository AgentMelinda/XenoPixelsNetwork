package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.actionmode.StackFormModeHandler;
import net.bullettrain.xenopixelsmod.dmz.form.IkariStackRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The stack handler only asks whether a form and a stack are compatible when a form is active, so
 * in base any stack may be charged. Ikari needs Super Saiyan 1 to 3 under it, so the charge is
 * refused here when there is no such form. Verified against dragonminez-2.1.3:
 * {@code StackFormModeHandler.canCharge(ServerPlayer, StatsData)}.
 */
@Mixin(value = StackFormModeHandler.class, remap = false)
public abstract class DmzIkariStackChargeMixin {

    @Inject(method = "canCharge", at = @At("HEAD"), cancellable = true)
    private void xenopixels$ikariNeedsSuperSaiyan(ServerPlayer player, StatsData data,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (data == null || data.getCharacter() == null) return;
        var character = data.getCharacter();
        String stackGroup = character.hasActiveStackForm()
                ? character.getActiveStackFormGroup() : character.getSelectedStackFormGroup();
        if (!IkariStackRules.isIkari(stackGroup)) return;
        boolean allowed = character.hasActiveForm()
                && IkariStackRules.allows(character.getActiveFormGroup(), character.getActiveForm());
        if (!allowed) cir.setReturnValue(false);
    }
}
