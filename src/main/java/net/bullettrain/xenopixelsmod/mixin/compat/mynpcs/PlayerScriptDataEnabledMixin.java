package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.constants.EnumScriptType;
import espi.mynpcs.controllers.ScriptContainer;
import espi.mynpcs.controllers.ScriptController;
import net.bullettrain.xenopixelsmod.compat.npc.PlayerScriptsGate;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * My NPCs' player-script GUI edits a detached {@code PlayerScriptData}.
 * {@code getEnabled}/{@code getLanguage}/{@code isEnabled} all read
 * {@code ScriptController.Instance.playerScripts}, so the Enabled button showed No
 * and {@code runScript} ignored {@code setEnabled} on the copy the GUI actually edits.
 *
 * <p>Read/write this instance in the GUI, mirror {@code setEnabled} onto the global
 * handler immediately (including when the GUI copy has a player — dedicated
 * servers attach one), and let {@code isEnabled} see that global flag (or this
 * copy's own flag) once the server has started so {@code login}/{@code chat} can run.
 */
@Mixin(targets = "espi.mynpcs.controllers.data.PlayerScriptData", remap = false)
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

    @Inject(method = "runScript(Lespi/mynpcs/constants/EnumScriptType;Lnet/neoforged/bus/api/Event;)V",
            at = @At("HEAD"), remap = false, require = 1)
    private void xenopixels$logSkip(EnumScriptType type, Event event, CallbackInfo ci) {
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
