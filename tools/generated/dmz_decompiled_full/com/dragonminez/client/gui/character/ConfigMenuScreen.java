package com.dragonminez.client.gui.character;

import com.dragonminez.client.crowdin.CrowdinManager;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.SwitchButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.gui.config.OverShoulderCameraScreen;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralUserConfig;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.DynamicGrowthToggleC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.DynamicGrowthStat;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConfigMenuScreen extends BaseMenuScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation STAT_BUTTONS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final int CONFIG_ITEM_HEIGHT = 20;
   private static final int MAX_VISIBLE_CONFIGS = 7;
   private int tickCount = 0;
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private final ScrollbarState scrollBar = new ScrollbarState();
   private int holdTicks = 0;
   private int heldConfigIndex = -1;
   private int heldDelta = 0;
   private GeneralUserConfig userConfig;
   private final List<ConfigMenuScreen.ConfigOption> configOptions = new ArrayList<>();
   private final List<CustomTextureButton> decreaseButtons = new ArrayList<>();
   private final List<CustomTextureButton> increaseButtons = new ArrayList<>();
   private final List<SwitchButton> switchButtons = new ArrayList<>();
   private final List<Button> actionButtons = new ArrayList<>();

   public ConfigMenuScreen() {
      super(Component.translatable("gui.dragonminez.config.title"));
   }

   @Override
   protected void init() {
      super.init();
      this.loadConfig();
      this.initializeConfigOptions();
      this.initConfigButtons();
      this.updateConfigsList();
   }

   private void loadConfig() {
      this.userConfig = ConfigManager.getUserConfig();
   }

   private void initializeConfigOptions() {
      this.configOptions.clear();
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.firstPersonAnimated",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getFirstPersonAnimated() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setFirstPersonAnimated(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.impactFramesEnabled",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.isImpactFramesEnabled() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setImpactFramesEnabled(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.taiyokenInvertPalette",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getTaiyokenInvertPalette() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setTaiyokenInvertPalette(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.transformationOutlines",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getTransformationOutlines() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setTransformationOutlines(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.showAccumulativeDamage",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getShowAccumulativeDamage() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setShowAccumulativeDamage(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.techniqueHotbarRightSide",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getTechniqueHotbarRightSide() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setTechniqueHotbarRightSide(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.alwaysVisibleHudValues",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getAlwaysVisibleHudValues() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setAlwaysVisibleHudValues(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.hideHudNumbers",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getHideHudNumbers() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setHideHudNumbers(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.xenoverseHudPosX",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getXenoverseHudPosX().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setXenoverseHudPosX(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.xenoverseHudPosY",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getXenoverseHudPosY().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setXenoverseHudPosY(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.xenoverseHudScale",
               ConfigMenuScreen.ConfigType.FLOAT,
               this.userConfig.getXenoverseHudScale(),
               0.5F,
               2.5F,
               v -> this.userConfig.setXenoverseHudScale(v)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.advancedDescription",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getAdvancedDescription() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setAdvancedDescription(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.advancedDescriptionPercentage",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getAdvancedDescriptionPercentage() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setAdvancedDescriptionPercentage(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.alternativeHud",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getAlternativeHud() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setAlternativeHud(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.hexagonStatsDisplay",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getHexagonStatsDisplay() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setHexagonStatsDisplay(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.menuScaleMultiplier",
               ConfigMenuScreen.ConfigType.FLOAT,
               this.userConfig.getMenuScaleMultiplier(),
               0.75F,
               2.5F,
               v -> this.userConfig.setMenuScaleMultiplier(v)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.utilityMenuScaleMultiplier",
               ConfigMenuScreen.ConfigType.FLOAT,
               this.userConfig.getUtilityMenuScaleMultiplier(),
               0.5F,
               2.5F,
               v -> this.userConfig.setUtilityMenuScaleMultiplier(v)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.healthBarPosX",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getHealthBarPosX().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setHealthBarPosX(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.healthBarPosY",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getHealthBarPosY().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setHealthBarPosY(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.energyBarPosX",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getEnergyBarPosX().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setEnergyBarPosX(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.energyBarPosY",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getEnergyBarPosY().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setEnergyBarPosY(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.staminaBarPosX",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getStaminaBarPosX().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setStaminaBarPosX(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.staminaBarPosY",
               ConfigMenuScreen.ConfigType.INT,
               (float)this.userConfig.getStaminaBarPosY().intValue(),
               -1000.0F,
               2000.0F,
               v -> this.userConfig.setStaminaBarPosY(v.intValue())
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.cameraMovementDuringFlight",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getCameraMovementDuringFlight() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setCameraMovementDuringFlight(v > 0.0F)
            )
         );
      this.configOptions
         .add(
            new ConfigMenuScreen.ConfigOption(
               "config.liveCrowdinTranslations",
               ConfigMenuScreen.ConfigType.BOOLEAN,
               this.userConfig.getLiveCrowdinTranslations() ? 1.0F : 0.0F,
               0.0F,
               1.0F,
               v -> this.userConfig.setLiveCrowdinTranslations(v > 0.0F)
            )
         );
      this.initializeDynamicGrowthOptions();
      this.configOptions
         .add(new ConfigMenuScreen.ConfigOption("config.overShoulderCamera", () -> this.minecraft.setScreen(new OverShoulderCameraScreen(this))));
   }

   private void initializeDynamicGrowthOptions() {
      if (this.minecraft != null && this.minecraft.player != null) {
         if (ConfigManager.getServerConfig().getDynamicGrowth().isEnabled()) {
            StatsProvider.get(StatsCapability.INSTANCE, this.minecraft.player)
               .ifPresent(
                  data -> {
                     for (DynamicGrowthStat stat : DynamicGrowthStat.values()) {
                        this.configOptions
                           .add(
                              new ConfigMenuScreen.ConfigOption(
                                 "config.dynamicGrowthFor" + stat.key(),
                                 ConfigMenuScreen.ConfigType.BOOLEAN,
                                 data.getDynamicGrowth().isGrowthEnabled(stat) ? 1.0F : 0.0F,
                                 0.0F,
                                 1.0F,
                                 v -> {
                                    boolean enabled = v > 0.0F;
                                    data.getDynamicGrowth().setGrowthEnabled(stat, enabled);
                                    NetworkHandler.sendToServer(new DynamicGrowthToggleC2S(stat.key(), enabled));
                                 }
                              )
                           );
                     }
                  }
               );
         }
      }
   }

   private void initConfigButtons() {
      this.clearConfigButtons();
      LivingEntity player = this.minecraft.player;
      int rightPanelX = this.getRightPanelX() - 5;
      int centerY = this.getUiHeight() / 2;
      int rightPanelY = centerY - 105;
      int startY = rightPanelY + 35;
      int visibleStart = this.scrollOffset;
      int visibleEnd = Math.min(visibleStart + 7, this.configOptions.size());

      for (int i = visibleStart; i < visibleEnd; i++) {
         ConfigMenuScreen.ConfigOption option = this.configOptions.get(i);
         int itemY = startY + (i - visibleStart) * 20;
         int index = i;
         if (option.type == ConfigMenuScreen.ConfigType.BOOLEAN) {
            boolean isOn = option.value > 0.0F;
            int switchX = rightPanelX + 65;
            int switchY = itemY + 3;
            SwitchButton switchBtn = new SwitchButton(switchX, switchY, isOn, Component.empty(), button -> {
               this.modifyConfigValue(index, 1);
               ((SwitchButton)button).toggle();
               if (isOn) {
                  player.playSound((SoundEvent)MainSounds.SWITCH_OFF.get());
               } else {
                  player.playSound((SoundEvent)MainSounds.SWITCH_ON.get());
               }
            });
            this.switchButtons.add(switchBtn);
            this.addRenderableWidget(switchBtn);
         } else if (option.type == ConfigMenuScreen.ConfigType.ACTION) {
            TexturedTextButton actionBtn = new TexturedTextButton.Builder()
               .position(rightPanelX + 45, itemY)
               .size(74, 20)
               .texture(STAT_BUTTONS)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.config.open", new Object[0]))
               .onPress(b -> option.action.run())
               .build();
            this.actionButtons.add(actionBtn);
            this.addRenderableWidget(actionBtn);
         } else {
            CustomTextureButton decreaseBtn = new CustomTextureButton.Builder()
               .position(rightPanelX + 25, itemY + 3)
               .size(14, 11)
               .texture(STAT_BUTTONS)
               .textureCoords(142, 0, 142, 10)
               .textureSize(10, 10)
               .onPress(button -> {
                  this.modifyConfigValue(index, -1);
                  this.heldConfigIndex = index;
                  this.heldDelta = -1;
                  this.holdTicks = 0;
               })
               .build();
            this.decreaseButtons.add(decreaseBtn);
            this.addRenderableWidget(decreaseBtn);
            CustomTextureButton increaseBtn = new CustomTextureButton.Builder()
               .position(rightPanelX + 108, itemY + 3)
               .size(14, 11)
               .texture(STAT_BUTTONS)
               .textureCoords(0, 0, 0, 10)
               .textureSize(10, 10)
               .onPress(button -> {
                  this.modifyConfigValue(index, 1);
                  this.heldConfigIndex = index;
                  this.heldDelta = 1;
                  this.holdTicks = 0;
               })
               .build();
            this.increaseButtons.add(increaseBtn);
            this.addRenderableWidget(increaseBtn);
         }
      }
   }

   private void clearConfigButtons() {
      for (CustomTextureButton btn : this.decreaseButtons) {
         this.removeWidget(btn);
      }

      for (CustomTextureButton btn : this.increaseButtons) {
         this.removeWidget(btn);
      }

      for (SwitchButton btn : this.switchButtons) {
         this.removeWidget(btn);
      }

      for (Button btn : this.actionButtons) {
         this.removeWidget(btn);
      }

      this.decreaseButtons.clear();
      this.increaseButtons.clear();
      this.switchButtons.clear();
      this.actionButtons.clear();
   }

   @Override
   public void tick() {
      super.tick();
      this.tickCount++;
      if (this.heldConfigIndex != -1) {
         this.holdTicks++;
         if (this.holdTicks > 10 && this.holdTicks % 2 == 0) {
            this.modifyConfigValue(this.heldConfigIndex, this.heldDelta);
         }
      }
   }

   private void updateConfigsList() {
      this.maxScroll = Math.max(0, this.configOptions.size() - 7);
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
      graphics.pose().pushPose();
      graphics.pose().translate((float)leftOffset, 0.0F, 0.0F);
      this.renderLeftPanel(graphics, uiMouseX - leftOffset, uiMouseY);
      graphics.pose().popPose();
      int rightOffset = this.getRightPanelSwitchOffset(partialTick);
      this.updateRightPanelButtonOffsets(rightOffset);
      graphics.pose().pushPose();
      graphics.pose().translate((float)rightOffset, 0.0F, 0.0F);
      this.renderRightPanel(graphics, uiMouseX - rightOffset, uiMouseY);
      graphics.pose().popPose();
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void updateRightPanelButtonOffsets(int rightOffset) {
      int rightPanelX = this.getRightPanelX() - 5;
      int decreaseX = rightPanelX + 25 + rightOffset;
      int increaseX = rightPanelX + 108 + rightOffset;
      int switchX = rightPanelX + 65 + rightOffset;

      for (CustomTextureButton btn : this.decreaseButtons) {
         btn.setX(decreaseX);
      }

      for (CustomTextureButton btn : this.increaseButtons) {
         btn.setX(increaseX);
      }

      for (SwitchButton btn : this.switchButtons) {
         btn.setX(switchX);
      }

      for (Button btn : this.actionButtons) {
         btn.setX(decreaseX);
      }
   }

   private int getLeftPanelX() {
      return this.getUiWidth() / 2 - 143;
   }

   private int getRightPanelX() {
      return this.getUiWidth() / 2 + 2;
   }

   private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int leftPanelX = this.getLeftPanelX();
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, leftPanelX, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, leftPanelX + 17, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.config.options", new Object[0]).withStyle(ChatFormatting.BOLD), leftPanelX + 70, leftPanelY + 17, -10496
      );
      this.renderConfigsList(graphics, leftPanelX, leftPanelY, mouseX, mouseY);
   }

   private void renderConfigsList(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      int startY = panelY + 35;
      int visibleStart = this.scrollOffset;
      int visibleEnd = Math.min(visibleStart + 7, this.configOptions.size());
      graphics.enableScissor(
         this.toScreenCoord((double)(panelX + 5)),
         this.toScreenCoord((double)startY),
         this.toScreenCoord((double)(panelX + 144)),
         this.toScreenCoord((double)(startY + 140))
      );
      graphics.pose().pushPose();
      graphics.pose().scale(0.75F, 0.75F, 0.75F);

      for (int i = visibleStart; i < visibleEnd; i++) {
         ConfigMenuScreen.ConfigOption option = this.configOptions.get(i);
         int itemY = startY + (i - visibleStart) * 20;
         String displayName = this.tr("gui.dragonminez." + option.key, new Object[0]).getString();
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt(displayName), (int)((float)(panelX + 15) / 0.75F), (int)((float)itemY / 0.75F) + 6, -1);
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      this.scrollBar.update(panelX + 128, 3, startY, 140, (float)this.maxScroll);
      if (this.maxScroll > 0) {
         int scrollBarX = panelX + 128;
         int scrollBarHeight = 140;
         int totalItems = this.configOptions.size();
         graphics.fill(scrollBarX, startY, scrollBarX + 3, startY + scrollBarHeight, -13421773);
         float scrollPercent = (float)this.scrollOffset / (float)this.maxScroll;
         float visiblePercent = 7.0F / (float)totalItems;
         int indicatorHeight = Math.max(20, (int)((float)scrollBarHeight * visiblePercent));
         int indicatorY = startY + (int)((float)(scrollBarHeight - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
      }
   }

   private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int rightPanelX = this.getRightPanelX();
      int centerY = this.getUiHeight() / 2;
      int rightPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, rightPanelX, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, rightPanelX + 17, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.config.values", new Object[0]).withStyle(ChatFormatting.BOLD),
         rightPanelX + 70,
         rightPanelY + 17,
         -10496
      );
      this.renderConfigValues(graphics, rightPanelX, rightPanelY);
   }

   private void renderConfigValues(GuiGraphics graphics, int panelX, int panelY) {
      int startY = panelY + 35;
      int visibleStart = this.scrollOffset;
      int visibleEnd = Math.min(visibleStart + 7, this.configOptions.size());

      for (int i = visibleStart; i < visibleEnd; i++) {
         ConfigMenuScreen.ConfigOption option = this.configOptions.get(i);
         int itemY = startY + (i - visibleStart) * 20;
         if (option.type != ConfigMenuScreen.ConfigType.BOOLEAN && option.type != ConfigMenuScreen.ConfigType.ACTION) {
            String valueText;
            if (option.type == ConfigMenuScreen.ConfigType.FLOAT) {
               valueText = String.format("%.2f", option.value);
            } else {
               valueText = String.valueOf((int)option.value);
            }

            graphics.pose().pushPose();
            graphics.pose().scale(0.75F, 0.75F, 0.75F);
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.txt(valueText), (int)((float)(panelX + 69) / 0.75F), (int)((float)(itemY + 5) / 0.75F), -1
            );
            graphics.pose().popPose();
         }
      }
   }

   private void modifyConfigValue(int index, int delta) {
      if (index >= 0 && index < this.configOptions.size()) {
         ConfigMenuScreen.ConfigOption option = this.configOptions.get(index);
         boolean isShiftDown = Screen.hasShiftDown();
         if (option.type == ConfigMenuScreen.ConfigType.BOOLEAN) {
            option.value = option.value > 0.0F ? 0.0F : 1.0F;
         } else if (option.type == ConfigMenuScreen.ConfigType.INT) {
            int step = isShiftDown ? 5 : 1;
            option.value = Math.max(option.min, Math.min(option.max, option.value + (float)(delta * step)));
         } else if (option.type == ConfigMenuScreen.ConfigType.FLOAT) {
            float step;
            if (!"config.menuScaleMultiplier".equals(option.key)
               && !"config.xenoverseHudScale".equals(option.key)
               && !"config.utilityMenuScaleMultiplier".equals(option.key)) {
               step = isShiftDown ? 1.0F : 0.1F;
            } else {
               step = isShiftDown ? 0.25F : 0.05F;
            }

            option.value = Math.max(option.min, Math.min(option.max, option.value + (float)delta * step));
            option.value = (float)Math.round(option.value * 100.0F) / 100.0F;
         }

         option.setter.accept(option.value);
         if ("config.menuScaleMultiplier".equals(option.key)) {
            this.rebuildWidgetsWithoutTransition();
         }

         if ("config.liveCrowdinTranslations".equals(option.key) && this.minecraft != null) {
            if (this.userConfig.getLiveCrowdinTranslations()) {
               CrowdinManager.fetchLanguage(this.minecraft.options.languageCode);
            } else {
               CrowdinManager.clearCache();
            }

            this.minecraft.reloadResourcePacks();
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int leftPanelX = this.getLeftPanelX();
      int rightPanelX = this.getRightPanelX();
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      boolean overLeft = uiMouseX >= (double)leftPanelX && uiMouseX <= (double)(leftPanelX + 148);
      boolean overRight = uiMouseX >= (double)rightPanelX && uiMouseX <= (double)(rightPanelX + 148);
      if ((overLeft || overRight) && uiMouseY >= (double)(panelY + 40) && uiMouseY <= (double)(panelY + 219)) {
         int scrollAmount = (int)Math.signum(scrollY);
         this.scrollOffset = Math.max(0, Math.min(this.maxScroll, this.scrollOffset - scrollAmount));
         this.initConfigButtons();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      if (this.scrollBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.scrollOffset = Math.round(this.scrollBar.scrollFor(uiMouseY));
         this.initConfigButtons();
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.scrollBar.isDragging()) {
         this.scrollOffset = Math.round(this.scrollBar.scrollFor(this.toUiY(mouseY)));
         this.initConfigButtons();
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.heldConfigIndex = -1;
      if (this.scrollBar.isDragging()) {
         this.scrollBar.stopDrag();
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public void removed() {
      if (this.minecraft != null) {
         ConfigManager.saveGeneralUserConfig();
      }

      super.removed();
   }

   private static class ConfigOption {
      String key;
      ConfigMenuScreen.ConfigType type;
      float value;
      float min;
      float max;
      Consumer<Float> setter;
      Runnable action;

      ConfigOption(String key, ConfigMenuScreen.ConfigType type, float value, float min, float max, Consumer<Float> setter) {
         this.key = key;
         this.type = type;
         this.value = value;
         this.min = min;
         this.max = max;
         this.setter = setter;
      }

      ConfigOption(String key, Runnable action) {
         this.key = key;
         this.type = ConfigMenuScreen.ConfigType.ACTION;
         this.action = action;
      }
   }

   private static enum ConfigType {
      INT,
      FLOAT,
      BOOLEAN,
      ACTION;
   }
}
