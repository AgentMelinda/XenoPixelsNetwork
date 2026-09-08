package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.util.KeyBinds;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops BT3's Y ki blast from also using an item or placing a block. */
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class MinecraftPadUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void xenopixels$withholdPadUse(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!XenoPadInput.suppressesUseItem()) return;
        try {
            if (KeyBinds.isPhysicallyDown(minecraft.options.keyUse)) return;
        } catch (Throwable ignored) {
        }
        ci.cancel();
    }
}
