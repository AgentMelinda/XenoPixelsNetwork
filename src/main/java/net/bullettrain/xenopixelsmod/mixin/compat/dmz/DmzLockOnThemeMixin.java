package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.hud.DmzHudThemeTextures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Uses the Xeno lock-on reticle while themed DMZ menus are enabled. */
@Mixin(targets = "com.dragonminez.client.events.LockOnEvent", remap = false)
public abstract class DmzLockOnThemeMixin {
    @ModifyArg(
            method = "lambda$static$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit("
                            + "Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V",
                    remap = true
            ),
            index = 0,
            require = 1
    )
    private static ResourceLocation xenopixels$themeLockOn(ResourceLocation texture) {
        return DmzHudThemeTextures.remap(texture);
    }
}
