package net.bullettrain.xenopixelsmod.mixin.compat.controlify;

import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.utils.HoldRepeatHelper;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets BT3 mode own only the physical inputs used by its active layout. */
@Mixin(targets = "dev.isxander.controlify.ingame.InGameInputHandler", remap = false)
public abstract class ControlifyBt3ModeMixin {
    private static boolean xenopixels$allow(InputBinding binding) {
        return !XenoPadInput.bt3ModeActive()
                || !net.bullettrain.xenopixelsmod.client.pad.XenoPadBinds.conflicts(binding);
    }
    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/api/bind/InputBinding;justPressed()Z"),
            require = 0
    )
    private boolean xenopixels$filterDirectPresses(InputBinding binding) {
        return xenopixels$allow(binding) && binding.justPressed();
    }

    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/api/bind/InputBinding;justReleased()Z"),
            require = 0
    )
    private boolean xenopixels$filterDirectReleases(InputBinding binding) {
        return xenopixels$allow(binding) && binding.justReleased();
    }

    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/api/bind/InputBinding;digitalNow()Z"),
            require = 0
    )
    private boolean xenopixels$filterDirectHolds(InputBinding binding) {
        return xenopixels$allow(binding) && binding.digitalNow();
    }

    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/api/bind/InputBinding;analogueNow()F"),
            require = 0
    )
    private float xenopixels$filterDirectAxes(InputBinding binding) {
        return xenopixels$allow(binding) ? binding.analogueNow() : 0f;
    }

    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/utils/HoldRepeatHelper;shouldAction(Ldev/isxander/controlify/api/bind/InputBinding;)Z"),
            require = 0
    )
    private boolean xenopixels$filterRepeatedActions(HoldRepeatHelper helper, InputBinding binding) {
        return xenopixels$allow(binding) && helper.shouldAction(binding);
    }
}
