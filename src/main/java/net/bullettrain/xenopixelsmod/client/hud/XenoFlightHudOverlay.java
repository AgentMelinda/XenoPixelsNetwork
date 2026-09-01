package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.flight.ClientFlightState;
import net.bullettrain.xenopixelsmod.client.flight.XenoFlightControls;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Instrument strip for a seated pilot: throttle, flaps, speed, altitude and controller status.
 *
 * <p>Every value comes from the authoritative snapshot in {@link ClientFlightState}, never from
 * the client's own idea of what it commanded. A throttle the server refused therefore visibly
 * does not move, which is the point — a HUD that shows the request rather than the result would
 * quietly lie whenever power was short or flight was disengaged.
 *
 * <p>Strings are rebuilt only when the underlying numbers change. The snapshot updates a few
 * times a second while the HUD draws every frame, so formatting per frame would be pure waste.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoFlightHudOverlay {

    private static final int PANEL_WIDTH = 128;
    private static final int BAR_HEIGHT = 6;

    private static final int COLOR_PANEL = 0x88000B14;
    private static final int COLOR_EDGE = 0xFF2C6D8F;
    private static final int COLOR_LABEL = 0xFF8EC6E4;
    private static final int COLOR_THROTTLE = 0xFF52D1FF;
    private static final int COLOR_FLAP = 0xFF7BE58C;
    private static final int COLOR_FLAP_TARGET = 0x66FFFFFF;
    private static final int COLOR_WARN = 0xFFFF9A3C;
    private static final int COLOR_ALERT = 0xFFFF5D5D;

    /** Cached formatted line, rebuilt only when its inputs change. */
    private String speedLine = "";
    private double cachedSpeed = Double.NaN;
    private double cachedAltitude = Double.NaN;

    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        if (!XenoClientConfig.flightHudEnabled) return;
        if (!XenoFlightControls.seated()) return;

        AeroStateSnapshot state = ClientFlightState.get();
        if (state == null) return;

        // Sized for the warning row, so the panel never has text hanging outside its border.
        int width = PANEL_WIDTH;
        int height = 78;
        int x = 8;
        int y = graphics.guiHeight() - height - 8;

        HudDraw.fillRect(graphics, x, y, width, height, COLOR_PANEL);
        HudDraw.borderRect(graphics, x, y, width, height, COLOR_EDGE, 1);

        int inner = x + 6;
        int lineY = y + 5;
        graphics.drawString(minecraft.font, "FLIGHT", inner, lineY, COLOR_LABEL, false);

        String mode = XenoClientConfig.flightMouseAim ? "MOUSE" : "KEYS";
        graphics.drawString(minecraft.font, mode, x + width - 6 - minecraft.font.width(mode), lineY,
                COLOR_LABEL, false);

        lineY += 12;
        bar(graphics, inner, lineY, width - 12, state.throttle(), COLOR_THROTTLE);
        graphics.drawString(minecraft.font,
                String.format(Locale.ROOT, "THR %3.0f%%", state.throttle() * 100.0),
                inner, lineY + BAR_HEIGHT + 1, COLOR_LABEL, false);

        lineY += 20;
        bar(graphics, inner, lineY, width - 12, state.flap(), COLOR_FLAP);
        // A thin tick at the commanded setting, so travelling flaps read as travelling rather
        // than as a bar that mysteriously disagrees with the key that was just pressed.
        int tick = inner + (int) Math.round((width - 12) * Math.max(0.0, Math.min(1.0, state.flapTarget())));
        HudDraw.fillRect(graphics, Math.min(tick, inner + width - 13), lineY - 1, 1, BAR_HEIGHT + 2,
                COLOR_FLAP_TARGET);
        graphics.drawString(minecraft.font,
                String.format(Locale.ROOT, "FLP %3.0f%%%s", state.flap() * 100.0,
                        state.autoFlap() ? " AUTO" : ""),
                inner, lineY + BAR_HEIGHT + 1, COLOR_LABEL, false);

        lineY += 20;
        graphics.drawString(minecraft.font, speedLine(minecraft, state), inner, lineY, COLOR_LABEL, false);

        // Right-aligned on the speed row: the air brake is a control the pilot is holding down,
        // and a held control with no readout is indistinguishable from one that is not working.
        if (state.airBrake()) {
            String brake = "BRK";
            graphics.drawString(minecraft.font, brake,
                    x + width - 6 - minecraft.font.width(brake), lineY, COLOR_WARN, false);
        }

        // Its own line. Sharing the speed row meant a four-digit altitude and a word like
        // "DISENGAGED" drew straight through each other.
        String warning = warning(state);
        if (warning != null) {
            int color = state.powerTier() == AeroBus.PowerTier.OFFLINE ? COLOR_ALERT : COLOR_WARN;
            graphics.drawString(minecraft.font, warning, inner, lineY + 11, color, false);
        }
    }

    private String speedLine(Minecraft minecraft, AeroStateSnapshot state) {
        double altitude = minecraft.player == null ? 0.0 : minecraft.player.getY();
        if (state.actualSpeed() != cachedSpeed || Math.floor(altitude) != cachedAltitude) {
            cachedSpeed = state.actualSpeed();
            cachedAltitude = Math.floor(altitude);
            speedLine = String.format(Locale.ROOT, "%.0f m/s   ALT %.0f", cachedSpeed, cachedAltitude);
        }
        return speedLine;
    }

    /** The single most important thing wrong, or null when nothing is. */
    private static String warning(AeroStateSnapshot state) {
        if (state.powerTier() == AeroBus.PowerTier.OFFLINE) return "NO POWER";
        if (state.status() != null && state.status().contains("STALL")) return "STALL";
        if (state.powerTier() == AeroBus.PowerTier.CRITICAL) return "PWR LOW";
        if (!state.flightEngaged()) return "DISENGAGED";
        if (state.linkCount() > 0 && state.healthyLinkCount() < state.linkCount()) return "ENGINE";
        return null;
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, double fraction, int color) {
        HudDraw.fillRect(graphics, x, y, width, BAR_HEIGHT, 0x66000000);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, fraction)));
        HudDraw.fillRect(graphics, x, y, filled, BAR_HEIGHT, color);
        HudDraw.borderRect(graphics, x, y, width, BAR_HEIGHT, 0x55FFFFFF, 1);
    }
}
