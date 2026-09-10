package com.dragonminez.client.gui.hud;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Status;
import com.mojang.blaze3d.systems.RenderSystem;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class AlternativeHUD {
   private static final ResourceLocation hud = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/alternativehud.png");
   private static final ResourceLocation xvhud = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/xenoversehud.png");
   private static final ResourceLocation racialIcons = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/racial_icons.png");
   private static final HudBarAnimator HP_BAR = new HudBarAnimator();
   private static final HudBarAnimator KI_BAR = new HudBarAnimator();
   private static final HudBarAnimator STM_BAR = new HudBarAnimator();
   private static volatile float displayPowerRelease = 0.0F;
   private static volatile float lastSeenMaxHP = -1.0F;
   private static volatile float lastSeenMaxKi = -1.0F;
   private static volatile float lastSeenMaxStm = -1.0F;
   private static final float LERP_SPEED = 0.25F;
   private static final float BAR_MAX_WIDTH = 76.0F;
   private static final HudStatNumberAnimator HP_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.HEALTH);
   private static final HudStatNumberAnimator KI_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.KI);
   private static final HudStatNumberAnimator STM_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.STAMINA);
   static NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
   public static final Layer HUD_ALTERNATIVE = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         if (ConfigManager.getUserConfig().getAlternativeHud()) {
            StatsProvider.get(StatsCapability.INSTANCE, mc.player)
               .ifPresent(
                  data -> {
                     Character character = data.getCharacter();
                     Status status = data.getStatus();
                     Resources resources = data.getResources();
                     if (status.isHasCreatedCharacter()) {
                        float maxHP = Math.max(1.0F, (float)mc.player.getAttributeValue(Attributes.MAX_HEALTH));
                        float maxKi = Math.max(1.0F, data.getMaxEnergy());
                        float maxStm = Math.max(1.0F, data.getMaxStamina());
                        int powerRelease = resources.getPowerRelease();
                        int formRelease = resources.getActionCharge() < 10 ? 10 + resources.getActionCharge() : resources.getActionCharge();
                        String raceName = character.getRaceName();
                        String auraColor = character.getAuraColor();
                        FormConfig.FormData formData = character.getActiveStackForm() != null && !character.getActiveStackForm().isEmpty()
                           ? character.getActiveStackFormData()
                           : (character.getActiveForm() != null && !character.getActiveForm().isEmpty() ? character.getActiveFormData() : null);
                        if (formData != null && formData.getAuraColor() != null && !formData.getAuraColor().isEmpty()) {
                           auraColor = formData.getAuraColor();
                        }

                        float currentHP = mc.player.getHealth();
                        float currentKi = resources.getCurrentEnergy();
                        float currentStm = resources.getCurrentStamina();
                        float hpFraction = Mth.clamp(currentHP / maxHP, 0.0F, 1.0F);
                        float kiFraction = Mth.clamp(currentKi / maxKi, 0.0F, 1.0F);
                        float stmFraction = Mth.clamp(currentStm / maxStm, 0.0F, 1.0F);
                        if (lastSeenMaxHP != maxHP) {
                           HP_BAR.reset(hpFraction);
                           lastSeenMaxHP = maxHP;
                        }

                        if (lastSeenMaxKi != maxKi) {
                           KI_BAR.reset(kiFraction);
                           lastSeenMaxKi = maxKi;
                        }

                        if (lastSeenMaxStm != maxStm) {
                           STM_BAR.reset(stmFraction);
                           lastSeenMaxStm = maxStm;
                        }

                        HP_BAR.update(hpFraction);
                        KI_BAR.update(kiFraction);
                        STM_BAR.update(stmFraction);
                        displayPowerRelease = lerp(displayPowerRelease, (float)powerRelease, partialTicks);
                        float currentHPBarWidth = HP_BAR.frontFraction() * 76.0F;
                        float currentKiBarWidth = KI_BAR.frontFraction() * 76.0F;
                        float currentStmBarWidth = STM_BAR.frontFraction() * 76.0F;
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        float hudScale = 1.25F;
                        int globalAnchorX = width / 2;
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate((float)globalAnchorX, (float)height, 0.0F);
                        guiGraphics.pose().scale(hudScale, hudScale, 1.0F);
                        float baseHpX = -95.0F;
                        float baseHpY = -49.0F;
                        float baseKiX = -95.0F;
                        float baseKiY = -50.0F;
                        float baseStmX = -5.0F;
                        float baseStmY = -50.0F;
                        float hpOffX = (float)ConfigManager.getUserConfig().getHealthBarPosX().intValue() / hudScale;
                        float hpOffY = (float)ConfigManager.getUserConfig().getHealthBarPosY().intValue() / hudScale;
                        float kiOffX = (float)ConfigManager.getUserConfig().getEnergyBarPosX().intValue() / hudScale;
                        float kiOffY = (float)ConfigManager.getUserConfig().getEnergyBarPosY().intValue() / hudScale;
                        float stmOffX = (float)ConfigManager.getUserConfig().getStaminaBarPosX().intValue() / hudScale;
                        float stmOffY = (float)ConfigManager.getUserConfig().getStaminaBarPosY().intValue() / hudScale;
                        float tickTime = (float)mc.player.tickCount + partialTicks;
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(baseHpX + hpOffX, baseHpY + hpOffY, 0.0F);
                        guiGraphics.blit(hud, 0, 0, 0.0F, 0.0F, 83, 9, 128, 128);
                        int hpTextureV = (double)currentHP < (double)maxHP * 0.33 ? 33 : ((double)currentHP < (double)maxHP * 0.66 ? 22 : 11);
                        drawHpChip(guiGraphics, 9, 3, 9, hpTextureV, currentHPBarWidth, HP_BAR.ghostFraction() * 76.0F, HP_BAR.gapType(), 5);
                        guiGraphics.blit(hud, 2, 3, 2.0F, (float)hpTextureV, 7 + (int)currentHPBarWidth, 5, 128, 128);
                        drawBarValues(guiGraphics, HP_NUMBER, currentHP, maxHP, 42, 3, tickTime);
                        guiGraphics.pose().popPose();
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(baseKiX + kiOffX, baseKiY + kiOffY, 0.0F);
                        guiGraphics.blit(hud, 0, 0, 0.0F, 44.0F, 83, 9, 128, 128);
                        float[] auraRgb = ColorUtils.hexToRgb(auraColor);
                        RenderSystem.setShaderColor(auraRgb[0], auraRgb[1], auraRgb[2], 1.0F);
                        guiGraphics.blit(hud, 3, 3, 3.0F, 61.0F, 7 + (int)currentKiBarWidth, 4, 128, 128);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        drawBarValues(guiGraphics, KI_NUMBER, currentKi, maxKi, 42, 3, tickTime);
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().scale(1.5F, 1.5F, 1.5F);
                        drawRacialIcon(guiGraphics, raceName, displayPowerRelease, -28, 0);
                        drawFormIcon(guiGraphics, formRelease, -28, 0);
                        guiGraphics.pose().popPose();
                        guiGraphics.pose().popPose();
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(baseStmX + stmOffX, baseStmY + stmOffY, 0.0F);
                        guiGraphics.blit(hud, 0, 0, 0.0F, 72.0F, 83, 9, 128, 128);
                        guiGraphics.blit(hud, 2, 3, 2.0F, 90.0F, -5 + (int)currentStmBarWidth, 4, 128, 128);
                        guiGraphics.blit(hud, 77, 3, 77.0F, 90.0F, 4, 4, 128, 128);
                        drawBarValues(guiGraphics, STM_NUMBER, currentStm, maxStm, 41, 3, tickTime);
                        guiGraphics.pose().popPose();
                        guiGraphics.pose().popPose();
                     }
                  }
               );
         }
      }
   };

   private static void drawHpChip(GuiGraphics guiGraphics, int x, int y, int u, int v, float front, float ghost, HudBarAnimator.GapType gap, int height) {
      if (gap != HudBarAnimator.GapType.NONE) {
         int start = Math.round(Math.min(front, ghost));
         int end = Math.round(Math.max(front, ghost));
         int chipWidth = end - start;
         if (chipWidth > 0) {
            if (gap == HudBarAnimator.GapType.DAMAGE) {
               RenderSystem.setShaderColor(1.0F, 0.24F, 0.24F, 1.0F);
            } else {
               RenderSystem.setShaderColor(0.34F, 1.0F, 0.42F, 1.0F);
            }

            guiGraphics.blit(hud, x + start, y, (float)(u + start), (float)v, chipWidth, height, 128, 128);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }
   }

   private static void drawBarValues(GuiGraphics guiGraphics, HudStatNumberAnimator animator, float current, float max, int x, int y, float tickTime) {
      if (ConfigManager.getUserConfig().getAdvancedDescription()) {
         boolean pct = ConfigManager.getUserConfig().getAdvancedDescriptionPercentage();
         String text = pct
            ? String.format("%.0f%%", current / max * 100.0F)
            : numberFormat.format(Math.round((double)current)) + " / " + numberFormat.format(Math.round((double)max));
         drawAnimatedTinyText(guiGraphics, animator, text, displayValue(current, max, pct), tickTime, x, y);
      }
   }

   private static void drawRacialIcon(GuiGraphics guiGraphics, String raceName, float powerRelease, int x, int y) {
      List<String> loadedRaces = ConfigManager.getDefaultRaces();
      int raceIndex = Math.max(0, loadedRaces.indexOf(raceName.toLowerCase()));
      int iconU = 1 + raceIndex * 17;
      boolean isCustomRace = !loadedRaces.contains(raceName.toLowerCase());
      int fillHeight = (int)(16.0F * (Math.min(powerRelease, 100.0F) / 100.0F));
      guiGraphics.blit(racialIcons, x + 7, y + 4, isCustomRace ? 103.0F : (float)iconU, 1.0F, 16, 16, 256, 256);
      if (fillHeight > 0) {
         guiGraphics.blit(
            racialIcons, x + 7, y + 4 + (16 - fillHeight), isCustomRace ? 103.0F : (float)iconU, (float)(18 + (16 - fillHeight)), 16, fillHeight, 256, 256
         );
      }

      RenderSystem.enableBlend();
      guiGraphics.blit(xvhud, x, y, 218.0F, 100.0F, 26, 27, 256, 256);
      RenderSystem.disableBlend();
      drawTinyText(guiGraphics, Math.round(powerRelease) + "%", -18, 11, ColorUtils.hexToInt("#FACAF7"));
   }

   private static void drawFormIcon(GuiGraphics guiGraphics, int formRelease, int x, int y) {
      int fillFormHeight = (int)(17.0F * ((float)formRelease / 100.0F));
      if (fillFormHeight > 0) {
         guiGraphics.blit(xvhud, x + 2, y + 12 + (17 - fillFormHeight), 220.0F, (float)(130 + (17 - fillFormHeight)), 26, fillFormHeight, 256, 256);
      }
   }

   private static void drawTinyText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate((float)x, (float)y, 0.0F);
      guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
      TextUtil.drawStringWithBorder(guiGraphics, Minecraft.getInstance().font, text, 0, 0, color);
      guiGraphics.pose().popPose();
   }

   private static void drawAnimatedTinyText(GuiGraphics guiGraphics, HudStatNumberAnimator animator, String text, float value, float tickTime, int x, int y) {
      HudStatNumberAnimator.RenderState state = animator.update(text, value, tickTime);
      if (!state.isHidden()) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate((float)x + state.offsetX(), (float)y + state.offsetY(), 0.0F);
         guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
         drawFadingString(guiGraphics, text, 0, 0, withAlpha(state.rgbColor(), state.alpha()), false);
         guiGraphics.pose().popPose();
      }
   }

   private static void drawFadingString(GuiGraphics guiGraphics, String text, int x, int y, int color, boolean centered) {
      int alpha = color >>> 24 & 0xFF;
      if (alpha > 2) {
         int borderCol = alpha << 24;
         Font font = Minecraft.getInstance().font;
         int dx = centered ? -font.width(text) / 2 : 0;
         guiGraphics.drawString(font, text, x + dx - 1, y, borderCol, false);
         guiGraphics.drawString(font, text, x + dx + 1, y, borderCol, false);
         guiGraphics.drawString(font, text, x + dx, y - 1, borderCol, false);
         guiGraphics.drawString(font, text, x + dx, y + 1, borderCol, false);
         guiGraphics.drawString(font, text, x + dx, y, color, false);
      }
   }

   private static float displayValue(float current, float max, boolean showPercent) {
      return showPercent ? (float)Math.round(current / max * 100.0F) : (float)Math.round(current);
   }

   private static int withAlpha(int rgb, float alpha) {
      int alphaChannel = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
      return alphaChannel << 24 | rgb & 16777215;
   }

   private static float lerp(float start, float end, float delta) {
      float change = (end - start) * 0.25F * delta;
      return Math.abs(end - start) <= 1.0F ? end : start + change;
   }
}
