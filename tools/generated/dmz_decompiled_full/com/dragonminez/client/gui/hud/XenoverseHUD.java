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

public class XenoverseHUD {
   private static final ResourceLocation hud = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/xenoversehud.png");
   private static final ResourceLocation racialIcons = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/racial_icons.png");
   private static final HudBarAnimator HP_BAR = new HudBarAnimator();
   private static final HudBarAnimator KI_BAR = new HudBarAnimator();
   private static final HudBarAnimator STM_BAR = new HudBarAnimator();
   private static volatile float displayPowerRelease = 0.0F;
   private static volatile float lastSeenMaxHP = -1.0F;
   private static volatile float lastSeenMaxKi = -1.0F;
   private static volatile float lastSeenMaxStm = -1.0F;
   private static final float LERP_SPEED = 0.25F;
   private static final float HP_BAR_MAX_WIDTH = 137.0F;
   private static final float KI_BAR_MAX_WIDTH = 114.0F;
   private static final float STM_BAR_MAX_WIDTH = 85.0F;
   private static final HudStatNumberAnimator HP_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.HEALTH);
   private static final HudStatNumberAnimator KI_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.KI);
   private static final HudStatNumberAnimator STM_NUMBER = new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.STAMINA);
   static NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
   public static final Layer HUD_XENOVERSE = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         if (!ConfigManager.getUserConfig().getAlternativeHud()) {
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
                        displayPowerRelease = displayPowerRelease + ((float)powerRelease - displayPowerRelease) * 0.25F * partialTicks;
                        if (Math.abs(displayPowerRelease - (float)powerRelease) <= 1.0F) {
                           displayPowerRelease = (float)powerRelease;
                        }

                        float currentHPBarWidth = HP_BAR.frontFraction() * 137.0F;
                        float currentKiBarWidth = KI_BAR.frontFraction() * 114.0F;
                        float currentStmBarWidth = STM_BAR.frontFraction() * 85.0F;
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        float baseScale = 2.25F;
                        float baseWidth = 184.0F;
                        float maxAllowedWidth = (float)width * 0.5F;
                        float userScale = ConfigManager.getUserConfig().getXenoverseHudScale();
                        float finalScale = Math.min(baseScale * userScale, maxAllowedWidth / baseWidth);
                        int anchorX = ConfigManager.getUserConfig().getXenoverseHudPosX();
                        int anchorY = ConfigManager.getUserConfig().getXenoverseHudPosY();
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate((float)anchorX, (float)anchorY, 0.0F);
                        guiGraphics.pose().scale(finalScale, finalScale, 1.0F);
                        guiGraphics.blit(hud, 0, 0, 184.0F, 10.0F, 56, 25, 256, 256);
                        guiGraphics.blit(hud, 31, 13, 14.0F, 2.0F, 141, 9, 256, 256);
                        int hpV = (double)currentHP < (double)maxHP * 0.33 ? 48 : ((double)currentHP < (double)maxHP * 0.66 ? 35 : 21);
                        drawHpChip(guiGraphics, 32, 15, 15, hpV, currentHPBarWidth, HP_BAR.ghostFraction() * 137.0F, HP_BAR.gapType(), 5);
                        guiGraphics.blit(hud, 32, 15, 15.0F, (float)hpV, (int)currentHPBarWidth, 5, 256, 256);
                        guiGraphics.blit(hud, 28, 21, 8.0F, 65.0F, 118, 8, 256, 256);
                        float[] auraRgb = ColorUtils.hexToRgb(auraColor);
                        RenderSystem.setShaderColor(auraRgb[0], auraRgb[1], auraRgb[2], 1.0F);
                        guiGraphics.blit(hud, 29, 23, 9.0F, 81.0F, (int)currentKiBarWidth, 4, 256, 256);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        guiGraphics.blit(hud, 28, 28, 9.0F, 105.0F, 100, 7, 256, 256);
                        guiGraphics.blit(hud, 43, 29, 24.0F, 121.0F, (int)currentStmBarWidth, 5, 256, 256);
                        List<String> loadedRaces = ConfigManager.getDefaultRaces();
                        int raceIndex = Math.max(0, loadedRaces.indexOf(raceName.toLowerCase()));
                        int iconU = 1 + raceIndex * 17;
                        boolean isMajin = raceName.equalsIgnoreCase("majin");
                        boolean isCustomRace = !loadedRaces.contains(raceName.toLowerCase());
                        int raceY = isMajin ? 12 : 13;
                        guiGraphics.blit(racialIcons, 15, raceY, isCustomRace ? 103.0F : (float)iconU, 1.0F, 16, 16, 256, 256);
                        int fillHeight = (int)(16.0F * (Math.min(displayPowerRelease, 100.0F) / 100.0F));
                        if (fillHeight > 0) {
                           guiGraphics.blit(
                              racialIcons,
                              15,
                              raceY + (16 - fillHeight),
                              isCustomRace ? 103.0F : (float)iconU,
                              (float)(18 + (16 - fillHeight)),
                              16,
                              fillHeight,
                              256,
                              256
                           );
                        }

                        guiGraphics.blit(hud, 8, 8, 218.0F, 100.0F, 26, 27, 256, 256);
                        int fillFormHeight = (int)(17.0F * ((float)formRelease / 100.0F));
                        if (fillFormHeight > 0) {
                           guiGraphics.blit(hud, 10, 20 + (17 - fillFormHeight), 220.0F, (float)(130 + (17 - fillFormHeight)), 26, fillFormHeight, 256, 256);
                        }

                        drawScaledText(guiGraphics, Math.round(displayPowerRelease) + "%", 7, 32, 0.5F, ColorUtils.hexToInt("#FACAF7"));
                        if (ConfigManager.getUserConfig().getAdvancedDescription()) {
                           boolean showPercent = ConfigManager.getUserConfig().getAdvancedDescriptionPercentage();
                           float tickTime = (float)mc.player.tickCount + partialTicks;
                           String hpText = showPercent
                              ? String.format("%.0f%%", currentHP / maxHP * 100.0F)
                              : numberFormat.format(Math.round((double)currentHP)) + " / " + numberFormat.format(Math.round((double)maxHP));
                           drawAnimatedScaledText(guiGraphics, HP_NUMBER, hpText, displayValue(currentHP, maxHP, showPercent), tickTime, 100, 15, 0.5F);
                           String kiText = showPercent
                              ? String.format("%.0f%%", currentKi / maxKi * 100.0F)
                              : numberFormat.format(Math.round((double)currentKi)) + " / " + numberFormat.format(Math.round((double)maxKi));
                           drawAnimatedScaledText(guiGraphics, KI_NUMBER, kiText, displayValue(currentKi, maxKi, showPercent), tickTime, 90, 23, 0.5F);
                           String stmText = showPercent
                              ? String.format("%.0f%%", currentStm / maxStm * 100.0F)
                              : numberFormat.format(Math.round((double)currentStm)) + " / " + numberFormat.format(Math.round((double)maxStm));
                           drawAnimatedScaledText(guiGraphics, STM_NUMBER, stmText, displayValue(currentStm, maxStm, showPercent), tickTime, 80, 30, 0.5F);
                        }

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

            guiGraphics.blit(hud, x + start, y, (float)(u + start), (float)v, chipWidth, height, 256, 256);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }
   }

   private static void drawScaledText(GuiGraphics guiGraphics, String text, int x, int y, float scale, int color) {
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate((float)x, (float)y, 0.0F);
      guiGraphics.pose().scale(scale, scale, 1.0F);
      TextUtil.drawCenteredStringWithBorder(guiGraphics, Minecraft.getInstance().font, text, 0, 0, color);
      guiGraphics.pose().popPose();
   }

   private static void drawAnimatedScaledText(
      GuiGraphics guiGraphics, HudStatNumberAnimator animator, String text, float value, float tickTime, int x, int y, float scale
   ) {
      HudStatNumberAnimator.RenderState state = animator.update(text, value, tickTime);
      if (!state.isHidden()) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate((float)x + state.offsetX(), (float)y + state.offsetY(), 0.0F);
         guiGraphics.pose().scale(scale, scale, 1.0F);
         drawFadingString(guiGraphics, text, 0, 0, withAlpha(state.rgbColor(), state.alpha()), true);
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
}
