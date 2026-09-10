package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.hud.DmzHudThemeTextures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Uses the Xeno radar atlas while themed DMZ menus are enabled. */
@Mixin(targets = "com.dragonminez.client.events.RadarRenderEvent", remap = false)
public abstract class DmzRadarThemeMixin {
    @ModifyArg(
            method = "renderRadar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit("
                            + "Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                    remap = true
            ),
            index = 0,
            require = 3
    )
    private static ResourceLocation xenopixels$themeRadar(ResourceLocation texture) {
        return DmzHudThemeTextures.remap(texture);
    }
}
