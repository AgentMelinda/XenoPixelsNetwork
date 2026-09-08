package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.util.KeyBinds;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes only the vanilla actions that share BT3 face buttons. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPadInputMixin {
    @Shadow public Input input;

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/Input;tick(ZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void xenopixels$arbitrateBt3FaceButtons(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) return;
        if (XenoPadInput.suppressesJump()
                && !physicallyDown(minecraft.options.keyJump)) {
            input.jumping = false;
        }
        if (XenoPadInput.suppressesSneak()
                && !physicallyDown(minecraft.options.keyShift)) {
            input.shiftKeyDown = false;
        }
        if (XenoPadInput.suppressesSprint()
                && !physicallyDown(minecraft.options.keySprint)) {
            minecraft.options.keySprint.setDown(false);
        }
    }

    private static boolean physicallyDown(net.minecraft.client.KeyMapping mapping) {
        try {
            return KeyBinds.isPhysicallyDown(mapping);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
