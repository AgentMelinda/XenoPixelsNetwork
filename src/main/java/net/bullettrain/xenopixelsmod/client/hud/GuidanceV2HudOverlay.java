package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2Bus;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2SurfaceMode;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.flight.ClientFlightState;
import net.bullettrain.xenopixelsmod.client.flight.XenoFlightControls;
import net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

/** Seated v2 instruments. Drawn only while {@link GuidanceV2Client#isV2()} is true. */
@OnlyIn(Dist.CLIENT)
public final class GuidanceV2HudOverlay {
    private static final int PANEL_WIDTH = 148;
    private static final int BAR_HEIGHT = 6;
    private static final int COLOR_PANEL = 0x99060A14;
    private static final int COLOR_EDGE = 0xFF3DE0FF;
    private static final int COLOR_EDGE_2 = 0xFFFF6AD5;
    private static final int COLOR_CYAN = 0xFF5CE1FF;
    private static final int COLOR_AMBER = 0xFFFFC14A;
    private static final int COLOR_MAGENTA = 0xFFFF6AD5;
    private static final int COLOR_FLAP = 0xFF7BE58C;
    private static final int COLOR_ALERT = 0xFFFF5D5D;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (!GuidanceV2Client.isV2()) {
            return;
        }
        if (!XenoClientConfig.flightHudEnabled) {
            return;
        }
        if (!XenoFlightControls.seated()) {
            return;
        }

        AeroStateSnapshot state = ClientFlightState.get();
        if (state == null) {
            return;
        }
        GuidanceV2Bus bus = GuidanceV2Client.bus();
        GuidanceV2SurfaceMode mode = bus != null ? bus.mode() : GuidanceV2Client.surfaceMode(state.controllerPos());
        boolean flapsOnly = mode != null && mode.flapsOnly();

        int width = PANEL_WIDTH;
        int height = 96;
        int x = 8;
        int y = graphics.guiHeight() - height - 8;

        HudDraw.fillRect(graphics, x, y, width, height, COLOR_PANEL);
        GuidanceV2ArtLayout.blitNineSlice(graphics, GuidanceV2ArtLayout.PANEL,
                x, y, width, height, GuidanceV2ArtLayout.PANEL_CORNER);

        int inner = x + 8;
        int lineY = y + 7;
        graphics.drawString(minecraft.font, "GUIDANCE v2", inner, lineY, COLOR_CYAN, false);
        String modeLabel = flapsOnly ? "FLAPS" : "MIX";
        GuidanceV2ArtLayout.blit(graphics, GuidanceV2ArtLayout.MODE_CHIP,
                x + width - 8 - GuidanceV2ArtLayout.MODE_CHIP.w(), lineY - 2);
        graphics.drawString(minecraft.font, modeLabel,
                x + width - 8 - minecraft.font.width(modeLabel), lineY, COLOR_MAGENTA, false);

        lineY += 14;
        if (flapsOnly) {
            bar(graphics, inner, lineY, width - 16, state.flap(), COLOR_FLAP);
            graphics.drawString(minecraft.font,
                    String.format(Locale.ROOT, "FLAPS %3.0f%%", state.flap() * 100.0),
                    inner, lineY + BAR_HEIGHT + 1, COLOR_CYAN, false);
        } else {
            bar(graphics, inner, lineY, width - 16, state.throttle(), COLOR_CYAN);
            graphics.drawString(minecraft.font,
                    String.format(Locale.ROOT, "THR %3.0f%%", state.throttle() * 100.0),
                    inner, lineY + BAR_HEIGHT + 1, COLOR_CYAN, false);
        }

        lineY += 20;
        if (!flapsOnly) {
            bar(graphics, inner, lineY, width - 16, state.flap(), COLOR_FLAP);
            graphics.drawString(minecraft.font,
                    String.format(Locale.ROOT, "FLP %3.0f%%%s", state.flap() * 100.0,
                            state.autoFlap() ? " AUTO" : ""),
                    inner, lineY + BAR_HEIGHT + 1, COLOR_CYAN, false);
            lineY += 20;
        }

        String speed = String.format(Locale.ROOT, "%.0f m/s   ALT %.0f",
                state.actualSpeed(), minecraft.player.getY());
        graphics.drawString(minecraft.font, speed, inner, lineY, COLOR_AMBER, false);

        String warning = warning(state, bus, flapsOnly);
        if (warning != null) {
            GuidanceV2ArtLayout.blit(graphics, GuidanceV2ArtLayout.WARN, inner, lineY + 10);
            graphics.drawString(minecraft.font, warning, inner, lineY + 12, COLOR_ALERT, false);
        }
    }

    private static String warning(AeroStateSnapshot state, GuidanceV2Bus bus, boolean flapsOnly) {
        if (state.mode() != ControllerMode.FLIGHT) {
            return "MISSILE MODE";
        }
        if (state.powerTier() == AeroBus.PowerTier.OFFLINE) {
            return "NO POWER";
        }
        if (state.status() != null && state.status().contains("STALL")) {
            return "STALL";
        }
        if (bus != null && bus.missingAttitudeSurfaces()) {
            return "NO P/R/Y SURFACES";
        }
        if (flapsOnly && bus != null && bus.cannotBleedSpeed()) {
            return "NO SPEED BLEED";
        }
        if (state.powerTier() == AeroBus.PowerTier.CRITICAL) {
            return "PWR LOW";
        }
        if (!state.flightEngaged()) {
            return "DISENGAGED";
        }
        return null;
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, double fraction, int color) {
        GuidanceV2ArtLayout.Region fill = color == COLOR_FLAP
                ? GuidanceV2ArtLayout.BAR_FLAP : GuidanceV2ArtLayout.BAR_FULL;
        GuidanceV2ArtLayout.blitBar(graphics, GuidanceV2ArtLayout.BAR_EMPTY, x, y, width, BAR_HEIGHT, 1f);
        GuidanceV2ArtLayout.blitBar(graphics, fill, x, y, width, BAR_HEIGHT, (float) fraction);
    }
}
