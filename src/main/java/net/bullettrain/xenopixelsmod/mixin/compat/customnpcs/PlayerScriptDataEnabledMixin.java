package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.PlayerScriptsGate;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.constants.EnumScriptType;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * CustomNPCs {@code isEnabled} and {@code getLanguage} read
 * {@code ScriptController.Instance.playerScripts}, so a detached GUI copy's
 * Enabled toggle never reached {@code runScript}. Mirror {@code setEnabled}
 * onto the global handler even when this copy has a player (dedicated GUI).
 */
@Mixin(targets = "noppes.npcs.controllers.data.PlayerScriptData", remap = false)
public abstract class PlayerScriptDataEnabledMixin {

    @Shadow
    private boolean enabled;

    @Shadow
    private String scriptLanguage;

    @Shadow
    private Player player;

    @Shadow
    public abstract boolean isEnabled();

    @Shadow
    public abstract List<ScriptContainer> getScripts();

    @Inject(method = "getEnabled()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void xenopixels$readThisEnabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.enabled);
    }

    @Inject(method = "getLanguage()Ljava/lang/String;", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void xenopixels$readThisLanguage(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(this.scriptLanguage == null ? "" : this.scriptLanguage);
    }

    @Inject(method = "setEnabled(Z)V", at = @At("TAIL"), remap = false, require = 1)
    private void xenopixels$mirrorGlobalEnabled(boolean value, CallbackInfo ci) {
        ScriptController controller = ScriptController.Instance;
        if (controller == null || controller.playerScripts == null) {
            return;
        }
        if (controller.playerScripts == (Object) this) {
            return;
        }
        controller.playerScripts.setEnabled(value);
    }

    @Inject(method = "isEnabled()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void xenopixels$runtimeEnabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(PlayerScriptsGate.allow(
                ScriptController.HasStart, this.enabled, globalEnabled(), clientPlayer()));
    }

    @Inject(method = "runScript(Lnoppes/npcs/constants/EnumScriptType;Lnet/minecraftforge/eventbus/api/Event;)V",
            at = @At("HEAD"), remap = false, require = 1)
    private void xenopixels$logSkip(EnumScriptType type, Object event, CallbackInfo ci) {
        if (isEnabled()) {
            return;
        }
        int scripts = 0;
        List<?> local = getScripts();
        if (local != null) {
            scripts = local.size();
        }
        ScriptController controller = ScriptController.Instance;
        if (scripts == 0 && controller != null && controller.playerScripts != null
                && controller.playerScripts != (Object) this) {
            List<?> global = controller.playerScripts.getScripts();
            if (global != null) {
                scripts = global.size();
            }
        }
        PlayerScriptsGate.logSkipOnce(
                ScriptController.HasStart, this.enabled, globalEnabled(), scripts);
    }

    private boolean globalEnabled() {
        ScriptController controller = ScriptController.Instance;
        if (controller == null || controller.playerScripts == null) {
            return this.enabled;
        }
        if (controller.playerScripts == (Object) this) {
            return this.enabled;
        }
        return controller.playerScripts.getEnabled();
    }

    private boolean clientPlayer() {
        return this.player != null && this.player.level() != null && this.player.level().isClientSide;
    }
}
