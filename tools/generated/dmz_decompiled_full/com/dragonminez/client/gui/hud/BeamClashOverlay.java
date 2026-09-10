package com.dragonminez.client.gui.hud;

import com.dragonminez.client.clash.ClientBeamClashState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BeamClashOverlay {
   private static final ResourceLocation BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/kicharge_hud.png");
   private static final int SRC_W = 148;
   private static final int SRC_H = 14;
   private static final int FILL_V = 14;
   private static final int ATLAS = 256;
   private static final int PANEL_W = 244;
   private static final int PANEL_H = 84;
   private static final int BAR_W = 208;
   private static final int BAR_H = 16;
   private static final int PANEL_BG = -1073082856;
   private static final int PANEL_INNER = 1610612736;
   private static final int YOU_LABEL = -8396545;
   private static final int FOE_LABEL = -38047;
   private static final int FOE_FILL = 14697531;
   private static final int SWEET_GREEN = 3727435;
   private static final int KNOB = -1;
   public static final Layer HUD_BEAM_CLASH = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.getDebugOverlay().showDebugScreen()) {
         if (ClientBeamClashState.isActive()) {
            float advantage = Mth.clamp(ClientBeamClashState.advantage(), 0.0F, 1.0F);
            float phase = Mth.clamp(ClientBeamClashState.meterPhase(), 0.0F, 1.0F);
            float sweetLow = Mth.clamp(ClientBeamClashState.sweetLow(), 0.0F, 1.0F);
            float sweetHigh = Mth.clamp(ClientBeamClashState.sweetHigh(), 0.0F, 1.0F);
            int beamRgb = ClientBeamClashState.beamColor() & 16777215;
            int beamArgb = 0xFF000000 | beamRgb;
            int panelX = (width - 244) / 2;
            int panelY = height - 84 - 34;
            int barX = panelX + 18;
            int tugY = panelY + 24;
            int sweepY = panelY + 52;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.fill(panelX, panelY, panelX + 244, panelY + 84, -1073082856);
            guiGraphics.fill(panelX + 2, panelY + 2, panelX + 244 - 2, panelY + 84 - 2, 1610612736);
            guiGraphics.fill(panelX, panelY, panelX + 244, panelY + 1, beamArgb);
            guiGraphics.fill(panelX, panelY + 84 - 1, panelX + 244, panelY + 84, beamArgb);
            Component title = Component.translatable("hud.dragonminez.beam_clash_title");
            guiGraphics.drawString(mc.font, title, (width - mc.font.width(title)) / 2, panelY + 6, -1, true);
            int split = Math.round(208.0F * advantage);
            drawBarFrame(guiGraphics, barX, tugY);
            setColor(beamRgb);
            drawBarFill(guiGraphics, barX, tugY, 0, split);
            setColor(14697531);
            drawBarFill(guiGraphics, barX, tugY, split, 208 - split);
            resetColor();
            guiGraphics.fill(barX + split - 1, tugY - 2, barX + split + 1, tugY + 16 + 2, -1);
            guiGraphics.drawString(mc.font, "YOU", barX, tugY - 10, -8396545, true);
            guiGraphics.drawString(mc.font, "FOE", barX + 208 - mc.font.width("FOE"), tugY - 10, -38047, true);
            drawBarFrame(guiGraphics, barX, sweepY);
            int sweetPx0 = Math.round(208.0F * sweetLow);
            int sweetPxW = Math.round(208.0F * sweetHigh) - sweetPx0;
            setColor(3727435);
            drawBarFill(guiGraphics, barX, sweepY, sweetPx0, sweetPxW);
            resetColor();
            int sweetX1 = barX + sweetPx0;
            int sweetX2 = sweetX1 + sweetPxW;
            guiGraphics.fill(sweetX1, sweepY, sweetX1 + 1, sweepY + 16, -13049781);
            guiGraphics.fill(sweetX2 - 1, sweepY, sweetX2, sweepY + 16, -13049781);
            int markerX = barX + Math.round(208.0F * phase);

            for (int r = 0; r < 5; r++) {
               int hw = 5 - r;
               guiGraphics.fill(markerX - hw, sweepY - 7 + r, markerX + hw + 1, sweepY - 6 + r, beamArgb);
            }

            guiGraphics.fill(markerX, sweepY - 2, markerX + 1, sweepY + 16 + 2, -1);
            Component hint = Component.translatable("hud.dragonminez.beam_clash_hint");
            guiGraphics.drawString(mc.font, hint, (width - mc.font.width(hint)) / 2, sweepY + 16 + 4, -4601898, true);
         }
      }
   };

   private static void drawBarFrame(GuiGraphics g, int x, int y) {
      setColor(16777215);
      g.blit(BAR_TEXTURE, x, y, 208, 16, 0.0F, 0.0F, 148, 14, 256, 256);
      resetColor();
   }

   private static void drawBarFill(GuiGraphics g, int barX, int y, int pxOffset, int pxWidth) {
      if (pxWidth > 0) {
         float frac0 = (float)pxOffset / 208.0F;
         float fracW = (float)pxWidth / 208.0F;
         g.blit(BAR_TEXTURE, barX + pxOffset, y, pxWidth, 16, 148.0F * frac0, 14.0F, Math.round(148.0F * fracW), 14, 256, 256);
      }
   }

   private static void setColor(int rgb) {
      RenderSystem.setShaderColor((float)(rgb >> 16 & 0xFF) / 255.0F, (float)(rgb >> 8 & 0xFF) / 255.0F, (float)(rgb & 0xFF) / 255.0F, 1.0F);
   }

   private static void resetColor() {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
