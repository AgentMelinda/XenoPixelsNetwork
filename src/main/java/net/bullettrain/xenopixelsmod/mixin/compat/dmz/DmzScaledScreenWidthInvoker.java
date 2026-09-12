package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Read access to the logical width DragonMineZ's menus lay themselves out in.
 *
 * <p>{@code ScaledScreen.getUiWidth()} is {@code protected}, and every V-menu positions its panels
 * relative to it -- {@code ConfigMenuScreen} puts its left panel at {@code getUiWidth() / 2 - 143}
 * and its right at {@code getUiWidth() / 2 + 2}. The neon theme has to know that midpoint to tell
 * the two apart, because the only thing the texture hook sees is the draw's x coordinate.
 *
 * <p>Without it the split has to guess, and the guess in use before this -- a literal 160, half of
 * {@code ScaledScreen}'s 320 minimum -- is wrong for every window wide enough to give the menus a
 * larger canvas. At a UI width of 640 the settings page's left panel is drawn at x=177 and would be
 * handed the right panel's art.
 *
 * <p>An {@code @Invoker} rather than a reimplementation of the scale maths: the value is computed
 * from the window size, DragonMineZ's configured menu-scale multiplier and its own clamping, and a
 * copy of that arithmetic here would be a second source of truth that drifts.
 *
 * <p>Declared against the class by name because this is a compat mixin; it is inert when
 * DragonMineZ is absent.
 */
@Mixin(targets = "com.dragonminez.client.gui.character.util.ScaledScreen", remap = false)
public interface DmzScaledScreenWidthInvoker {

    @Invoker("getUiWidth")
    int xeno$getUiWidth();
}
