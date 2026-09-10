package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.client.hud.DmzHudThemeTextures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Remaps every scouter helper to the matching Xeno atlas while the theme is active. */
@Mixin(targets = "com.dragonminez.client.gui.hud.ScouterHUD", remap = false)
public abstract class DmzScouterThemeMixin {
    @ModifyVariable(
            method = {
                    "renderScouterFrame",
                    "renderCustomNumbers",
                    "renderDirectionIcon",
                    "renderEntityInfo"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 4
    )
    private static ResourceLocation xenopixels$themeScouter(ResourceLocation texture) {
        return DmzHudThemeTextures.remap(texture);
    }
}
