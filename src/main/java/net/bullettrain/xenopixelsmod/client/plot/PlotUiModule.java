package net.bullettrain.xenopixelsmod.client.plot;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One independently toggleable unit of plot UI.
 *
 * <p>Modules are registered with {@link PlotUiManager}. The manager owns ordering and enablement;
 * a module owns its own state and drawing. Adding a plot overlay therefore never means editing the
 * manager.</p>
 */
public interface PlotUiModule {

    /** Stable identifier, used for logging and for per-module enablement. */
    String id();

    /** Whether this module should currently receive {@link #render} calls. */
    default boolean isEnabled() {
        return true;
    }

    /** Called once per client tick while enabled. */
    default void tick() {
    }

    /** Called once per frame while enabled. */
    default void render(GuiGraphics graphics, float partialTick) {
    }
}