package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Substitutes DragonMineZ menu textures without changing any DragonMineZ screen behaviour.
 *
 * <p>This hooks the <b>terminal</b> blit -- the one every {@code blit(ResourceLocation, ...)}
 * overload funnels into -- rather than the two convenience overloads it used to. That is not tidying:
 * the buttons were unreachable before. {@code CustomTextureButton} draws through the six-argument
 * {@code blit}, which delegates to {@code (ResourceLocation;IIIFFIIII)V} and from there straight to
 * this method, touching neither of the previously hooked overloads. So no icon draw was ever offered
 * for remapping, and the nav row stayed stock however much art was generated for it.
 *
 * <p>One hook now covers panels, headers, nameplates and icons alike.
 *
 * <p>This sees <em>every</em> textured GUI draw in the game, so it must stay cheap and inert outside
 * DMZ's menus. {@link DmzMenuThemeState#remap} returns its argument untouched unless a DragonMineZ
 * menu is mid-render and the texture belongs to DragonMineZ, which is two reference comparisons in
 * the common case.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsDmzMenuThemeMixin {

    @ModifyVariable(
            method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIIIIIFFII)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ResourceLocation xenopixels$remapBlit(ResourceLocation value,
                                                  ResourceLocation texture,
                                                  int x1, int x2, int y1, int y2,
                                                  int blitOffset,
                                                  int uWidth, int vHeight,
                                                  float u, float v,
                                                  int textureWidth, int textureHeight) {
        // x1 is the left edge, which is what the left/right panel split keys off.
        return DmzMenuThemeState.remap(value, x1);
    }
}
