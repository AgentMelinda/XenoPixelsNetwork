package net.bullettrain.xenopixelsmod.client.plot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD layer that drives the plot UI modules.
 *
 * <p>Registered with {@code RegisterGuiLayersEvent} like every other XenoPixels overlay. It owns no
 * plot state — it only advances the {@link PlotUiManager} and hands it the frame. Adding a plot
 * overlay means registering a {@link PlotUiModule}, never touching this class.</p>
 *
 * <p>{@code tick} is driven from the render pass rather than a separate client-tick hook so the UI
 * has a single registration point. The modules' tick work is a selection lookup, which is cheap
 * enough that a per-frame cadence is not a concern.</p>
 */
public final class PlotHudOverlay {

    static {
        // Registered here rather than at the call site so the layer works regardless of which
        // event registers it; register() is idempotent per id.
        PlotUiManager.get().register(new PlotSelectionHudModule());
        PlotUiManager.get().register(new PlotBoundaryHudModule());
    }

    private PlotHudOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (graphics == null) {
            return;
        }
        PlotUiManager manager = PlotUiManager.get();
        manager.tick();
        float partialTick = deltaTracker == null ? 0.0F : deltaTracker.getGameTimeDeltaPartialTick(false);
        manager.render(graphics, partialTick);
    }
}