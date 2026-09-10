package com.dragonminez.client.gui.hud;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class TechniqueChargeOverlay {
   private static final ResourceLocation CHARGE_HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/kicharge_hud.png");
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static volatile float currentChargePercent = 0.0F;
   public static final Layer HUD_TECHNIQUE_CHARGE = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               float targetChargePercent = data.getTechniques().getTechniqueChargePercent();
               if (targetChargePercent <= 0.0F && !data.getTechniques().isTechniqueCharging()) {
                  currentChargePercent = 0.0F;
               } else if (data.getTechniques().getSelectedTechnique() instanceof KiAttackData kiAttack) {
                  if (!kiAttack.isInstantCast()) {
                     float scale = 1.125F;
                     int barW = Math.round(148.0F * scale);
                     int x = (width - barW) / 2;
                     int y = height - 72;
                     float lerped = currentChargePercent + (targetChargePercent - currentChargePercent) * 0.2F;
                     if (Math.abs(lerped - targetChargePercent) <= 0.3F) {
                        lerped = targetChargePercent;
                     }

                     currentChargePercent = Math.max(0.0F, Math.min(200.0F, lerped));
                     float normalFillRatio = Mth.clamp(currentChargePercent / 100.0F, 0.0F, 1.0F);
                     int normalPixels = Math.round(148.0F * normalFillRatio);
                     float overMax = Math.max(1.0F, 75.0F);
                     float overchargeRatio = Mth.clamp((currentChargePercent - 100.0F) / overMax, 0.0F, 1.0F);
                     int overchargePixels = Math.round(148.0F * overchargeRatio);
                     RenderSystem.enableBlend();
                     RenderSystem.defaultBlendFunc();
                     RenderSystem.setShader(GameRenderer::getPositionTexShader);
                     RenderSystem.setShaderTexture(0, CHARGE_HUD_TEXTURE);
                     RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                     guiGraphics.pose().pushPose();
                     guiGraphics.pose().translate((float)x, (float)y, 0.0F);
                     guiGraphics.pose().scale(scale, scale, 1.0F);
                     guiGraphics.blit(CHARGE_HUD_TEXTURE, 0, 0, 0.0F, 0.0F, 145, 14, 256, 256);
                     int kiColor = kiAttack.getColorExterior();
                     float r = (float)(kiColor >> 16 & 0xFF) / 255.0F;
                     float g = (float)(kiColor >> 8 & 0xFF) / 255.0F;
                     float b = (float)(kiColor & 0xFF) / 255.0F;
                     if (normalPixels > 0) {
                        RenderSystem.setShaderColor(r, g, b, 1.0F);
                        guiGraphics.blit(CHARGE_HUD_TEXTURE, 0, 0, 0.0F, 14.0F, normalPixels, 14, 256, 256);
                     }

                     if (overchargePixels > 0) {
                        RenderSystem.setShaderColor(r * 0.5F, g * 0.5F, b * 0.5F, 1.0F);
                        guiGraphics.blit(CHARGE_HUD_TEXTURE, 0, 0, 0.0F, 14.0F, overchargePixels, 14, 256, 256);
                     }

                     RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                     guiGraphics.pose().popPose();
                     drawChargeStatus(guiGraphics, mc.font, currentChargePercent, width, y);
                  }
               }
            }
         });
      }
   };

   private static void drawChargeStatus(GuiGraphics guiGraphics, Font font, float percent, int width, int barY) {
      boolean charging = percent < 99.99F;
      MutableComponent hint = charging ? tr("technique.charge.charging") : tr("technique.charge.overcharging");
      int color = charging ? -1 : -11776;
      MutableComponent pct = Component.literal(Math.round(percent) + "%").withStyle(Style.EMPTY.withFont(DMZ_FONT));
      int pctY = barY - 4 - 9;
      int hintY = pctY - 2 - 9;
      guiGraphics.drawString(font, hint, (width - font.width(hint)) / 2, hintY, color, true);
      guiGraphics.drawString(font, pct, (width - font.width(pct)) / 2, pctY, color, true);
   }

   private static MutableComponent tr(String key) {
      return Component.translatable(key).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }
}
