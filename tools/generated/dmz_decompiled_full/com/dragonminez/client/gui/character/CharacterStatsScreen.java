package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.SwitchButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.shader.ClientGravityState;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.config.RaceStatsConfig;
import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.IncreaseStatC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.BonusStats;
import com.dragonminez.common.stats.extras.DynamicGrowthMath;
import com.dragonminez.common.stats.extras.DynamicGrowthStat;
import com.dragonminez.common.stats.skills.Skills;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class CharacterStatsScreen extends BaseMenuScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation MENU_SMALL = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menusmall.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private int tpMultiplier = 1;
   private StatsData statsData;
   private int tickCount = 0;
   private final NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
   private final DecimalFormat oneDecimalFormatter;
   private final DecimalFormat twoDecimalFormatter;
   private final DecimalFormat scientificFormatter;
   private final DecimalFormat fullTpsFormatter;
   private final DecimalFormat compactBpFormatter;
   private boolean useHexagonView = false;
   private CustomTextureButton strButton;
   private CustomTextureButton skpButton;
   private CustomTextureButton resButton;
   private CustomTextureButton vitButton;
   private CustomTextureButton pwrButton;
   private CustomTextureButton eneButton;
   private CustomTextureButton multiplierButton;
   private SwitchButton viewSwitchButton;

   public CharacterStatsScreen() {
      super(Component.translatable("gui.dragonminez.character_stats.title"));
      DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
      this.oneDecimalFormatter = new DecimalFormat("#,##0.#", symbols);
      this.twoDecimalFormatter = new DecimalFormat("#,##0.00", symbols);
      this.scientificFormatter = new DecimalFormat("0.###E0", symbols);
      this.fullTpsFormatter = new DecimalFormat("#,##0.######", symbols);
      this.compactBpFormatter = new DecimalFormat("0.##", symbols);
   }

   @Override
   protected void init() {
      super.init();
      this.useHexagonView = ConfigManager.getUserConfig().getHexagonStatsDisplay();
      this.tpMultiplier = 1;
      this.updateStatsData();
      this.initStatButtons();
      this.initViewSwitchButton();
   }

   @Override
   public void tick() {
      super.tick();
      this.tickCount++;
      if (this.tickCount >= 10) {
         this.tickCount = 0;
         this.updateStatsData();
         this.refreshStatButtons();
      }
   }

   private void updateStatsData() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> this.statsData = data);
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.isNotAnimating()) {
         this.renderBackground(graphics, mouseX, mouseY, partialTick);
      }

      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.applyZoom(graphics, partialTick);
      int leftOffset = this.getLeftPanelSwitchOffset(partialTick);
      int rightOffset = this.getRightPanelSwitchOffset(partialTick);
      int topOffset = this.getTopPanelSwitchOffset(partialTick);
      this.updatePanelWidgetOffsets(leftOffset, rightOffset);
      this.renderPlayerModel(graphics, this.getUiWidth() / 2 + 5, this.getUiHeight() / 2 + 70, 75, (float)uiMouseX, (float)uiMouseY);
      this.renderMenuPanels(graphics, leftOffset, rightOffset, topOffset);
      this.renderPlayerInfo(graphics, uiMouseX, uiMouseY - topOffset, topOffset);
      graphics.pose().pushPose();
      graphics.pose().translate((float)leftOffset, 0.0F, 0.0F);
      this.renderStatsInfo(graphics, uiMouseX - leftOffset, uiMouseY);
      graphics.pose().popPose();
      graphics.pose().pushPose();
      graphics.pose().translate((float)rightOffset, 0.0F, 0.0F);
      this.renderStatisticsInfo(graphics, uiMouseX - rightOffset, uiMouseY);
      graphics.pose().popPose();
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void initStatButtons() {
      if (this.statsData != null) {
         int centerY = this.getUiHeight() / 2;
         int buttonX = 27;
         int startY = centerY - 15;
         int maxStats = ConfigManager.getServerConfig().getGameplay().getMaxValue();
         boolean maxByLevel = ConfigManager.getServerConfig().getGameplay().getMaxLevelValueInsteadOfStats();
         float availableTPs = this.statsData.getResources().getTrainingPoints();
         int pendingAP = this.statsData.getPendingAttributePoints();
         int freeCount = Math.min(pendingAP, this.tpMultiplier);
         int tpCost = this.statsData.calculateRecursiveCost(this.tpMultiplier, maxStats) - this.statsData.calculateRecursiveCost(freeCount, maxStats);
         int remainingTotal = this.statsData.getRemainingAssignableStats();
         boolean hasAP = pendingAP >= 1;
         boolean hasEnoughTPs = availableTPs >= (float)tpCost;
         boolean canGrowAnyStat = (hasAP || hasEnoughTPs) && (!maxByLevel || remainingTotal > 0);
         this.multiplierButton = new CustomTextureButton.Builder()
            .position(buttonX, startY + 86)
            .size(14, 11)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 0, 0, 10)
            .textureSize(10, 10)
            .onPress(button -> {
               this.tpMultiplier = switch (this.tpMultiplier) {
                  case 1 -> 10;
                  case 10 -> 100;
                  case 100 -> 1000;
                  case 1000 -> 1;
                  default -> 1;
               };
               this.refreshStatButtons();
            })
            .build();
         this.addRenderableWidget(this.multiplierButton);
         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("STR", 1) > 0) {
            this.strButton = this.createStatButton(buttonX, startY + 11, "STR");
            this.addRenderableWidget(this.strButton);
         }

         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("SKP", 1) > 0) {
            this.skpButton = this.createStatButton(buttonX, startY + 23, "SKP");
            this.addRenderableWidget(this.skpButton);
         }

         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("RES", 1) > 0) {
            this.resButton = this.createStatButton(buttonX, startY + 35, "RES");
            this.addRenderableWidget(this.resButton);
         }

         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("VIT", 1) > 0) {
            this.vitButton = this.createStatButton(buttonX, startY + 47, "VIT");
            this.addRenderableWidget(this.vitButton);
         }

         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("PWR", 1) > 0) {
            this.pwrButton = this.createStatButton(buttonX, startY + 59, "PWR");
            this.addRenderableWidget(this.pwrButton);
         }

         if (canGrowAnyStat && this.statsData.getMaxAllowedIncreaseForStat("ENE", 1) > 0) {
            this.eneButton = this.createStatButton(buttonX, startY + 71, "ENE");
            this.addRenderableWidget(this.eneButton);
         }
      }
   }

   private CustomTextureButton createStatButton(int x, int y, String statName) {
      return new CustomTextureButton.Builder()
         .position(x, y)
         .size(14, 11)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 0, 0, 10)
         .textureSize(10, 10)
         .onPress(button -> {
            IncreaseStatC2S.StatType statEnum = IncreaseStatC2S.StatType.valueOf(statName.toUpperCase());
            NetworkHandler.sendToServer(new IncreaseStatC2S(statEnum, this.tpMultiplier));
         })
         .build();
   }

   private void refreshStatButtons() {
      if (this.strButton != null) {
         this.removeWidget(this.strButton);
      }

      if (this.skpButton != null) {
         this.removeWidget(this.skpButton);
      }

      if (this.resButton != null) {
         this.removeWidget(this.resButton);
      }

      if (this.vitButton != null) {
         this.removeWidget(this.vitButton);
      }

      if (this.pwrButton != null) {
         this.removeWidget(this.pwrButton);
      }

      if (this.eneButton != null) {
         this.removeWidget(this.eneButton);
      }

      if (this.multiplierButton != null) {
         this.removeWidget(this.multiplierButton);
      }

      this.strButton = null;
      this.skpButton = null;
      this.resButton = null;
      this.vitButton = null;
      this.pwrButton = null;
      this.eneButton = null;
      this.multiplierButton = null;
      this.initStatButtons();
   }

   private double[] getDamageReductionPercentages() {
      double baseDefense = this.statsData.getDefense();
      int maxValue = this.statsData.getConfiguredMaxValue();
      double expectedMaxStats = this.statsData.isMaxLevelValueInsteadOfStats() ? (double)maxValue * 6.0 / 2.0 : (double)maxValue;
      double expectedMaxDef = expectedMaxStats * this.statsData.getStatScaling("DEF");
      double k_factor = Math.max(12.0, expectedMaxDef * ConfigManager.getCombatConfig().getDefenseReductionScale());
      double baseReduction;
      if (baseDefense >= 0.0) {
         baseReduction = baseDefense / (k_factor + baseDefense);
      } else {
         baseReduction = baseDefense / (k_factor - baseDefense);
      }

      double baseCap = ConfigManager.getCombatConfig().getBaseDamageReductionCap();
      baseReduction = Mth.clamp(baseReduction, 0.0, baseCap);
      int totalProtection = 0;
      if (Minecraft.getInstance().player != null) {
         LocalPlayer player = Minecraft.getInstance().player;
         Holder<Enchantment> protection = player.level().registryAccess().holderOrThrow(Enchantments.PROTECTION);

         for (ItemStack stack : player.getArmorSlots()) {
            totalProtection += EnchantmentHelper.getItemEnchantmentLevel(protection, stack);
         }
      }

      double enchReduction = 0.0;
      if (totalProtection > 0) {
         double effectiveProtection = 0.0;
         int remaining = totalProtection;

         for (double mult = 1.0; remaining > 0; mult *= 0.5) {
            int chunk = Math.min(remaining, 4);
            effectiveProtection += (double)chunk * mult;
            remaining -= chunk;
         }

         double k_ench = 20.0;
         enchReduction = effectiveProtection / (k_ench + effectiveProtection);
         double totalCap = ConfigManager.getCombatConfig().getEnchantmentDamageReductionCap();
         double maxEnchReductionAllowed = (totalCap - baseReduction) / (1.0 - baseReduction);
         enchReduction = Mth.clamp(enchReduction, 0.0, Math.max(0.0, maxEnchReductionAllowed));
      }

      double mitigationReduction = 1.0 - (1.0 - baseReduction) * (1.0 - enchReduction);
      double mitigationReductionPct = Mth.clamp(mitigationReduction * 100.0, 0.0, 100.0);
      return new double[]{mitigationReductionPct, enchReduction * 100.0};
   }

   private void renderMenuPanels(GuiGraphics graphics, int leftOffset, int rightOffset, int topOffset) {
      int centerX = this.getUiWidth() / 2;
      int centerY = this.getUiHeight() / 2;
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, 12 + leftOffset, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, 29 + leftOffset, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      graphics.blit(MENU_BIG, 43 + leftOffset, centerY - 28, 142.0F, 0.0F, 79, 21, 256, 256);
      graphics.blit(MENU_BIG, this.getUiWidth() - 158 + rightOffset, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, this.getUiWidth() - 141 + rightOffset, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      graphics.blit(MENU_SMALL, centerX - 70, 8 + topOffset, 0.0F, 95.0F, 145, 58, 256, 256);
      RenderSystem.disableBlend();
   }

   private void renderPlayerInfo(GuiGraphics graphics, int mouseX, int mouseY, int topOffset) {
      int centerX = this.getUiWidth() / 2;
      if (Minecraft.getInstance().player != null) {
         String playerName = Minecraft.getInstance().player.getName().getString();
         String raceName = this.statsData.getCharacter().getRaceName();
         String gender = this.statsData.getCharacter().getGender();
         int alignment = this.statsData.getResources().getAlignment();
         int nameColor = gender.equals("male") ? 6553599 : 16738740;
         String genderSymbol = gender.equals("male") ? "♂" : "♀";
         Component nameBold = this.txt(playerName).withStyle(style -> style.withBold(true));
         int totalWidth = this.font.width(nameBold) + this.font.width(" " + genderSymbol);
         int startX = centerX - totalWidth / 2;
         graphics.drawString(this.font, nameBold, startX + 1, 19 + topOffset, 0, false);
         graphics.drawString(this.font, nameBold, startX - 1, 19 + topOffset, 0, false);
         graphics.drawString(this.font, nameBold, startX, 20 + topOffset, 0, false);
         graphics.drawString(this.font, nameBold, startX, 18 + topOffset, 0, false);
         graphics.drawString(this.font, nameBold, startX, 19 + topOffset, nameColor, false);
         int symbolX = startX + this.font.width(nameBold);
         graphics.drawString(this.font, " " + genderSymbol, symbolX + 1, 19 + topOffset, 0, false);
         graphics.drawString(this.font, " " + genderSymbol, symbolX - 1, 19 + topOffset, 0, false);
         graphics.drawString(this.font, " " + genderSymbol, symbolX, 20 + topOffset, 0, false);
         graphics.drawString(this.font, " " + genderSymbol, symbolX, 18 + topOffset, 0, false);
         graphics.drawString(this.font, " " + genderSymbol, symbolX, 19 + topOffset, nameColor, false);
         if (mouseX >= centerX - 40 && mouseX <= centerX + 40 && mouseY >= 19 && mouseY <= 19 + 9) {
            Component title = this.tr("gui.dragonminez.character_stats.alignment", new Object[0]).withStyle(ChatFormatting.GOLD);
            List<Component> tooltip = new ArrayList<>();
            if (alignment > 60) {
               tooltip.add(this.tr("gui.dragonminez.character_stats.alignment.good", new Object[]{alignment}).withStyle(ChatFormatting.YELLOW));
            } else if (alignment > 40) {
               tooltip.add(this.tr("gui.dragonminez.character_stats.alignment.neutral", new Object[]{alignment}).withStyle(ChatFormatting.YELLOW));
            } else {
               tooltip.add(this.tr("gui.dragonminez.character_stats.alignment.evil", new Object[]{alignment}).withStyle(ChatFormatting.YELLOW));
            }

            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, tooltip, null, 16776960);
         }

         Component raceComponent = this.tr("race.dragonminez." + raceName, new Object[0]);
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, raceComponent, centerX, 46 + topOffset, 16777215, 0);
      }
   }

   private void updatePanelWidgetOffsets(int leftOffset, int rightOffset) {
      int leftButtonX = 27 + leftOffset;
      if (this.strButton != null) {
         this.strButton.setX(leftButtonX);
      }

      if (this.skpButton != null) {
         this.skpButton.setX(leftButtonX);
      }

      if (this.resButton != null) {
         this.resButton.setX(leftButtonX);
      }

      if (this.vitButton != null) {
         this.vitButton.setX(leftButtonX);
      }

      if (this.pwrButton != null) {
         this.pwrButton.setX(leftButtonX);
      }

      if (this.eneButton != null) {
         this.eneButton.setX(leftButtonX);
      }

      if (this.multiplierButton != null) {
         this.multiplierButton.setX(leftButtonX);
      }

      int rightSwitchX = this.getUiWidth() - 45 + rightOffset;
      if (this.viewSwitchButton != null) {
         this.viewSwitchButton.setX(rightSwitchX);
      }
   }

   private void renderStatsInfo(GuiGraphics graphics, int mouseX, int mouseY) {
      int centerY = this.getUiHeight() / 2;
      int titleY = centerY - 88;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.character_stats.info", new Object[0]).withStyle(style -> style.withBold(true)), 85, titleY, 16499996, 0
      );
      int level = this.statsData.getLevel();
      float tps = this.statsData.getResources().getTrainingPoints();
      String characterClass = this.statsData.getCharacter().getCharacterClass();
      String form = this.statsData.getCharacter().getActiveForm();
      String stackForm = this.statsData.getCharacter().getActiveStackForm();
      int labelX = 30;
      int valueX = 70;
      int startY = centerY - 72;
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.level", new Object[0]).withStyle(style -> style.withBold(true)),
         labelX,
         startY,
         14155509,
         0
      );
      TextUtil.drawStringWithBorder(graphics, this.font, this.txt(this.numberFormatter.format((long)level)), valueX + 5, startY, 16777215, 0);
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.tps", new Object[0]).withStyle(style -> style.withBold(true)),
         labelX,
         startY + 11,
         14155509,
         0
      );
      String displayedTps = this.formatTpsDisplay(tps);
      int tpsX = valueX + 5;
      int tpsY = startY + 11;
      TextUtil.drawStringWithBorder(graphics, this.font, this.txt(displayedTps), tpsX, tpsY, 16770451, 0);
      if (this.shouldUseScientificForTps(tps)) {
         int tpsWidth = this.font.width(displayedTps);
         if (mouseX >= tpsX && mouseX <= tpsX + tpsWidth && mouseY >= tpsY && mouseY <= tpsY + 9) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(this.txt(this.fullTpsFormatter.format((double)tps)).withStyle(ChatFormatting.YELLOW));
            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), null, tooltip, null, 16776960);
         }
      }

      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.form", new Object[0]).withStyle(style -> style.withBold(true)),
         labelX,
         startY + 22,
         14155509,
         0
      );
      boolean isBase = form == null || form.isEmpty() || form.equals("base");
      boolean hasActiveStack = stackForm != null && !stackForm.isEmpty();
      String activeStackGroup = this.statsData.getCharacter().getActiveStackFormGroup();
      Component baseFormComponent = isBase
         ? this.tr("race.dragonminez.base", new Object[0])
         : this.tr(
            "race.dragonminez." + this.statsData.getCharacter().getRaceName() + ".form." + this.statsData.getCharacter().getActiveFormGroup() + "." + form,
            new Object[0]
         );
      Component formComponent;
      if (!isBase) {
         if (hasActiveStack) {
            formComponent = baseFormComponent.copy()
               .append(" ")
               .append(this.tr("race.dragonminez.stack.form." + activeStackGroup + "." + stackForm, new Object[0]));
         } else {
            formComponent = baseFormComponent;
         }
      } else if (hasActiveStack) {
         formComponent = this.tr("race.dragonminez.stack.group." + activeStackGroup, new Object[0])
            .append(" ")
            .append(this.tr("race.dragonminez.stack.form." + activeStackGroup + "." + stackForm, new Object[0]));
      } else {
         formComponent = baseFormComponent;
      }

      TextUtil.drawStringWithBorder(graphics, this.font, formComponent, valueX + 5, startY + 22, 13101820, 0);
      if (mouseX >= valueX + 5 && mouseX <= valueX + 85 && mouseY >= startY + 22 && mouseY <= startY + 22 + 9) {
         Component title = this.tr("gui.dragonminez.character_stats.form.mastery", new Object[0]).withStyle(ChatFormatting.GOLD);
         List<Component> tooltip = new ArrayList<>();
         boolean hasTitle = false;
         if (!isBase) {
            String currentFormGroup = this.statsData.getCharacter().getActiveFormGroup();
            if (currentFormGroup != null && !currentFormGroup.isEmpty()) {
               FormConfig formConfig = ConfigManager.getFormGroup(this.statsData.getCharacter().getRaceName(), currentFormGroup);
               if (formConfig != null) {
                  FormConfig.FormData formData = formConfig.getForm(form);
                  if (formData != null) {
                     if (!hasTitle) {
                        tooltip.add(this.tr("gui.dragonminez.character_stats.form.mastery", new Object[0]).withStyle(ChatFormatting.GOLD));
                        hasTitle = true;
                     }

                     double mastery = this.statsData.getCharacter().getFormMasteries().getMastery(currentFormGroup, form);
                     double maxMastery = formData.getMaxMastery();
                     tooltip.add(
                        this.txt(" ")
                           .append(baseFormComponent.copy().withStyle(ChatFormatting.GRAY))
                           .append(
                              this.txt(": " + String.format(Locale.US, "%.2f", mastery) + " / " + String.format(Locale.US, "%.0f", maxMastery))
                                 .withStyle(ChatFormatting.AQUA)
                           )
                     );
                  }
               }
            }
         }

         if (stackForm != null && !stackForm.isEmpty()) {
            String currentStackGroup = this.statsData.getCharacter().getActiveStackFormGroup();
            FormConfig stackFormConfig = ConfigManager.getStackFormGroup(currentStackGroup);
            if (currentStackGroup != null && !currentStackGroup.isEmpty() && stackFormConfig != null) {
               FormConfig.FormData formData = stackFormConfig.getForm(stackForm);
               if (formData != null) {
                  if (!hasTitle) {
                     tooltip.add(this.tr("gui.dragonminez.character_stats.form.mastery", new Object[0]).withStyle(ChatFormatting.GOLD));
                     hasTitle = true;
                  }

                  double mastery = this.statsData.getCharacter().getStackFormMasteries().getMastery(currentStackGroup, stackForm);
                  double maxMastery = formData.getMaxMastery();
                  Component stackFormComponent = this.tr("race.dragonminez.stack.group." + currentStackGroup, new Object[0])
                     .append(" ")
                     .append(this.tr("race.dragonminez.stack.form." + currentStackGroup + "." + stackForm, new Object[0]));
                  tooltip.add(
                     this.txt(" ")
                        .append(stackFormComponent.copy().withStyle(ChatFormatting.GRAY))
                        .append(
                           this.txt(": " + String.format(Locale.US, "%.2f", mastery) + " / " + String.format(Locale.US, "%.0f", maxMastery))
                              .withStyle(ChatFormatting.AQUA)
                        )
                  );
               }
            }
         }

         if (!tooltip.isEmpty()) {
            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, tooltip, null, 16763904);
         }
      }

      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.class", new Object[0]).withStyle(style -> style.withBold(true)),
         labelX,
         startY + 33,
         14155509,
         0
      );
      Component classComponent = this.tr("class.dragonminez." + characterClass, new Object[0]);
      TextUtil.drawStringWithBorder(graphics, this.font, classComponent, valueX + 5, startY + 33, 16777215, 0);
      int statsStartY = centerY - 21;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.character_stats.stats", new Object[0]).withStyle(ChatFormatting.BOLD), 82, statsStartY, 6868223, 0
      );
      String[] statNames = new String[]{"str", "skp", "res", "vit", "pwr", "ene"};
      String[] statNamesUpper = new String[]{"STR", "SKP", "RES", "VIT", "PWR", "ENE"};
      int[] statValues = new int[]{
         this.statsData.getStats().getStrength(),
         this.statsData.getStats().getStrikePower(),
         this.statsData.getStats().getResistance(),
         this.statsData.getStats().getVitality(),
         this.statsData.getStats().getKiPower(),
         this.statsData.getStats().getEnergy()
      };
      int statY = centerY - 3;

      for (int i = 0; i < statNames.length; i++) {
         int statLabelX = 42;
         int yPos = statY + i * 12;
         double totalMult = this.statsData.getTotalMultiplier(statNamesUpper[i]);
         int baseValue = statValues[i];
         double modifiedValue = (double)baseValue * totalMult;
         Component statComponent = this.tr("gui.dragonminez.character_stats." + statNames[i], new Object[0]).withStyle(style -> style.withBold(true));
         TextUtil.drawStringWithBorder(graphics, this.font, statComponent, statLabelX, yPos, 14095410, 0);
         boolean hasMult = Math.abs(totalMult - 1.0) > 0.01;
         int statColor = hasMult ? 16776960 : 16766891;
         String statText = hasMult
            ? this.numberFormatter.format((long)((int)modifiedValue)) + " x" + String.format(Locale.US, "%.1f", totalMult)
            : this.numberFormatter.format((long)baseValue);
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt(statText), valueX + 5, yPos, statColor, 0);
         if (mouseX >= statLabelX && mouseX <= statLabelX + 25 && mouseY >= yPos && mouseY <= yPos + 9) {
            Component titlex = this.tr("gui.dragonminez.character_stats." + statNames[i], new Object[0]).withStyle(ChatFormatting.BOLD);
            List<Component> desc = new ArrayList<>();
            desc.add(this.tr("gui.dragonminez.character_stats." + statNames[i] + ".desc", new Object[0]));
            List<Component> extras = new ArrayList<>();
            if (hasMult) {
               extras.add(
                  this.tr("gui.dragonminez.character_stats.base_value", new Object[0])
                     .append(": " + this.numberFormatter.format((long)baseValue))
                     .withStyle(ChatFormatting.GRAY)
               );
               extras.add(
                  this.tr("gui.dragonminez.character_stats.modified_value", new Object[0])
                     .append(": " + this.numberFormatter.format((long)((int)modifiedValue)))
                     .withStyle(ChatFormatting.YELLOW)
               );
               if (statNamesUpper[i].equals("RES")) {
                  double formDef = this.statsData.getFormMultiplier("DEF");
                  double formStm = this.statsData.getFormMultiplier("STM");
                  double stackDef = this.statsData.getStackFormMultiplier("DEF");
                  double stackStm = this.statsData.getStackFormMultiplier("STM");
                  double effectsDef = this.statsData.getEffectsMultiplier("DEF");
                  double effectsStm = this.statsData.getEffectsMultiplier("STM");
                  double secondaryDef = this.statsData.getSecondaryStatEffects().getMultiplier("DEF");
                  boolean hasForm = Math.abs(formDef - 1.0) > 0.01 || Math.abs(formStm - 1.0) > 0.01;
                  boolean hasStack = Math.abs(stackDef - 1.0) > 0.01 || Math.abs(stackStm - 1.0) > 0.01;
                  boolean hasEffects = Math.abs(effectsDef - 1.0) > 0.01 || Math.abs(effectsStm - 1.0) > 0.01;
                  boolean hasSecondary = Math.abs(secondaryDef - 1.0) > 0.01;
                  if (hasForm || hasStack || hasEffects || hasSecondary) {
                     extras.add(this.tr("gui.dragonminez.character_stats.multipliers", new Object[0]).withStyle(ChatFormatting.AQUA));
                     if (hasForm) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_multiplier", new Object[0])
                              .append(this.txt(" ("))
                              .append(this.tr("gui.dragonminez.character_stats.def", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", formDef) + ", "))
                              .append(this.tr("gui.dragonminez.character_stats.stm", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", formStm) + ")"))
                              .withStyle(ChatFormatting.GOLD)
                        );
                     }

                     if (hasStack) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.stack_multiplier", new Object[0])
                              .append(this.txt(" ("))
                              .append(this.tr("gui.dragonminez.character_stats.def", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", stackDef) + ", "))
                              .append(this.tr("gui.dragonminez.character_stats.stm", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", stackStm) + ")"))
                              .withStyle(ChatFormatting.RED)
                        );
                     }

                     if (hasEffects) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.effects_multiplier", new Object[0])
                              .append(this.txt(" ("))
                              .append(this.tr("gui.dragonminez.character_stats.def", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", effectsDef) + ", "))
                              .append(this.tr("gui.dragonminez.character_stats.stm", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", effectsStm) + ")"))
                              .withStyle(ChatFormatting.LIGHT_PURPLE)
                        );
                     }

                     if (hasSecondary) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.secondary_multiplier", new Object[0])
                              .append(this.txt(" ("))
                              .append(this.tr("gui.dragonminez.character_stats.def", new Object[0]))
                              .append(this.txt(": x" + String.format(Locale.US, "%.2f", secondaryDef) + ")"))
                              .withStyle(ChatFormatting.DARK_AQUA)
                        );
                     }
                  }
               } else {
                  double formMultiplier = this.statsData.getFormMultiplier(statNamesUpper[i]);
                  double stackMultiplier = this.statsData.getStackFormMultiplier(statNamesUpper[i]);
                  double effectsMultiplier = this.statsData.getEffectsMultiplier(statNamesUpper[i]);
                  double secondaryMultiplier = this.statsData.getSecondaryStatEffects().getMultiplier(statNamesUpper[i]);
                  boolean hasForm = Math.abs(formMultiplier - 1.0) > 0.01;
                  boolean hasStack = Math.abs(stackMultiplier - 1.0) > 0.01;
                  boolean hasEffects = Math.abs(effectsMultiplier - 1.0) > 0.01;
                  boolean hasSecondary = Math.abs(secondaryMultiplier - 1.0) > 0.01;
                  if (hasForm || hasStack || hasEffects || hasSecondary) {
                     extras.add(this.tr("gui.dragonminez.character_stats.multipliers", new Object[0]).withStyle(ChatFormatting.AQUA));
                     if (hasForm) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_multiplier", new Object[0])
                              .append(" x" + String.format(Locale.US, "%.2f", formMultiplier))
                              .withStyle(ChatFormatting.GOLD)
                        );
                     }

                     if (hasStack) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.stack_multiplier", new Object[0])
                              .append(" x" + String.format(Locale.US, "%.2f", stackMultiplier))
                              .withStyle(ChatFormatting.RED)
                        );
                     }

                     if (hasEffects) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.effects_multiplier", new Object[0])
                              .append(" x" + String.format(Locale.US, "%.2f", effectsMultiplier))
                              .withStyle(ChatFormatting.LIGHT_PURPLE)
                        );
                     }

                     if (hasSecondary) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.secondary_multiplier", new Object[0])
                              .append(" x" + String.format(Locale.US, "%.2f", secondaryMultiplier))
                              .withStyle(ChatFormatting.DARK_AQUA)
                        );
                     }
                  }
               }
            }

            ArrayList<BonusStats.StatBonus> bonuses = new ArrayList<>(this.statsData.getBonusStats().getBonuses(statNamesUpper[i]));
            if (statNamesUpper[i].equals("RES")) {
               List<String> seenNames = new ArrayList<>();

               for (BonusStats.StatBonus b : bonuses) {
                  seenNames.add(b.name);
               }

               for (BonusStats.StatBonus b : this.statsData.getBonusStats().getBonuses("DEF")) {
                  if (!seenNames.contains(b.name)) {
                     bonuses.add(b);
                     seenNames.add(b.name);
                  }
               }

               for (BonusStats.StatBonus bx : this.statsData.getBonusStats().getBonuses("STM")) {
                  if (!seenNames.contains(bx.name)) {
                     bonuses.add(bx);
                     seenNames.add(bx.name);
                  }
               }
            }

            bonuses.sort((a, bxx) -> a.name.compareTo(bxx.name));
            if (!bonuses.isEmpty()) {
               extras.add(this.tr("gui.dragonminez.character_stats.bonus", new Object[0]).withStyle(ChatFormatting.AQUA));

               for (BonusStats.StatBonus bonus : bonuses) {
                  String opDisplay = bonus.operation.equals("*") ? "x" : bonus.operation;
                  String bonusText = bonus.name.replace("_", " ")
                     + ": "
                     + opDisplay
                     + (bonus.operation.equals("*") ? String.format(Locale.US, "%.2f", bonus.value) : String.format(Locale.US, "%.0f", bonus.value));
                  extras.add(this.txt("  " + bonusText).withStyle(ChatFormatting.GREEN));
               }
            }

            this.appendDynamicGrowthProgress(extras, statNamesUpper[i], baseValue);
            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), titlex, desc, extras, 14095410);
         }
      }

      int pendingAP = this.statsData.getPendingAttributePoints();
      boolean showAP = pendingAP > 0;
      int bottomY = statY + 76;
      int bottomValueX = 75;
      Component bottomLabel = (showAP
            ? this.tr("gui.dragonminez.character_stats.ap", new Object[0])
            : this.tr("gui.dragonminez.character_stats.tpc", new Object[0]))
         .withStyle(style -> style.withBold(true));
      int labelColor = showAP ? 11758591 : 2883554;
      TextUtil.drawStringWithBorder(graphics, this.font, bottomLabel, 42, bottomY, labelColor, 0);
      int maxStats = ConfigManager.getServerConfig().getGameplay().getMaxValue();
      int tpCost = this.statsData.calculateRecursiveCost(this.tpMultiplier, maxStats);
      Component bottomValue = showAP ? this.txt(this.numberFormatter.format((long)pendingAP)) : this.txt(this.numberFormatter.format((long)tpCost));
      int valueColor = showAP ? 16776960 : 16764481;
      TextUtil.drawStringWithBorder(graphics, this.font, bottomValue, bottomValueX, bottomY, valueColor, 0);
      TextUtil.drawStringWithBorder(graphics, this.font, this.txt("x" + this.tpMultiplier), bottomValueX, bottomY + 10, 2883554, 0);
      if (mouseX >= 42 && mouseX <= bottomValueX + this.font.width(bottomValue) && mouseY >= bottomY && mouseY <= bottomY + 9) {
         List<Component> descx = new ArrayList<>();
         List<Component> extrasx = new ArrayList<>();
         Component titlexx;
         int color;
         if (showAP) {
            titlexx = this.tr("gui.dragonminez.character_stats.ap", new Object[0]).withStyle(ChatFormatting.LIGHT_PURPLE);
            color = 11758591;
            descx.add(this.tr("gui.dragonminez.character_stats.ap.desc", new Object[0]));
            extrasx.add(
               this.tr("gui.dragonminez.character_stats.ap.pending", new Object[]{this.numberFormatter.format((long)pendingAP)})
                  .withStyle(ChatFormatting.YELLOW)
            );
         } else {
            titlexx = this.tr("gui.dragonminez.character_stats.tpc", new Object[0]).withStyle(ChatFormatting.AQUA);
            color = 2883554;
            descx.add(this.tr("gui.dragonminez.character_stats.tpc.desc", new Object[0]));
            if (hasShiftDown()) {
               extrasx.add(this.tr("gui.dragonminez.character_stats.tpc.formula1", new Object[0]).withStyle(ChatFormatting.GRAY));
               extrasx.add(this.tr("gui.dragonminez.character_stats.tpc.formula2", new Object[0]).withStyle(ChatFormatting.GRAY));
               extrasx.add(this.tr("gui.dragonminez.character_stats.tpc.formula3", new Object[0]).withStyle(ChatFormatting.GRAY));
            } else {
               this.appendAdvancedHint(extrasx, true);
            }
         }

         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), titlexx, descx, extrasx, color);
      }
   }

   private void appendDynamicGrowthProgress(List<Component> extras, String statName, int currentStat) {
      if (this.statsData != null) {
         if (ConfigManager.getServerConfig().getDynamicGrowth().isEnabled()) {
            DynamicGrowthStat stat;
            try {
               stat = DynamicGrowthStat.valueOf(statName);
            } catch (IllegalArgumentException var10) {
               return;
            }

            extras.add(this.tr("dynamicgrowth.dragonminez.title", new Object[0]).withStyle(ChatFormatting.AQUA));
            if (this.statsData.getMaxAllowedIncreaseForStat(statName, 1) <= 0) {
               extras.add(this.txt("  ").append(this.tr("dynamicgrowth.dragonminez.maxed", new Object[0])).withStyle(ChatFormatting.GREEN));
            } else {
               int requiredXp = DynamicGrowthMath.requiredXp(currentStat);
               double currentXp = this.statsData.getDynamicGrowth().getPracticeXp(stat);
               double percent = requiredXp <= 0 ? 100.0 : currentXp / (double)requiredXp * 100.0;
               if (!Double.isFinite(percent)) {
                  percent = 0.0;
               }

               percent = Math.max(0.0, Math.min(100.0, percent));
               extras.add(
                  this.txt("  " + String.format(Locale.US, "%.1f", currentXp) + " / " + requiredXp + " XP (" + String.format(Locale.US, "%.1f", percent) + "%)")
                     .withStyle(ChatFormatting.GREEN)
               );
            }
         }
      }
   }

   private void renderStatisticsInfo(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.useHexagonView) {
         this.renderStatisticsInfoHexagon(graphics, mouseX, mouseY);
      } else {
         this.renderStatisticsInfoList(graphics, mouseX, mouseY);
      }

      this.renderBattlePowerInfo(graphics, mouseX, mouseY);
      this.renderGravityInfo(graphics, mouseX, mouseY);
      this.renderTpMultiplierInfo(graphics, mouseX, mouseY);
   }

   private void renderBattlePowerInfo(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.statsData != null) {
         int centerY = this.getUiHeight() / 2;
         int labelX = this.getUiWidth() - 137;
         int y = centerY + 54;
         boolean androidUpgraded = this.statsData.getStatus().isAndroidUpgraded();
         long bp = (long)this.statsData.getBattlePowerExact();
         String displayedBp = androidUpgraded ? "???" : this.formatBattlePower((double)bp);
         Component label = this.tr("gui.dragonminez.character_stats.power_level", new Object[0]);
         Component separator = this.txt(": ");
         Component value = this.txt(displayedBp);
         TextUtil.drawStringWithBorder(graphics, this.font, label, labelX, y, 8191446, 0);
         int separatorX = labelX + this.font.width(label);
         TextUtil.drawStringWithBorder(graphics, this.font, separator, separatorX, y, 8191446, 0);
         int valueX = separatorX + this.font.width(separator);
         TextUtil.drawStringWithBorder(graphics, this.font, value, valueX, y, 16770451, 0);
         int textWidth = this.font.width(label) + this.font.width(separator) + this.font.width(value);
         if (!androidUpgraded && this.shouldUseCompactForBp((double)bp) && mouseX >= labelX && mouseX <= labelX + textWidth && mouseY >= y && mouseY <= y + 9) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(this.txt(this.numberFormatter.format(bp)).withStyle(ChatFormatting.YELLOW));
            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), null, tooltip, null, 16776960);
         }
      }
   }

   private boolean shouldUseCompactForBp(double bp) {
      return bp > 9999999.0;
   }

   private String formatBattlePower(double bp) {
      if (!this.shouldUseCompactForBp(bp)) {
         return this.numberFormatter.format(bp);
      } else {
         String[] suffixes = new String[]{"M", "B", "T", "Qa", "Qi"};
         double[] scales = new double[]{1000000.0, 1.0E9, 1.0E12, 1.0E15, 1.0E18};
         int i = scales.length - 1;

         while (i > 0 && bp < scales[i]) {
            i--;
         }

         return this.compactBpFormatter.format(bp / scales[i]) + suffixes[i];
      }
   }

   private void renderStatisticsInfoList(GuiGraphics graphics, int mouseX, int mouseY) {
      int rightX = this.getUiWidth() - 137;
      int centerY = this.getUiHeight() / 2;
      int titleY = centerY - 88;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.statistics", new Object[0]).withStyle(style -> style.withBold(true)),
         this.getUiWidth() - 85,
         titleY,
         16326244,
         0
      );
      int labelStartY = centerY - 64;
      int valueX = this.getUiWidth() - 65;
      double meleeDamage = this.statsData.getMeleeDamage();
      double maxMeleeDamage = this.statsData.getMaxMeleeDamage();
      double strikeDamage = this.statsData.getStrikeDamage();
      double maxStrikeDamage = this.statsData.getMaxStrikeDamage();
      float stamina = this.statsData.getMaxStamina();
      double defense = this.statsData.getFlatMitigation();
      double maxDefense = this.statsData.getMaxFlatMitigation();
      double health = (double)Minecraft.getInstance().player.getMaxHealth();
      double kiDamage = this.statsData.getKiDamage();
      double maxKiDamage = this.statsData.getMaxKiDamage();
      float energy = this.statsData.getMaxEnergy();
      double strScaling = this.statsData.getStatScaling("STR");
      double skpScaling = this.statsData.getStatScaling("SKP");
      double resScaling = this.statsData.getStatScaling("DEF");
      double vitScaling = this.statsData.getStatScaling("VIT");
      double pwrScaling = this.statsData.getStatScaling("PWR");
      double eneScaling = this.statsData.getStatScaling("ENE");
      double stmScaling = this.statsData.getStatScaling("STM");
      RaceStatsConfig statsConfig = ConfigManager.getRaceStats(this.statsData.getCharacter().getRaceName());
      RaceStatsConfig.ClassStats classStats = statsConfig != null ? statsConfig.getClassStats(this.statsData.getCharacter().getCharacterClass()) : null;
      String[] labels = new String[]{
         "gui.dragonminez.character_stats.melee_damage",
         "gui.dragonminez.character_stats.strike_damage",
         "gui.dragonminez.character_stats.stamina",
         "gui.dragonminez.character_stats.defense",
         "gui.dragonminez.character_stats.health",
         "gui.dragonminez.character_stats.ki_damage",
         "gui.dragonminez.character_stats.max_energy"
      };
      boolean isTransformed = this.statsData.getCharacter().hasActiveForm() || this.statsData.getCharacter().hasActiveStackForm();

      for (int i = 0; i < labels.length; i++) {
         int yPos = labelStartY + i * 12;
         Component labelComponent = this.tr(labels[i], new Object[0]);
         TextUtil.drawStringWithBorder(graphics, this.font, labelComponent, rightX, yPos, 8191446, 0);
         if (mouseX >= rightX && mouseX <= rightX + 60 && mouseY >= yPos && mouseY <= yPos + 9) {
            Component title = this.tr(labels[i], new Object[0]).withStyle(ChatFormatting.BOLD);
            List<Component> desc = new ArrayList<>();
            List<Component> extras = new ArrayList<>();
            switch (i) {
               case 0:
                  desc.add(this.tr("gui.dragonminez.character_stats.melee_damage.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.melee_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(strScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxMeleeDamage)})
                        .withStyle(ChatFormatting.GREEN)
                  );
                  double defensePenxx = this.getDefensePenetrationPercentage();
                  if (defensePenxx > 0.0) {
                     extras.add(
                        this.tr("gui.dragonminez.character_stats.defense_penetration", new Object[0])
                           .append(this.txt(": " + this.formatUpToOneDecimal(defensePenxx) + "%"))
                           .withStyle(ChatFormatting.RED)
                     );
                  }
                  break;
               case 1:
                  desc.add(this.tr("gui.dragonminez.character_stats.strike_damage.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.strike_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(skpScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxStrikeDamage)})
                        .withStyle(ChatFormatting.GREEN)
                  );
                  double defensePenx = this.getDefensePenetrationPercentage();
                  if (defensePenx > 0.0) {
                     extras.add(
                        this.tr("gui.dragonminez.character_stats.defense_penetration", new Object[0])
                           .append(this.txt(": " + this.formatUpToOneDecimal(defensePenx) + "%"))
                           .withStyle(ChatFormatting.RED)
                     );
                  }
                  break;
               case 2:
                  desc.add(this.tr("gui.dragonminez.character_stats.stamina.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.stamina.tooltip2", new Object[]{this.formatUpToOneDecimal(stmScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  if (classStats != null) {
                     double currentRegenSec = (
                           classStats.getBaseSp5()
                              + (double)this.statsData.getStats().getResistance() * this.statsData.getTotalMultiplier("RES") * classStats.getSp5StmScaling()
                        )
                        * 0.2;
                     extras.add(
                        Component.translatable("gui.dragonminez.customization.stat.regen.stm")
                           .append(": ")
                           .append(this.txt(String.format(Locale.US, "%.1f/s", currentRegenSec)))
                           .withStyle(ChatFormatting.AQUA)
                     );
                  }

                  extras.add(
                     this.tr("gui.dragonminez.character_stats.stamina_per_hit", new Object[0])
                        .append(": ")
                        .append(this.txt(this.formatUpToOneDecimal(this.statsData.getStaminaPerHit())))
                        .withStyle(ChatFormatting.GOLD)
                  );
                  if (isTransformed) {
                     double stamDrain = this.statsData.getEffectiveStaminaDrain();
                     if (stamDrain > 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.stamina.cost", new Object[]{this.formatUpToOneDecimal(stamDrain)})
                              .withStyle(ChatFormatting.RED)
                        );
                     } else if (stamDrain < 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.stamina.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(stamDrain))})
                              .withStyle(ChatFormatting.GREEN)
                        );
                     }

                     double stamMult = this.statsData.getAdjustedStaminaDrainMultiplier();
                     if (stamMult != 1.0) {
                        ChatFormatting color = stamMult > 1.0 ? ChatFormatting.RED : ChatFormatting.GREEN;
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.stamina.multiplier", new Object[]{this.formatUpToOneDecimal(stamMult)})
                              .withStyle(color)
                        );
                     }
                  }
                  break;
               case 3:
                  desc.add(this.tr("gui.dragonminez.character_stats.defense.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.defense.tooltip2", new Object[]{this.formatUpToOneDecimal(resScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxDefense)}).withStyle(ChatFormatting.GREEN)
                  );
                  double[] pcts = this.getDamageReductionPercentages();
                  extras.add(
                     this.tr("gui.dragonminez.character_stats.defense", new Object[0])
                        .append(": ")
                        .append(this.txt(this.formatUpToTwoDecimals(pcts[0]) + "% "))
                        .append(this.tr("gui.dragonminez.character_stats.dmg_reduction", new Object[0]))
                        .withStyle(ChatFormatting.AQUA)
                  );
                  if (pcts[1] > 0.0) {
                     extras.add(
                        this.tr("gui.dragonminez.character_stats.protection", new Object[0])
                           .append(": ")
                           .append(this.txt(this.formatUpToTwoDecimals(pcts[1]) + "% "))
                           .append(this.tr("gui.dragonminez.character_stats.dmg_reduction", new Object[0]))
                           .withStyle(ChatFormatting.LIGHT_PURPLE)
                     );
                  }
                  break;
               case 4:
                  desc.add(this.tr("gui.dragonminez.character_stats.health.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.health.tooltip2", new Object[]{this.formatUpToOneDecimal(vitScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  if (classStats != null) {
                     double currentRegenSec = (
                           classStats.getBaseHp5()
                              + (double)this.statsData.getStats().getVitality() * this.statsData.getTotalMultiplier("VIT") * classStats.getHp5VitScaling()
                        )
                        * 0.2;
                     extras.add(
                        Component.translatable("gui.dragonminez.customization.stat.regen.hp")
                           .append(": ")
                           .append(this.txt(String.format(Locale.US, "%.1f/s", currentRegenSec)))
                           .withStyle(ChatFormatting.AQUA)
                     );
                  }

                  if (isTransformed) {
                     double hpDrain = this.statsData.getEffectiveHealthDrain();
                     if (hpDrain > 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.health.cost", new Object[]{this.formatUpToOneDecimal(hpDrain)})
                              .withStyle(ChatFormatting.RED)
                        );
                     } else if (hpDrain < 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.health.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(hpDrain))})
                              .withStyle(ChatFormatting.GREEN)
                        );
                     }
                  }
                  break;
               case 5:
                  desc.add(this.tr("gui.dragonminez.character_stats.ki_damage.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.ki_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(pwrScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxKiDamage)}).withStyle(ChatFormatting.GREEN)
                  );
                  double defensePen = this.getDefensePenetrationPercentage();
                  if (defensePen > 0.0) {
                     extras.add(
                        this.tr("gui.dragonminez.character_stats.ki_damage", new Object[0])
                           .append(this.txt(": "))
                           .append(this.txt(this.formatUpToOneDecimal(kiDamage)))
                           .withStyle(ChatFormatting.AQUA)
                     );
                  }
                  break;
               case 6:
                  desc.add(this.tr("gui.dragonminez.character_stats.max_energy.tooltip1", new Object[0]));
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.max_energy.tooltip2", new Object[]{this.formatUpToOneDecimal(eneScaling)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
                  if (classStats != null) {
                     double currentRegenSec = (
                           classStats.getBaseEp5()
                              + (double)this.statsData.getStats().getEnergy() * this.statsData.getTotalMultiplier("ENE") * classStats.getEp5EneScaling()
                        )
                        * 0.2;
                     extras.add(
                        Component.translatable("gui.dragonminez.customization.stat.regen.ki")
                           .append(": ")
                           .append(this.txt(String.format(Locale.US, "%.1f/s", currentRegenSec)))
                           .withStyle(ChatFormatting.AQUA)
                     );
                  }

                  if (isTransformed) {
                     double eneDrain = this.statsData.getEffectiveEnergyDrain();
                     if (eneDrain > 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.energy.cost", new Object[]{this.formatUpToOneDecimal(eneDrain)})
                              .withStyle(ChatFormatting.RED)
                        );
                     } else if (eneDrain < 0.0) {
                        extras.add(
                           this.tr("gui.dragonminez.character_stats.form_drain.energy.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(eneDrain))})
                              .withStyle(ChatFormatting.GREEN)
                        );
                     }
                  }
            }

            if (i == 0 || i == 1) {
               this.appendAttackSkillInfo(extras);
            }

            if (i == 3) {
               this.appendKiProtectionInfo(extras);
            }

            if (i == 2) {
               this.appendMeditationInfo(extras, false);
            }

            if (i == 6) {
               this.appendMeditationInfo(extras, true);
            }

            boolean hasAdvanced = (i == 0 || i == 1) && this.hasAttackSkillInfo()
               || i == 3 && this.hasKiProtectionInfo()
               || (i == 2 || i == 6) && this.hasMeditationInfo();
            this.appendAdvancedHint(extras, hasAdvanced);
            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 8191446);
         }
      }

      double strTotalMult = this.statsData.getTotalMultiplier("STR");
      double skpTotalMult = this.statsData.getTotalMultiplier("SKP");
      double stmTotalMult = this.statsData.getTotalMultiplier("STM");
      double defTotalMult = this.statsData.getTotalMultiplier("DEF");
      double vitTotalMult = this.statsData.getTotalMultiplier("VIT");
      double pwrTotalMult = this.statsData.getTotalMultiplier("PWR");
      double eneTotalMult = this.statsData.getTotalMultiplier("ENE");
      int meleeDamageColor = Math.abs(strTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int strikeDamageColor = Math.abs(skpTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int staminaColor = Math.abs(stmTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int defenseColor = Math.abs(defTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int healthColor = Math.abs(vitTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int kiDamageColor = Math.abs(pwrTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      int energyColor = Math.abs(eneTotalMult - 1.0) > 0.01 ? 16776960 : 16766891;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.txt(this.formatUpToOneDecimal(meleeDamage)), valueX + 15, labelStartY, meleeDamageColor, 0
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.txt(this.formatUpToOneDecimal(strikeDamage)), valueX + 15, labelStartY + 12, strikeDamageColor, 0
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.txt(this.formatUpToOneDecimal((double)stamina)), valueX + 15, labelStartY + 24, staminaColor, 0
      );
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(this.formatUpToOneDecimal(defense)), valueX + 15, labelStartY + 36, defenseColor, 0);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(this.formatUpToOneDecimal(health)), valueX + 15, labelStartY + 48, healthColor, 0);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(this.formatUpToOneDecimal(kiDamage)), valueX + 15, labelStartY + 60, kiDamageColor, 0);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.txt(this.formatUpToOneDecimal((double)energy)), valueX + 15, labelStartY + 72, energyColor, 0
      );
   }

   private void renderStatisticsInfoHexagon(GuiGraphics graphics, int mouseX, int mouseY) {
      int centerY = this.getUiHeight() / 2;
      int titleY = centerY - 88;
      int centerX = this.getUiWidth() - 85;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.statistics", new Object[0]).withStyle(style -> style.withBold(true)),
         centerX,
         titleY,
         16326244,
         0
      );
      double meleeDamage = this.statsData.getMeleeDamage();
      double maxMeleeDamage = this.statsData.getMaxMeleeDamage();
      double strikeDamage = this.statsData.getStrikeDamage();
      double maxStrikeDamage = this.statsData.getMaxStrikeDamage();
      float stamina = this.statsData.getMaxStamina();
      double defense = this.statsData.getFlatMitigation();
      double maxDefense = this.statsData.getMaxFlatMitigation();
      float health = this.statsData.getMaxHealth();
      double kiDamage = this.statsData.getKiDamage();
      double maxKiDamage = this.statsData.getMaxKiDamage();
      float energy = this.statsData.getMaxEnergy();
      double strScaling = this.statsData.getStatScaling("STR");
      double skpScaling = this.statsData.getStatScaling("SKP");
      double resScaling = (this.statsData.getStatScaling("DEF") + this.statsData.getStatScaling("STM")) / 2.0;
      double vitScaling = this.statsData.getStatScaling("VIT");
      double pwrScaling = this.statsData.getStatScaling("PWR");
      double eneScaling = this.statsData.getStatScaling("ENE");
      int hexCenterY = centerY - 20;
      float maxRadius = 35.0F;
      int strValue = this.statsData.getStats().getStrength();
      int skpValue = this.statsData.getStats().getStrikePower();
      int resValue = this.statsData.getStats().getResistance();
      int vitValue = this.statsData.getStats().getVitality();
      int pwrValue = this.statsData.getStats().getKiPower();
      int eneValue = this.statsData.getStats().getEnergy();
      int maxStatValue = Math.max(strValue, Math.max(skpValue, Math.max(resValue, Math.max(vitValue, Math.max(pwrValue, eneValue)))));
      if (maxStatValue == 0) {
         maxStatValue = 1;
      }

      int absoluteMaxStats = ConfigManager.getServerConfig().getGameplay().getMaxValue();
      float referenceValue;
      if (this.statsData.isMaxLevelValueInsteadOfStats()) {
         referenceValue = Math.max(1.0F, (float)maxStatValue);
      } else if ((float)maxStatValue >= (float)absoluteMaxStats * 0.9F) {
         referenceValue = (float)absoluteMaxStats;
      } else {
         referenceValue = (float)maxStatValue * 1.1F;
      }

      float[] statRadii = new float[]{
         maxRadius * Math.min(1.0F, (float)strValue / referenceValue),
         maxRadius * Math.min(1.0F, (float)resValue / referenceValue),
         maxRadius * Math.min(1.0F, (float)eneValue / referenceValue),
         maxRadius * Math.min(1.0F, (float)vitValue / referenceValue),
         maxRadius * Math.min(1.0F, (float)pwrValue / referenceValue),
         maxRadius * Math.min(1.0F, (float)skpValue / referenceValue)
      };
      float[] hexPointsX = new float[6];
      float[] hexPointsY = new float[6];
      float[] hexPointsMaxX = new float[6];
      float[] hexPointsMaxY = new float[6];

      for (int i = 0; i < 6; i++) {
         double angle = Math.toRadians((double)(60 * i - 90));
         hexPointsX[i] = (float)centerX + (float)((double)statRadii[i] * Math.cos(angle));
         hexPointsY[i] = (float)hexCenterY + (float)((double)statRadii[i] * Math.sin(angle));
         hexPointsMaxX[i] = (float)centerX + (float)((double)maxRadius * Math.cos(angle));
         hexPointsMaxY[i] = (float)hexCenterY + (float)((double)maxRadius * Math.sin(angle));
      }

      this.drawHexagon(graphics, centerX, hexCenterY, hexPointsX, hexPointsY, hexPointsMaxX, hexPointsMaxY);
      float textOffset = 10.0F;
      int strX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(-90.0)));
      int strY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(-90.0)));
      int resX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(-30.0)));
      int resY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(-30.0)));
      int eneX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(30.0)));
      int eneY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(30.0)));
      int vitX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(90.0)));
      int vitY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(90.0)));
      int pwrX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(150.0)));
      int pwrY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(150.0)));
      int skpX = (int)((double)centerX + (double)(maxRadius + textOffset) * Math.cos(Math.toRadians(210.0)));
      int skpY = (int)((double)hexCenterY + (double)(maxRadius + textOffset) * Math.sin(Math.toRadians(210.0)));
      Component strComponent = this.tr("gui.dragonminez.character_stats.str", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, strComponent, strX, strY, 14095410, 0);
      Component skpComponent = this.tr("gui.dragonminez.character_stats.skp", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, skpComponent, skpX, skpY, 14095410, 0);
      Component resComponent = this.tr("gui.dragonminez.character_stats.res", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, resComponent, resX, resY, 14095410, 0);
      Component pwrComponent = this.tr("gui.dragonminez.character_stats.pwr", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, pwrComponent, pwrX, pwrY, 14095410, 0);
      Component eneComponent = this.tr("gui.dragonminez.character_stats.ene", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, eneComponent, eneX, eneY, 14095410, 0);
      Component vitComponent = this.tr("gui.dragonminez.character_stats.vit", new Object[0]).withStyle(style -> style.withBold(true));
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, vitComponent, vitX, vitY, 14095410, 0);
      int strTextWidth = this.font.width(strComponent);
      int skpTextWidth = this.font.width(skpComponent);
      int resTextWidth = this.font.width(resComponent);
      int pwrTextWidth = this.font.width(pwrComponent);
      int eneTextWidth = this.font.width(eneComponent);
      int vitTextWidth = this.font.width(vitComponent);
      boolean isTransformed = this.statsData.getCharacter().hasActiveForm() || this.statsData.getCharacter().hasActiveStackForm();
      if (mouseX >= strX - strTextWidth / 2 && mouseX <= strX + strTextWidth / 2 && mouseY >= strY && mouseY <= strY + 9) {
         Component title = this.tr("gui.dragonminez.character_stats.str", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> desc = new ArrayList<>();
         desc.add(this.tr("gui.dragonminez.character_stats.melee_damage.tooltip1", new Object[0]));
         desc.add(
            this.tr("gui.dragonminez.character_stats.melee_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(strScaling)})
               .withStyle(ChatFormatting.YELLOW)
         );
         desc.add(this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxMeleeDamage)}).withStyle(ChatFormatting.GREEN));
         List<Component> extras = new ArrayList<>();
         extras.add(
            this.tr("gui.dragonminez.character_stats.melee_damage", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(meleeDamage)))
               .withStyle(ChatFormatting.AQUA)
         );
         double defensePen = this.getDefensePenetrationPercentage();
         if (defensePen > 0.0) {
            extras.add(
               this.tr("gui.dragonminez.character_stats.defense_penetration", new Object[0])
                  .append(this.txt(": " + this.formatUpToOneDecimal(defensePen) + "%"))
                  .withStyle(ChatFormatting.RED)
            );
         }

         this.appendAttackSkillInfo(extras);
         this.appendAdvancedHint(extras, this.hasAttackSkillInfo());
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 14095410);
      }

      if (mouseX >= skpX - skpTextWidth / 2 && mouseX <= skpX + skpTextWidth / 2 && mouseY >= skpY && mouseY <= skpY + 9) {
         Component title = this.tr("gui.dragonminez.character_stats.skp", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> desc = new ArrayList<>();
         desc.add(this.tr("gui.dragonminez.character_stats.strike_damage.tooltip1", new Object[0]));
         desc.add(
            this.tr("gui.dragonminez.character_stats.strike_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(skpScaling)})
               .withStyle(ChatFormatting.YELLOW)
         );
         desc.add(
            this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxStrikeDamage)}).withStyle(ChatFormatting.GREEN)
         );
         List<Component> extras = new ArrayList<>();
         extras.add(
            this.tr("gui.dragonminez.character_stats.strike_damage", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(strikeDamage)))
               .withStyle(ChatFormatting.AQUA)
         );
         double defensePen = this.getDefensePenetrationPercentage();
         if (defensePen > 0.0) {
            extras.add(
               this.tr("gui.dragonminez.character_stats.defense_penetration", new Object[0])
                  .append(this.txt(": " + this.formatUpToOneDecimal(defensePen) + "%"))
                  .withStyle(ChatFormatting.RED)
            );
         }

         this.appendAttackSkillInfo(extras);
         this.appendAdvancedHint(extras, this.hasAttackSkillInfo());
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 14095410);
      }

      if (mouseX >= resX - resTextWidth / 2 && mouseX <= resX + resTextWidth / 2 && mouseY >= resY && mouseY <= resY + 9) {
         Component title = this.tr("gui.dragonminez.character_stats.res", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> desc = new ArrayList<>();
         desc.add(this.tr("gui.dragonminez.character_stats.defense.tooltip1", new Object[0]));
         desc.add(
            this.tr("gui.dragonminez.character_stats.defense.tooltip2", new Object[]{this.formatUpToOneDecimal(resScaling)}).withStyle(ChatFormatting.YELLOW)
         );
         desc.add(this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxDefense)}).withStyle(ChatFormatting.GREEN));
         desc.add(Component.empty());
         desc.add(this.tr("gui.dragonminez.character_stats.stamina.tooltip1", new Object[0]));
         desc.add(
            this.tr("gui.dragonminez.character_stats.stamina.tooltip2", new Object[]{this.formatUpToOneDecimal(resScaling)}).withStyle(ChatFormatting.YELLOW)
         );
         List<Component> extras = new ArrayList<>();
         extras.add(
            this.tr("gui.dragonminez.character_stats.defense", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(defense)))
               .withStyle(ChatFormatting.AQUA)
         );
         extras.add(
            this.tr("gui.dragonminez.character_stats.stamina", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal((double)stamina)))
               .withStyle(ChatFormatting.AQUA)
         );
         extras.add(
            this.tr("gui.dragonminez.character_stats.stamina_per_hit", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(this.statsData.getStaminaPerHit())))
               .withStyle(ChatFormatting.GOLD)
         );
         if (isTransformed) {
            double stamDrain = this.statsData.getEffectiveStaminaDrain();
            if (stamDrain > 0.0) {
               extras.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.stamina.cost", new Object[]{this.formatUpToOneDecimal(stamDrain)})
                     .withStyle(ChatFormatting.RED)
               );
            } else if (stamDrain < 0.0) {
               extras.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.stamina.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(stamDrain))})
                     .withStyle(ChatFormatting.GREEN)
               );
            }

            double stamMult = this.statsData.getAdjustedStaminaDrainMultiplier();
            if (stamMult != 1.0) {
               ChatFormatting color = stamMult > 1.0 ? ChatFormatting.RED : ChatFormatting.GREEN;
               extras.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.stamina.multiplier", new Object[]{this.formatUpToOneDecimal(stamMult)}).withStyle(color)
               );
            }
         }

         double[] pcts = this.getDamageReductionPercentages();
         extras.add(this.txt(""));
         extras.add(
            this.tr("gui.dragonminez.character_stats.defense", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToTwoDecimals(pcts[0]) + "% "))
               .append(this.tr("gui.dragonminez.character_stats.dmg_reduction", new Object[0]))
               .withStyle(ChatFormatting.AQUA)
         );
         if (pcts[1] > 0.0) {
            extras.add(
               this.tr("gui.dragonminez.character_stats.protection", new Object[0])
                  .append(": ")
                  .append(this.txt(this.formatUpToTwoDecimals(pcts[1]) + "% "))
                  .append(this.tr("gui.dragonminez.character_stats.dmg_reduction", new Object[0]))
                  .withStyle(ChatFormatting.LIGHT_PURPLE)
            );
         }

         this.appendKiProtectionInfo(extras);
         this.appendMeditationInfo(extras, false);
         this.appendAdvancedHint(extras, this.hasKiProtectionInfo() || this.hasMeditationInfo());
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 14095410);
      }

      if (mouseX >= pwrX - pwrTextWidth / 2 && mouseX <= pwrX + pwrTextWidth / 2 && mouseY >= pwrY && mouseY <= pwrY + 9) {
         Component titlex = this.tr("gui.dragonminez.character_stats.pwr", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> descx = new ArrayList<>();
         descx.add(this.tr("gui.dragonminez.character_stats.ki_damage.tooltip1", new Object[0]));
         descx.add(
            this.tr("gui.dragonminez.character_stats.ki_damage.tooltip2", new Object[]{this.formatUpToOneDecimal(pwrScaling)}).withStyle(ChatFormatting.YELLOW)
         );
         descx.add(this.tr("gui.dragonminez.character_stats.max_value", new Object[]{this.formatUpToOneDecimal(maxKiDamage)}).withStyle(ChatFormatting.GREEN));
         List<Component> extrasx = new ArrayList<>();
         extrasx.add(
            this.tr("gui.dragonminez.character_stats.ki_damage", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(kiDamage)))
               .withStyle(ChatFormatting.AQUA)
         );
         double defensePen = this.getDefensePenetrationPercentage();
         if (defensePen > 0.0) {
            extrasx.add(
               this.tr("gui.dragonminez.character_stats.defense_penetration", new Object[0])
                  .append(this.txt(": " + this.formatUpToOneDecimal(defensePen) + "%"))
                  .withStyle(ChatFormatting.RED)
            );
         }

         this.appendAdvancedHint(extrasx, false);
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), titlex, descx, extrasx, 14095410);
      }

      if (mouseX >= eneX - eneTextWidth / 2 && mouseX <= eneX + eneTextWidth / 2 && mouseY >= eneY && mouseY <= eneY + 9) {
         Component titlex = this.tr("gui.dragonminez.character_stats.ene", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> descx = new ArrayList<>();
         descx.add(this.tr("gui.dragonminez.character_stats.max_energy.tooltip1", new Object[0]));
         descx.add(
            this.tr("gui.dragonminez.character_stats.max_energy.tooltip2", new Object[]{this.formatUpToOneDecimal(eneScaling)})
               .withStyle(ChatFormatting.YELLOW)
         );
         List<Component> extrasx = new ArrayList<>();
         extrasx.add(
            this.tr("gui.dragonminez.character_stats.max_energy", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal((double)energy)))
               .withStyle(ChatFormatting.AQUA)
         );
         if (isTransformed) {
            double eneDrain = this.statsData.getEffectiveEnergyDrain();
            if (eneDrain > 0.0) {
               extrasx.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.energy.cost", new Object[]{this.formatUpToOneDecimal(eneDrain)})
                     .withStyle(ChatFormatting.RED)
               );
            } else if (eneDrain < 0.0) {
               extrasx.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.energy.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(eneDrain))})
                     .withStyle(ChatFormatting.GREEN)
               );
            }
         }

         this.appendMeditationInfo(extrasx, true);
         this.appendAdvancedHint(extrasx, this.hasMeditationInfo());
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), titlex, descx, extrasx, 14095410);
      }

      if (mouseX >= vitX - vitTextWidth / 2 && mouseX <= vitX + vitTextWidth / 2 && mouseY >= vitY && mouseY <= vitY + 9) {
         Component titlex = this.tr("gui.dragonminez.character_stats.vit", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> descx = new ArrayList<>();
         descx.add(this.tr("gui.dragonminez.character_stats.health.tooltip1", new Object[0]));
         descx.add(
            this.tr("gui.dragonminez.character_stats.health.tooltip2", new Object[]{this.formatUpToOneDecimal(vitScaling)}).withStyle(ChatFormatting.YELLOW)
         );
         List<Component> extrasx = new ArrayList<>();
         extrasx.add(
            this.tr("gui.dragonminez.character_stats.health", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal((double)health)))
               .withStyle(ChatFormatting.AQUA)
         );
         if (isTransformed) {
            double hpDrain = this.statsData.getEffectiveHealthDrain();
            if (hpDrain > 0.0) {
               extrasx.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.health.cost", new Object[]{this.formatUpToOneDecimal(hpDrain)})
                     .withStyle(ChatFormatting.RED)
               );
            } else if (hpDrain < 0.0) {
               extrasx.add(
                  this.tr("gui.dragonminez.character_stats.form_drain.health.regen", new Object[]{this.formatUpToOneDecimal(Math.abs(hpDrain))})
                     .withStyle(ChatFormatting.GREEN)
               );
            }
         }

         this.appendAdvancedHint(extrasx, false);
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), titlex, descx, extrasx, 14095410);
      }
   }

   private void renderPlayerModel(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY) {
      LivingEntity player = Minecraft.getInstance().player;
      if (player != null) {
         int adjustedScale = this.getAdjustedModelScale(scale);
         float xRotation = (float)Math.atan((double)((float)y - mouseY) / 40.0);
         float yRotation = (float)Math.atan((double)((float)x - mouseX) / 40.0);
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(xRotation * 20.0F * (float) (Math.PI / 180.0));
         pose.mul(cameraOrientation);
         float yBodyRotO = player.yBodyRot;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = 180.0F + yRotation * 20.0F;
         player.setYRot(180.0F + yRotation * 40.0F);
         player.setXRot(-xRotation * 20.0F);
         player.yHeadRot = player.getYRot();
         player.yHeadRotO = player.getYRot();
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         EntityPreviewRenderContext.renderEntityInInventory(graphics, x, y, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player);
         graphics.pose().popPose();
         player.yBodyRot = yBodyRotO;
         player.setYRot(yRotO);
         player.setXRot(xRotO);
         player.yHeadRotO = yHeadRotO;
         player.yHeadRot = yHeadRot;
      }
   }

   private void initViewSwitchButton() {
      int centerY = this.getUiHeight() / 2;
      int buttonX = this.getUiWidth() - 45;
      int buttonY = centerY + 90;
      LivingEntity player = Minecraft.getInstance().player;
      this.viewSwitchButton = new SwitchButton(buttonX, buttonY, this.useHexagonView, Component.empty(), button -> {
         this.useHexagonView = !this.useHexagonView;
         ConfigManager.getUserConfig().setHexagonStatsDisplay(this.useHexagonView);
         ConfigManager.saveGeneralUserConfig();
         ((SwitchButton)button).toggle();
         if (this.useHexagonView) {
            player.playSound((SoundEvent)MainSounds.SWITCH_OFF.get());
         } else {
            player.playSound((SoundEvent)MainSounds.SWITCH_ON.get());
         }
      });
      this.addRenderableWidget(this.viewSwitchButton);
   }

   private void renderGravityInfo(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.statsData != null) {
         int centerY = this.getUiHeight() / 2;
         int labelX = this.getUiWidth() - 137;
         int valueX = this.getUiWidth() - 65;
         int y = centerY + 66;
         double envGravity = (double)ClientGravityState.getEnvironmentalGravity();
         double netGravity = (double)ClientGravityState.getNetGravity();
         double gravityStatMult = (double)ClientGravityState.getStatMult();
         int totalWeight = ClientGravityState.getTotalWeight();
         int effectiveWeight = ClientGravityState.getEffectiveWeight();
         int idealWeight = ClientGravityState.getIdealWeight();
         int zone = ClientGravityState.getZone();
         double weightTpMult = (double)ClientGravityState.getWeightTpMult();
         double gravityTpBonus = (double)ClientGravityState.getTpGravityMult();
         boolean hasGravity = netGravity > 0.01;
         boolean hasPenalty = gravityStatMult < 0.999;
         Component label = this.tr("gui.dragonminez.character_stats.gravity", new Object[0]);
         TextUtil.drawStringWithBorder(graphics, this.font, label, labelX, y, hasGravity ? 16742178 : 8191446, 0);
         String penStr = hasPenalty ? " -" + this.formatUpToOneDecimal((1.0 - gravityStatMult) * 100.0) + "%" : "";
         Component valueComp = hasGravity ? this.txt(this.formatUpToOneDecimal(netGravity) + "g" + penStr) : this.txt("--");
         int valueColor = hasGravity ? 16750916 : 16766891;
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, valueComp, valueX, y, valueColor, 0);
         int totalTextWidth = this.font.width(label) + this.font.width(valueComp) + 20;
         if (mouseX >= labelX && mouseX <= labelX + totalTextWidth && mouseY >= y && mouseY <= y + 9) {
            Component title = this.tr("gui.dragonminez.character_stats.gravity", new Object[0]).withStyle(ChatFormatting.GOLD);
            List<Component> desc = new ArrayList<>();
            desc.add(
               this.tr("gui.dragonminez.character_stats.gravity.tooltip.environmental", new Object[]{this.formatUpToOneDecimal(envGravity)})
                  .withStyle(ChatFormatting.YELLOW)
            );
            if (totalWeight > 0) {
               if (effectiveWeight > totalWeight) {
                  desc.add(
                     this.tr(
                           "gui.dragonminez.character_stats.gravity.tooltip.weight_load",
                           new Object[]{this.numberFormatter.format((long)totalWeight), this.numberFormatter.format((long)effectiveWeight)}
                        )
                        .withStyle(ChatFormatting.YELLOW)
                  );
               } else {
                  desc.add(
                     this.tr("gui.dragonminez.character_stats.gravity.tooltip.weight", new Object[]{this.numberFormatter.format((long)totalWeight)})
                        .withStyle(ChatFormatting.YELLOW)
                  );
               }
            }

            List<Component> extras = new ArrayList<>();
            if (idealWeight > 0) {
               GeneralServerConfig.GravityConfig gravityCfg = ConfigManager.getServerConfig().getGravity();
               int low = (int)Math.round((double)idealWeight * gravityCfg.getTpIdealRatioLow());
               int high = (int)Math.round((double)idealWeight * gravityCfg.getTpIdealRatioHigh());
               String range = this.numberFormatter.format((long)low) + " - " + this.numberFormatter.format((long)high);
               extras.add(this.tr("gui.dragonminez.character_stats.gravity.tooltip.ideal_weight", new Object[]{range}).withStyle(ChatFormatting.GOLD));
            }

            if (totalWeight > 0 && zone > 0) {
               extras.add(this.tr("gui.dragonminez.character_stats.gravity.tooltip.zone", new Object[]{this.zoneName(zone)}).withStyle(this.zoneColor(zone)));
            }

            extras.add(
               this.tr("gui.dragonminez.character_stats.gravity.tooltip.net", new Object[]{this.formatUpToOneDecimal(netGravity)})
                  .withStyle(hasGravity ? ChatFormatting.RED : ChatFormatting.GREEN)
            );
            if (hasPenalty) {
               extras.add(
                  this.tr(
                        "gui.dragonminez.character_stats.gravity.tooltip.stat_penalty",
                        new Object[]{this.formatUpToOneDecimal((1.0 - gravityStatMult) * 100.0)}
                     )
                     .withStyle(ChatFormatting.RED)
               );
            }

            if (gravityTpBonus > 1.0) {
               extras.add(
                  this.tr("gui.dragonminez.character_stats.gravity.tooltip.tp_bonus", new Object[]{this.formatUpToTwoDecimals(gravityTpBonus)})
                     .withStyle(ChatFormatting.GREEN)
               );
            }

            if (weightTpMult > 1.01 && totalWeight > 0) {
               extras.add(
                  this.tr("gui.dragonminez.character_stats.gravity.tooltip.weight_bell", new Object[]{this.formatUpToTwoDecimals(weightTpMult)})
                     .withStyle(ChatFormatting.AQUA)
               );
            }

            TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 16742178);
         }
      }
   }

   private Component zoneName(int zone) {
      String key = switch (zone) {
         case 1 -> "gui.dragonminez.character_stats.gravity.zone.light";
         case 2 -> "gui.dragonminez.character_stats.gravity.zone.ideal";
         case 3 -> "gui.dragonminez.character_stats.gravity.zone.heavy";
         case 4 -> "gui.dragonminez.character_stats.gravity.zone.overload";
         default -> "gui.dragonminez.character_stats.gravity.zone.none";
      };
      return this.tr(key, new Object[0]);
   }

   private ChatFormatting zoneColor(int zone) {
      return switch (zone) {
         case 2 -> ChatFormatting.GREEN;
         case 3 -> ChatFormatting.GOLD;
         case 4 -> ChatFormatting.RED;
         default -> ChatFormatting.GRAY;
      };
   }

   private void renderTpMultiplierInfo(GuiGraphics graphics, int mouseX, int mouseY) {
      int centerY = this.getUiHeight() / 2;
      int labelX = this.getUiWidth() - 137;
      int y = centerY + 78;
      double eps = 0.005;
      double totalDelta = 0.0;
      List<Component> extras = new ArrayList<>();
      double general = this.statsData.getTpGlobalMultiplier();
      extras.add(
         this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.general", new Object[]{this.formatUpToTwoDecimals(general)})
            .withStyle(ChatFormatting.GRAY)
      );
      totalDelta += general - 1.0;
      double clazz = this.statsData.getTpClassMultiplier();
      extras.add(
         this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.class", new Object[]{this.formatUpToTwoDecimals(clazz)}).withStyle(ChatFormatting.AQUA)
      );
      totalDelta += clazz - 1.0;
      if (this.statsData.isFrostDemonTpPassiveActive()) {
         double frost = this.statsData.getTpFrostDemonMultiplier();
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.frost_demon", new Object[]{this.formatUpToTwoDecimals(frost)})
               .withStyle(ChatFormatting.LIGHT_PURPLE)
         );
         totalDelta += frost - 1.0;
      }

      double htc = this.statsData.getTpHTCMultiplier();
      if (Math.abs(htc - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.htc", new Object[]{this.formatUpToTwoDecimals(htc)}).withStyle(ChatFormatting.GOLD)
         );
         totalDelta += htc - 1.0;
      }

      double gravity = (double)ClientGravityState.getTpGravityMult();
      if (Math.abs(gravity - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.gravity", new Object[]{this.formatUpToTwoDecimals(gravity)})
               .withStyle(ChatFormatting.GREEN)
         );
         totalDelta += gravity - 1.0;
      }

      double weightBell = (double)ClientGravityState.getWeightTpMult();
      if (Math.abs(weightBell - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.weight", new Object[]{this.formatUpToTwoDecimals(weightBell)})
               .withStyle(ChatFormatting.YELLOW)
         );
         totalDelta += weightBell - 1.0;
      }

      double potionEffect = this.statsData.getTpPotionEffectMultiplier();
      if (Math.abs(potionEffect - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.effect", new Object[]{this.formatUpToTwoDecimals(potionEffect)})
               .withStyle(ChatFormatting.LIGHT_PURPLE)
         );
         totalDelta += potionEffect - 1.0;
      }

      double mutantTp = this.statsData.getMutantTpMultiplier();
      if (Math.abs(mutantTp - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.mutant", new Object[]{this.formatUpToTwoDecimals(mutantTp)})
               .withStyle(ChatFormatting.DARK_PURPLE)
         );
         totalDelta += mutantTp - 1.0;
      }

      double progressionTp = this.statsData.getProgressionTpGainMultiplier();
      if (Math.abs(progressionTp - 1.0) > eps) {
         extras.add(
            this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.progression", new Object[]{this.formatUpToTwoDecimals(progressionTp)})
               .withStyle(ChatFormatting.GOLD)
         );
         totalDelta += progressionTp - 1.0;
      }

      double totalMultiplier = Math.max(0.0, 1.0 + totalDelta);
      String totalMult = this.formatUpToTwoDecimals(totalMultiplier);
      Component label = this.tr("gui.dragonminez.character_stats.tp_multiplier", new Object[0]);
      Component separator = this.txt(": ");
      Component value = this.txt("x" + totalMult);
      TextUtil.drawStringWithBorder(graphics, this.font, label, labelX, y, 8191446, 0);
      int separatorX = labelX + this.font.width(label);
      TextUtil.drawStringWithBorder(graphics, this.font, separator, separatorX, y, 8191446, 0);
      int valueColor = totalMultiplier > 1.0 ? 16776960 : 16770451;
      int valueX = separatorX + this.font.width(separator);
      TextUtil.drawStringWithBorder(graphics, this.font, value, valueX, y, valueColor, 0);
      int textWidth = this.font.width(label) + this.font.width(separator) + this.font.width(value);
      if (mouseX >= labelX && mouseX <= labelX + textWidth && mouseY >= y && mouseY <= y + 9) {
         Component title = this.tr("gui.dragonminez.character_stats.tp_multiplier", new Object[0]).withStyle(ChatFormatting.GOLD);
         List<Component> desc = new ArrayList<>();
         desc.add(this.tr("gui.dragonminez.character_stats.tp_multiplier.tooltip.total", new Object[]{totalMult}).withStyle(ChatFormatting.YELLOW));
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, 8191446);
      }
   }

   private void appendAdvancedHint(List<Component> extras, boolean hasAdvancedContent) {
      if (!hasShiftDown() && hasAdvancedContent) {
         extras.add(this.tr("gui.dragonminez.character_stats.shift_hint", new Object[0]).withStyle(ChatFormatting.DARK_GRAY));
      }
   }

   private boolean hasAttackSkillInfo() {
      Skills skills = this.statsData.getSkills();
      if (skills.hasSkill("ki_infusion") && skills.getSkillLevel("ki_infusion") > 0) {
         return true;
      } else if (skills.hasSkill("kimanipulation") && skills.getSkillLevel("kimanipulation") > 0) {
         String weaponType = this.statsData.getStatus().getKiWeaponType();
         return weaponType != null && ConfigManager.getCombatConfig().getKiWeaponConfig(weaponType) != null;
      } else {
         return false;
      }
   }

   private boolean hasKiProtectionInfo() {
      Skills skills = this.statsData.getSkills();
      return skills.hasSkill("kiprotection") && skills.getSkillLevel("kiprotection") > 0;
   }

   private boolean hasMeditationInfo() {
      return this.statsData.getSkills().getSkillLevel("meditation") > 0;
   }

   private MutableComponent skillBonusLine(String labelKey, String valuePart, boolean active) {
      MutableComponent line = this.tr(labelKey, new Object[0])
         .append(": ")
         .append(this.txt(valuePart))
         .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY);
      if (!active) {
         line.append(this.tr("gui.dragonminez.character_stats.inactive_suffix", new Object[0]).withStyle(ChatFormatting.DARK_GRAY));
      }

      return line;
   }

   private void appendAttackSkillInfo(List<Component> extras) {
      if (hasShiftDown()) {
         Skills skills = this.statsData.getSkills();
         int infusionLevel = skills.getSkillLevel("ki_infusion");
         if (skills.hasSkill("ki_infusion") && infusionLevel > 0) {
            double dmgPerLevel = ConfigManager.getCombatConfig().getKiInfusionDamagePerLevel();
            double infuseDamage = (double)this.statsData.getMaxEnergy() * dmgPerLevel * (double)infusionLevel;
            extras.add(
               this.skillBonusLine(
                  "gui.dragonminez.character_stats.infuse_damage", "+" + this.formatUpToOneDecimal(infuseDamage), skills.isSkillActive("ki_infusion")
               )
            );
         }

         int weaponLevel = skills.getSkillLevel("kimanipulation");
         if (skills.hasSkill("kimanipulation") && weaponLevel > 0) {
            String weaponType = this.statsData.getStatus().getKiWeaponType();
            CombatConfig.KiWeaponConfig kiCfg = weaponType != null ? ConfigManager.getCombatConfig().getKiWeaponConfig(weaponType) : null;
            if (kiCfg != null) {
               double weaponMult = (double)weaponLevel * 0.1;
               double weaponDamage = kiCfg.getBaseDamage() + this.statsData.getKiDamage() * kiCfg.getKiScalingDamage() * weaponMult;
               extras.add(
                  this.skillBonusLine(
                     "gui.dragonminez.character_stats.ki_weapon_damage", "+" + this.formatUpToOneDecimal(weaponDamage), skills.isSkillActive("kimanipulation")
                  )
               );
            }
         }
      }
   }

   private void appendKiProtectionInfo(List<Component> extras) {
      if (hasShiftDown()) {
         Skills skills = this.statsData.getSkills();
         int level = skills.getSkillLevel("kiprotection");
         if (skills.hasSkill("kiprotection") && level > 0) {
            double pct = (double)level * ConfigManager.getCombatConfig().getKiProtectionMitigationPerLevel() * 100.0;
            boolean active = skills.isSkillActive("kiprotection");
            MutableComponent line = this.tr("gui.dragonminez.character_stats.ki_protection", new Object[0])
               .append(": ")
               .append(this.txt(this.formatUpToOneDecimal(pct) + "% "))
               .append(this.tr("gui.dragonminez.character_stats.dmg_reduction", new Object[0]))
               .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY);
            if (!active) {
               line.append(" ").append(this.tr("gui.dragonminez.character_stats.inactive_suffix", new Object[0]).withStyle(ChatFormatting.DARK_GRAY));
            }

            extras.add(line);
         }
      }
   }

   private void appendMeditationInfo(List<Component> extras, boolean energy) {
      if (hasShiftDown()) {
         int level = this.statsData.getSkills().getSkillLevel("meditation");
         if (level > 0) {
            double base = energy ? this.baseEnergyRegenPerSec() : this.baseStaminaRegenPerSec();
            double bonusPct = (double)level * 0.05;
            double bonus = base * bonusPct;
            extras.add(
               this.tr(
                     "gui.dragonminez.character_stats.meditation_bonus",
                     new Object[]{this.formatUpToOneDecimal(bonus), this.formatUpToOneDecimal(bonusPct * 100.0)}
                  )
                  .withStyle(ChatFormatting.GREEN)
            );
         }
      }
   }

   private RaceStatsConfig.ClassStats currentClassStats() {
      RaceStatsConfig sc = ConfigManager.getRaceStats(this.statsData.getCharacter().getRaceName());
      return sc != null ? sc.getClassStats(this.statsData.getCharacter().getCharacterClass()) : null;
   }

   private double baseStaminaRegenPerSec() {
      RaceStatsConfig.ClassStats cs = this.currentClassStats();
      return cs == null
         ? 0.0
         : (cs.getBaseSp5() + (double)this.statsData.getStats().getResistance() * this.statsData.getTotalMultiplier("RES") * cs.getSp5StmScaling()) * 0.2;
   }

   private double baseEnergyRegenPerSec() {
      RaceStatsConfig.ClassStats cs = this.currentClassStats();
      return cs == null
         ? 0.0
         : (cs.getBaseEp5() + (double)this.statsData.getStats().getEnergy() * this.statsData.getTotalMultiplier("ENE") * cs.getEp5EneScaling()) * 0.2;
   }

   private String formatUpToOneDecimal(double value) {
      return this.oneDecimalFormatter.format(value);
   }

   private String formatUpToTwoDecimals(double value) {
      return this.twoDecimalFormatter.format(value);
   }

   private boolean shouldUseScientificForTps(float tps) {
      return !Float.isFinite(tps) ? false : Math.floor((double)Math.abs(tps)) >= 1.0E10;
   }

   private String formatTpsDisplay(float tps) {
      if (!Float.isNaN(tps) && !Float.isInfinite(tps)) {
         return this.shouldUseScientificForTps(tps) ? this.scientificFormatter.format((double)tps) : this.fullTpsFormatter.format((double)tps);
      } else {
         return String.valueOf(tps);
      }
   }

   private double getDefensePenetrationPercentage() {
      int skillLevel = this.statsData.getSkills().getSkillLevel("defense_penetration");
      int enchLevel = 0;
      if (Minecraft.getInstance().player != null) {
         enchLevel = MainEnchants.level(Minecraft.getInstance().player, MainEnchants.DEFENSE_PENETRATION);
      }

      return Math.min(0.5, (double)skillLevel * 0.025 + (double)enchLevel * 0.025) * 100.0;
   }

   private void drawHexagon(GuiGraphics graphics, int centerX, int centerY, float[] pointsX, float[] pointsY, float[] maxPointsX, float[] maxPointsY) {
      PoseStack pose = graphics.pose();
      Matrix4f matrix = pose.last().pose();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.disableCull();
      String auraColorHex = this.statsData.getCharacter().getAuraColor();
      float[] auraRgb = ColorUtils.hexToRgb(auraColorHex);
      float fillR = auraRgb[0];
      float fillG = auraRgb[1];
      float fillB = auraRgb[2];
      float fillAlpha = 0.3F;
      BufferBuilder buffer = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

      for (int i = 0; i < 6; i++) {
         int next = (i + 1) % 6;
         buffer.addVertex(matrix, (float)centerX, (float)centerY, 0.0F).setColor(fillR, fillG, fillB, fillAlpha);
         buffer.addVertex(matrix, pointsX[i], pointsY[i], 0.0F).setColor(fillR, fillG, fillB, fillAlpha);
         buffer.addVertex(matrix, pointsX[next], pointsY[next], 0.0F).setColor(fillR, fillG, fillB, fillAlpha);
      }

      BufferUploader.drawWithShader(buffer.buildOrThrow());
      buffer = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      float outlineR = 0.0F;
      float outlineG = 0.0F;
      float outlineB = 0.0F;
      float outlineAlpha = 1.0F;
      float borderWidth = 0.5F;

      for (int i = 0; i < 6; i++) {
         int next = (i + 1) % 6;
         float x1 = maxPointsX[i];
         float y1 = maxPointsY[i];
         float x2 = maxPointsX[next];
         float y2 = maxPointsY[next];
         float dx = x2 - x1;
         float dy = y2 - y1;
         float length = (float)Math.sqrt((double)(dx * dx + dy * dy));
         float perpX = -dy / length * borderWidth;
         float perpY = dx / length * borderWidth;
         buffer.addVertex(matrix, x1 + perpX, y1 + perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
         buffer.addVertex(matrix, x1 - perpX, y1 - perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
         buffer.addVertex(matrix, x2 + perpX, y2 + perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
         buffer.addVertex(matrix, x2 + perpX, y2 + perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
         buffer.addVertex(matrix, x1 - perpX, y1 - perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
         buffer.addVertex(matrix, x2 - perpX, y2 - perpY, 0.0F).setColor(outlineR, outlineG, outlineB, outlineAlpha);
      }

      BufferUploader.drawWithShader(buffer.buildOrThrow());
      buffer = Tesselator.getInstance().begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
      float lineR = auraRgb[0];
      float lineG = auraRgb[1];
      float lineB = auraRgb[2];
      float lineAlpha = 0.8F;

      for (int i = 0; i < 6; i++) {
         int next = (i + 1) % 6;
         buffer.addVertex(matrix, pointsX[i], pointsY[i], 0.0F).setColor(lineR, lineG, lineB, lineAlpha);
         buffer.addVertex(matrix, pointsX[next], pointsY[next], 0.0F).setColor(lineR, lineG, lineB, lineAlpha);
      }

      BufferUploader.drawWithShader(buffer.buildOrThrow());
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }
}
