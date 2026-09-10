package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.ColorSlider;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.CreateTechniqueC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class TechniqueCreatorScreen extends ScaledScreen {
   private static final ResourceLocation MENU_NPC = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menunpc.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final NumberFormat COST_NUMBER_FORMAT = NumberFormat.getIntegerInstance(new Locale("es", "ES"));
   private static final int PANEL_W = 345;
   private static final int PANEL_H = 273;
   private final Screen parent;
   private int panelX;
   private int panelY;
   private static final int HOLD_DELAY = 5;
   private static final int HOLD_INTERVAL = 2;
   private Runnable heldAdjuster;
   private int holdTicks;
   private EditBox nameField;
   private EditBox hexField;
   private CustomTextureButton interiorColorButton;
   private CustomTextureButton exteriorColorButton;
   private CustomTextureButton outlineColorButton;
   private CustomTextureButton utilityLeft;
   private CustomTextureButton utilityRight;
   private CustomTextureButton sizeLeft;
   private CustomTextureButton sizeRight;
   private CustomTextureButton speedLeft;
   private CustomTextureButton speedRight;
   private CustomTextureButton armorLeft;
   private CustomTextureButton armorRight;
   private ColorSlider hueSlider;
   private ColorSlider saturationSlider;
   private ColorSlider valueSlider;
   private boolean updatingFromHex = false;
   private boolean colorPickerVisible = false;
   private boolean auraDefaultsApplied = false;
   private String colorTarget = "exterior";
   private String creatorName;
   private KiAttackData.KiType creatorType = KiAttackData.KiType.SMALL_BALL;
   private KiAttackData.Utility creatorUtility = KiAttackData.Utility.DAMAGE;
   private float creatorDamage = KiAttackData.getDefaultDamageForType(KiAttackData.KiType.SMALL_BALL);
   private float creatorSize = KiAttackData.getDefaultSizeForType(KiAttackData.KiType.SMALL_BALL);
   private float creatorSpeed = 1.0F;
   private int creatorArmorPen = 0;
   private int creatorCast = 20;
   private int creatorCooldown = 20;
   private float kiCost = 25.0F;
   private float tpCost = 120.0F;
   private int creatorColorInterior = 16777215;
   private int creatorColorExterior = 44783;
   private int creatorColorOutline = 16777215;
   private KiAttackData.SecondaryEffectType creatorSecondaryType = KiAttackData.SecondaryEffectType.NONE;
   private KiAttackData.AffectedStat creatorAffectedStat = KiAttackData.AffectedStat.STR;
   private int creatorSecondaryIntensity = 5;
   private int creatorSecondaryDuration = 1;

   public TechniqueCreatorScreen(Screen parent) {
      super(Component.translatable("gui.dragonminez.skills.creator.title"));
      this.parent = parent;
      this.creatorName = Component.translatable("gui.dragonminez.skills.new_skill").getString();
   }

   @Override
   protected int getMinGuiWidth() {
      return 365;
   }

   @Override
   protected int getMinGuiHeight() {
      return 293;
   }

   protected void init() {
      super.init();
      if (!this.auraDefaultsApplied) {
         this.applyAuraDefaults();
      }

      this.recomputeDerivedValues();
      this.clearWidgets();
      this.panelX = (this.getUiWidth() - 345) / 2;
      this.panelY = (this.getUiHeight() - 273) / 2;
      int r1 = this.panelY + 32;
      int r2 = this.panelY + 46;
      int r3 = this.panelY + 58;
      int r4 = this.panelY + 70;
      this.nameField = new EditBox(this.font, this.panelX + 138, this.panelY + 30, 70, 12, Component.empty());
      this.nameField.setMaxLength(24);
      this.nameField.setValue(this.creatorName);
      this.nameField.setResponder(v -> this.creatorName = v);
      this.addRenderableWidget(this.nameField);
      this.addRenderableWidget(
         this.createArrowButton(this.panelX + 16, r1 + 2, true, btn -> this.setCreatorType(this.prevEnum(this.creatorType, KiAttackData.KiType.values())))
      );
      this.addRenderableWidget(
         this.createArrowButton(this.panelX + 118, r1 + 2, false, btn -> this.setCreatorType(this.nextEnum(this.creatorType, KiAttackData.KiType.values())))
      );
      this.utilityLeft = this.createArrowButton(this.panelX + 16, r2 + 2, true, btn -> this.toggleUtility());
      this.utilityRight = this.createArrowButton(this.panelX + 118, r2 + 2, false, btn -> this.toggleUtility());
      this.addRenderableWidget(this.utilityLeft);
      this.addRenderableWidget(this.utilityRight);
      this.updateUtilityArrowsVisibility();
      this.interiorColorButton = this.createSwatchButton(this.panelX + 300, r1 - 2, "interior");
      this.exteriorColorButton = this.createSwatchButton(this.panelX + 300, r2 - 2, "exterior");
      this.outlineColorButton = this.createSwatchButton(this.panelX + 300, r3 - 2, "outline");
      this.addRenderableWidget(this.interiorColorButton);
      this.addRenderableWidget(this.exteriorColorButton);
      this.addRenderableWidget(this.outlineColorButton);
      int beLeft = this.panelX + 18;
      int beRight = this.panelX + 150;
      this.addRenderableWidget(this.arrow(beLeft, this.panelY + 128, true, () -> this.adjustDamage(false)));
      this.addRenderableWidget(this.arrow(beRight, this.panelY + 128, false, () -> this.adjustDamage(true)));
      this.sizeLeft = this.arrow(beLeft, this.panelY + 148, true, () -> this.adjustSize(false));
      this.sizeRight = this.arrow(beRight, this.panelY + 148, false, () -> this.adjustSize(true));
      this.addRenderableWidget(this.sizeLeft);
      this.addRenderableWidget(this.sizeRight);
      this.speedLeft = this.arrow(beLeft, this.panelY + 168, true, () -> this.adjustSpeed(false));
      this.speedRight = this.arrow(beRight, this.panelY + 168, false, () -> this.adjustSpeed(true));
      this.addRenderableWidget(this.speedLeft);
      this.addRenderableWidget(this.speedRight);
      this.armorLeft = this.arrow(beLeft, this.panelY + 188, true, () -> this.adjustArmor(false));
      this.armorRight = this.arrow(beRight, this.panelY + 188, false, () -> this.adjustArmor(true));
      this.addRenderableWidget(this.armorLeft);
      this.addRenderableWidget(this.armorRight);
      this.updateAdjusterVisibility();
      int seLeft = this.panelX + 192;
      int seRight = this.panelX + 326;
      this.addRenderableWidget(this.createArrowButton(seLeft, this.panelY + 128 + 2, true, btn -> this.cycleSecondaryType()));
      this.addRenderableWidget(this.createArrowButton(seRight, this.panelY + 128 + 2, false, btn -> this.cycleSecondaryType()));
      this.addRenderableWidget(this.createArrowButton(seLeft, this.panelY + 148 + 2, true, btn -> {
         this.creatorAffectedStat = this.prevEnum(this.creatorAffectedStat, KiAttackData.AffectedStat.values());
         this.recomputeDerivedValues();
      }));
      this.addRenderableWidget(this.createArrowButton(seRight, this.panelY + 148 + 2, false, btn -> {
         this.creatorAffectedStat = this.nextEnum(this.creatorAffectedStat, KiAttackData.AffectedStat.values());
         this.recomputeDerivedValues();
      }));
      this.addRenderableWidget(this.arrow(seLeft, this.panelY + 168, true, () -> this.adjustIntensity(false)));
      this.addRenderableWidget(this.arrow(seRight, this.panelY + 168, false, () -> this.adjustIntensity(true)));
      this.addRenderableWidget(this.arrow(seLeft, this.panelY + 188, true, () -> this.adjustDuration(false)));
      this.addRenderableWidget(this.arrow(seRight, this.panelY + 188, false, () -> this.adjustDuration(true)));
      int btnY = this.getUiHeight() - 28;
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(this.panelX + 172 - 78, btnY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.skills.create_skill", new Object[0]))
            .onPress(btn -> this.createSkill())
            .build()
      );
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(this.panelX + 172 + 4, btnY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.hair_editor.cancel", new Object[0]))
            .onPress(btn -> this.onClose())
            .build()
      );
      this.initColorPickerSliders();
      if (this.colorPickerVisible) {
         this.showColorPicker(this.colorTarget);
      }
   }

   private int pickerOriginX() {
      return this.panelX + 232;
   }

   private int pickerOriginY() {
      return this.panelY + 120;
   }

   private void initColorPickerSliders() {
      int sliderX = this.pickerOriginX();
      int sliderY = this.pickerOriginY();
      int sliderWidth = 90;
      this.hueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY)
         .size(sliderWidth, 10)
         .range(0, 360)
         .value(0)
         .message(this.tr("gui.dragonminez.customization.hue", new Object[0]))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.saturationSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 12)
         .size(sliderWidth, 10)
         .range(100, 0)
         .value(100)
         .message(this.tr("gui.dragonminez.customization.saturation", new Object[0]))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.valueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 24)
         .size(sliderWidth, 10)
         .range(100, 0)
         .value(100)
         .message(this.tr("gui.dragonminez.customization.value", new Object[0]))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.hexField = new EditBox(this.font, sliderX, sliderY + 36, sliderWidth, 12, this.tr("gui.dragonminez.common.hex", new Object[0]));
      this.hexField.setMaxLength(7);
      this.hexField.setResponder(this::onHexChanged);
      this.addRenderableWidget(this.hueSlider);
      this.addRenderableWidget(this.saturationSlider);
      this.addRenderableWidget(this.valueSlider);
      this.addRenderableWidget(this.hexField);
      this.setSlidersVisible();
   }

   private CustomTextureButton arrow(int x, int textY, boolean left, Runnable repeatable) {
      return this.createArrowButton(x, textY + 2, left, btn -> this.beginHold(repeatable));
   }

   private void beginHold(Runnable action) {
      this.heldAdjuster = action;
      this.holdTicks = 0;
      action.run();
   }

   public void tick() {
      super.tick();
      if (this.heldAdjuster != null) {
         this.holdTicks++;
         if (this.holdTicks >= 5 && (this.holdTicks - 5) % 2 == 0) {
            this.heldAdjuster.run();
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.heldAdjuster = null;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   private void adjustDamage(boolean inc) {
      float step = hasShiftDown() ? 0.25F : 0.05F;
      this.creatorDamage = Mth.clamp(
         this.creatorDamage + (inc ? step : -step), KiAttackData.getMinDamageForType(this.creatorType), KiAttackData.getMaxDamageForType(this.creatorType)
      );
      this.recomputeDerivedValues();
   }

   private void adjustSize(boolean inc) {
      if (KiAttackData.usesCustomSize(this.creatorType)) {
         float step = hasShiftDown() ? 5.0F : 0.5F;
         this.creatorSize = Mth.clamp(
            this.creatorSize + (inc ? step : -step), KiAttackData.getMinSizeForType(this.creatorType), KiAttackData.getMaxSizeForType(this.creatorType)
         );
         this.recomputeDerivedValues();
      }
   }

   private void adjustSpeed(boolean inc) {
      if (KiAttackData.usesCustomSpeed(this.creatorType)) {
         float step = hasShiftDown() ? 0.5F : 0.1F;
         this.creatorSpeed = Mth.clamp(
            this.creatorSpeed + (inc ? step : -step), KiAttackData.getMinSpeedForType(this.creatorType), KiAttackData.getMaxSpeedForType(this.creatorType)
         );
         this.recomputeDerivedValues();
      }
   }

   private void adjustArmor(boolean inc) {
      if (KiAttackData.usesCustomArmorPen(this.creatorType)) {
         int step = hasShiftDown() ? 5 : 1;
         this.creatorArmorPen = Mth.clamp(this.creatorArmorPen + (inc ? step : -step), 0, KiAttackData.getMaxArmorPenForType(this.creatorType));
         this.recomputeDerivedValues();
      }
   }

   private void adjustIntensity(boolean inc) {
      int step = hasShiftDown() ? 25 : 5;
      this.creatorSecondaryIntensity = Mth.clamp(this.creatorSecondaryIntensity + (inc ? step : -step), 5, 50);
      this.recomputeDerivedValues();
   }

   private void adjustDuration(boolean inc) {
      int step = hasShiftDown() ? 4 : 1;
      this.creatorSecondaryDuration = Mth.clamp(this.creatorSecondaryDuration + (inc ? step : -step), 1, 8);
      this.recomputeDerivedValues();
   }

   private CustomTextureButton createArrowButton(int x, int y, boolean left, OnPress onPress) {
      return new CustomTextureButton.Builder()
         .position(x, y - 4)
         .size(10, 15)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(left ? 32 : 20, 0, left ? 32 : 20, 14)
         .textureSize(8, 14)
         .message(Component.empty())
         .onPress(onPress)
         .build();
   }

   private CustomTextureButton createSwatchButton(int x, int y, String target) {
      return new CustomTextureButton.Builder()
         .position(x, y)
         .size(12, 12)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(42, 15, 42, 15)
         .textureSize(5, 5)
         .message(Component.empty())
         .onPress(btn -> this.showColorPicker(target))
         .build();
   }

   private <T extends Enum<T>> T nextEnum(T current, T[] values) {
      return values[(current.ordinal() + 1) % values.length];
   }

   private <T extends Enum<T>> T prevEnum(T current, T[] values) {
      return values[(current.ordinal() - 1 + values.length) % values.length];
   }

   private void toggleUtility() {
      if (this.allowsUtility(this.creatorType)) {
         this.creatorUtility = this.creatorUtility == KiAttackData.Utility.DAMAGE ? KiAttackData.Utility.HEAL : KiAttackData.Utility.DAMAGE;
         this.recomputeDerivedValues();
      }
   }

   private boolean allowsUtility(KiAttackData.KiType type) {
      return KiAttackData.allowsHealUtility(type);
   }

   private void updateAdjusterVisibility() {
      boolean useSize = KiAttackData.usesCustomSize(this.creatorType);
      if (this.sizeLeft != null) {
         this.sizeLeft.visible = useSize;
      }

      if (this.sizeRight != null) {
         this.sizeRight.visible = useSize;
      }

      boolean useSpeed = KiAttackData.usesCustomSpeed(this.creatorType);
      if (this.speedLeft != null) {
         this.speedLeft.visible = useSpeed;
      }

      if (this.speedRight != null) {
         this.speedRight.visible = useSpeed;
      }

      boolean useArmor = KiAttackData.usesCustomArmorPen(this.creatorType);
      if (this.armorLeft != null) {
         this.armorLeft.visible = useArmor;
      }

      if (this.armorRight != null) {
         this.armorRight.visible = useArmor;
      }
   }

   private void setCreatorType(KiAttackData.KiType newType) {
      this.creatorType = newType;
      this.creatorUtility = KiAttackData.Utility.DAMAGE;
      this.creatorDamage = KiAttackData.getDefaultDamageForType(this.creatorType);
      this.creatorSize = KiAttackData.getDefaultSizeForType(this.creatorType);
      this.creatorSpeed = KiAttackData.getDefaultSpeedForType(this.creatorType);
      this.creatorArmorPen = KiAttackData.getDefaultArmorPenForType(this.creatorType);
      this.updateUtilityArrowsVisibility();
      this.updateAdjusterVisibility();
      this.recomputeDerivedValues();
   }

   private void cycleSecondaryType() {
      KiAttackData.SecondaryEffectType valid = this.creatorUtility == KiAttackData.Utility.HEAL
         ? KiAttackData.SecondaryEffectType.BUFF
         : KiAttackData.SecondaryEffectType.DEBUFF;
      this.creatorSecondaryType = this.creatorSecondaryType == KiAttackData.SecondaryEffectType.NONE ? valid : KiAttackData.SecondaryEffectType.NONE;
      this.recomputeDerivedValues();
   }

   private void recomputeDerivedValues() {
      if (this.creatorSecondaryType != KiAttackData.SecondaryEffectType.NONE) {
         boolean ok = this.creatorSecondaryType == KiAttackData.SecondaryEffectType.BUFF && this.creatorUtility == KiAttackData.Utility.HEAL
            || this.creatorSecondaryType == KiAttackData.SecondaryEffectType.DEBUFF && this.creatorUtility == KiAttackData.Utility.DAMAGE;
         if (!ok) {
            this.creatorSecondaryType = KiAttackData.SecondaryEffectType.NONE;
         }
      }

      float[] normalized = KiAttackData.normalizeStatsForType(this.creatorType, this.creatorDamage, this.creatorSize, this.creatorSpeed, this.creatorArmorPen);
      this.creatorDamage = normalized[0];
      this.creatorSize = normalized[1];
      this.creatorSpeed = normalized[2];
      this.creatorArmorPen = Math.round(normalized[3]);
      KiAttackData preview = this.buildPreviewTechnique(normalized);
      preview.calculateDerivedValues();
      this.tpCost = (float)Math.max(0, Math.round(preview.getTpCost()));
      this.creatorCast = preview.getActualCastTime();
      this.creatorCooldown = preview.getCooldown();
      this.kiCost = this.computePreviewKiCost(preview);
   }

   private KiAttackData buildPreviewTechnique(float[] normalized) {
      KiAttackData ki = new KiAttackData();
      ki.setKiType(this.creatorType);
      ki.setUtility(this.allowsUtility(this.creatorType) ? this.creatorUtility : KiAttackData.Utility.DAMAGE);
      ki.setDamageMultiplier(normalized[0]);
      ki.setSize(normalized[1]);
      ki.setSpeed(normalized[2]);
      ki.setArmorPenetration(Math.round(normalized[3]));
      ki.setSecondaryEffectType(this.creatorSecondaryType);
      if (this.creatorSecondaryType != KiAttackData.SecondaryEffectType.NONE) {
         ki.setAffectedStat(this.creatorAffectedStat);
         ki.setSecondaryIntensity((float)this.creatorSecondaryIntensity);
         ki.setSecondaryDuration(this.creatorSecondaryDuration);
      }

      return ki;
   }

   private float computePreviewKiCost(KiAttackData preview) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return 0.0F;
      } else {
         float[] cost = new float[]{0.0F};
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> cost[0] = (float)preview.getCalculatedCost(data));
         return cost[0];
      }
   }

   private void updateUtilityArrowsVisibility() {
      boolean canUseUtility = this.allowsUtility(this.creatorType);
      if (this.utilityLeft != null) {
         this.utilityLeft.visible = canUseUtility;
         this.utilityLeft.active = canUseUtility;
      }

      if (this.utilityRight != null) {
         this.utilityRight.visible = canUseUtility;
         this.utilityRight.active = canUseUtility;
      }
   }

   private void applyAuraDefaults() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            String auraColor = data.getCharacter().getAuraColor();
            if (auraColor != null && !auraColor.isEmpty()) {
               int aura = ColorUtils.hexToInt(auraColor);
               this.creatorColorInterior = aura;
               this.creatorColorExterior = ColorUtils.darkenColor(aura, 0.75F);
            }
         });
         this.auraDefaultsApplied = true;
      }
   }

   private void showColorPicker(String target) {
      this.colorTarget = target;
      this.colorPickerVisible = true;
      int color = "interior".equals(target) ? this.creatorColorInterior : ("exterior".equals(target) ? this.creatorColorExterior : this.creatorColorOutline);
      float[] hsv = ColorUtils.rgbToHsv(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
      this.updatingFromHex = true;
      if (this.hueSlider != null) {
         this.hueSlider.setValue(Math.round(hsv[0]));
      }

      if (this.saturationSlider != null) {
         this.saturationSlider.setValue(Math.round(hsv[1]));
         this.saturationSlider.setCurrentHue(hsv[0]);
      }

      if (this.valueSlider != null) {
         this.valueSlider.setValue(Math.round(hsv[2]));
         this.valueSlider.setCurrentHue(hsv[0]);
         this.valueSlider.setCurrentSaturation(hsv[1] == 0.0F ? 100.0F : hsv[1]);
      }

      if (this.hexField != null) {
         this.hexField.setValue(ColorUtils.hsvToHex(hsv[0], hsv[1], hsv[2]));
      }

      this.updatingFromHex = false;
      this.setSlidersVisible();
   }

   private void hideColorPicker() {
      this.colorPickerVisible = false;
      this.setSlidersVisible();
   }

   private void setSlidersVisible() {
      if (this.hueSlider != null) {
         this.hueSlider.visible = this.colorPickerVisible;
      }

      if (this.saturationSlider != null) {
         this.saturationSlider.visible = this.colorPickerVisible;
      }

      if (this.valueSlider != null) {
         this.valueSlider.visible = this.colorPickerVisible;
      }

      if (this.hexField != null) {
         this.hexField.visible = this.colorPickerVisible;
      }
   }

   private void onHexChanged(String value) {
      if (!this.updatingFromHex && value != null) {
         String hex = value.startsWith("#") ? value : "#" + value;
         if (hex.length() == 7 && hex.substring(1).matches("[0-9a-fA-F]{6}")) {
            this.updatingFromHex = true;

            try {
               float[] hsv = ColorUtils.hexToHsv(hex);
               if (this.hueSlider != null) {
                  this.hueSlider.setValue(Math.round(hsv[0]));
               }

               if (this.saturationSlider != null) {
                  this.saturationSlider.setValue(Math.round(hsv[1]));
                  this.saturationSlider.setCurrentHue(hsv[0]);
               }

               if (this.valueSlider != null) {
                  this.valueSlider.setValue(Math.round(hsv[2]));
                  this.valueSlider.setCurrentHue(hsv[0]);
                  this.valueSlider.setCurrentSaturation(hsv[1]);
               }

               this.applyColor(hex);
            } catch (Exception var4) {
            }

            this.updatingFromHex = false;
         }
      }
   }

   private void updateColorFromSliders() {
      if (this.colorPickerVisible && this.hueSlider != null && this.saturationSlider != null && this.valueSlider != null) {
         float h = (float)this.hueSlider.getValue();
         float s = (float)this.saturationSlider.getValue();
         float v = (float)this.valueSlider.getValue();
         this.saturationSlider.setCurrentHue(h);
         this.valueSlider.setCurrentHue(h);
         this.valueSlider.setCurrentSaturation(s);
         String newColor = ColorUtils.hsvToHex(h, s, v);
         this.updatingFromHex = true;
         if (this.hexField != null && !this.hexField.isFocused()) {
            this.hexField.setValue(newColor);
         }

         this.updatingFromHex = false;
         this.applyColor(newColor);
      }
   }

   private void applyColor(String hex) {
      int color = ColorUtils.hexToInt(hex);
      if ("interior".equals(this.colorTarget)) {
         this.creatorColorInterior = color;
      } else if ("exterior".equals(this.colorTarget)) {
         this.creatorColorExterior = color;
      } else {
         this.creatorColorOutline = color;
      }
   }

   private void createSkill() {
      if (!this.allowsUtility(this.creatorType)) {
         this.creatorUtility = KiAttackData.Utility.DAMAGE;
      }

      float[] normalized = KiAttackData.normalizeStatsForType(this.creatorType, this.creatorDamage, this.creatorSize, this.creatorSpeed, this.creatorArmorPen);
      String defaultName = this.tr("gui.dragonminez.skills.new_skill", new Object[0]).getString();
      String finalName = this.creatorName == null ? defaultName : this.creatorName.trim();
      if (finalName.isEmpty()) {
         finalName = defaultName;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String generatedId = TechniqueData.generateId(mc.player.getName().getString(), finalName);
         StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, mc.player).orElse(null);
         boolean duplicate = stats != null && stats.getTechniques().getUnlockedTechniques().containsKey(generatedId);
         if (duplicate) {
            mc.player.displayClientMessage(this.tr("gui.dragonminez.skills.creator.duplicate", new Object[]{finalName}), true);
            return;
         }

         if (stats != null && stats.getResources().getTrainingPoints() < this.tpCost) {
            mc.player
               .displayClientMessage(
                  Component.literal(
                     "Not enough TP: this technique costs "
                        + Math.round(this.tpCost)
                        + " TP (you have "
                        + Math.round(stats.getResources().getTrainingPoints())
                        + ")."
                  ),
                  true
               );
            return;
         }
      }

      NetworkHandler.INSTANCE
         .sendToServer(
            new CreateTechniqueC2S(
               finalName,
               this.creatorType.name(),
               this.creatorUtility.name(),
               normalized[0],
               normalized[2],
               normalized[1],
               Math.round(normalized[3]),
               this.creatorCast,
               this.creatorCooldown,
               this.creatorColorInterior,
               this.creatorColorExterior,
               this.creatorColorOutline,
               this.creatorSecondaryType.name(),
               this.creatorSecondaryType == KiAttackData.SecondaryEffectType.NONE ? "" : this.creatorAffectedStat.name(),
               (float)this.creatorSecondaryIntensity,
               this.creatorSecondaryDuration
            )
         );
      this.onClose();
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      graphics.blit(MENU_NPC, this.panelX, this.panelY, 0.0F, 0.0F, 345, 273, 512, 512);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.skills.creator.title", new Object[0]), this.panelX + 172, this.panelY + 12, -10496
      );
      this.renderHeader(graphics);
      this.renderBaseEffects(graphics);
      this.renderSecondaryEffects(graphics);
      if (this.colorPickerVisible) {
         this.renderColorPickerBackground(graphics);
      }

      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 400.0);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      graphics.pose().popPose();
      if (!this.colorPickerVisible) {
         this.renderEffectTooltip(graphics, uiMouseX, uiMouseY);
      }

      this.endUiScale(graphics);
   }

   private void renderHeader(GuiGraphics graphics) {
      int r1 = this.panelY + 32;
      int r2 = this.panelY + 46;
      int r3 = this.panelY + 58;
      int r4 = this.panelY + 70;
      int leftCx = this.panelX + 72;
      int centerCx = this.panelX + 172;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.skills.creator.type", new Object[0])
            .append(": ")
            .append(this.tr("technique.type." + this.creatorType.name().toLowerCase(Locale.ROOT), new Object[0])),
         leftCx,
         r1,
         -1
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.skills.creator.utility", new Object[0])
            .append(": ")
            .append(this.tr("technique.utility." + this.creatorUtility.name().toLowerCase(Locale.ROOT), new Object[0])),
         leftCx,
         r2,
         this.allowsUtility(this.creatorType) ? -1 : -8947849
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.skills.creator.ki_cost_label", new Object[0]).append(" ").append(this.txt(COST_NUMBER_FORMAT.format((double)this.kiCost))),
         centerCx,
         r2,
         -2236963
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.cooldown", new Object[0]).append(": ").append(this.txt(String.valueOf(this.creatorCooldown))),
         centerCx,
         r3,
         -2236963
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.skills.creator.tp_cost_label", new Object[0]).append(" ").append(this.txt(COST_NUMBER_FORMAT.format((double)this.tpCost))),
         centerCx,
         r4,
         -2236963
      );
      this.drawColorRow(graphics, this.tr("gui.dragonminez.skills.creator.color.interior", new Object[0]), this.creatorColorInterior, r1);
      this.drawColorRow(graphics, this.tr("gui.dragonminez.skills.creator.color.exterior", new Object[0]), this.creatorColorExterior, r2);
      this.drawColorRow(graphics, this.tr("gui.dragonminez.skills.creator.color.outline", new Object[0]), this.creatorColorOutline, r3);
   }

   private void drawColorRow(GuiGraphics graphics, Component label, int color, int rowY) {
      TextUtil.drawStringWithBorder(graphics, this.font, label, this.panelX + 234, rowY, -3355444);
      int sx = this.panelX + 300;
      int sy = rowY - 2;
      graphics.fill(sx - 1, sy - 1, sx + 13, sy + 13, -16777216);
      graphics.fill(sx, sy, sx + 12, sy + 12, 0xFF000000 | color);
   }

   private void renderBaseEffects(GuiGraphics graphics) {
      int cx = this.panelX + 84;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.technique.base_effects", new Object[0]), cx, this.panelY + 108, -10496
      );
      int damagePercent = Math.round(this.creatorDamage * 100.0F);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.damage", new Object[0]).append(": ").append(this.txt(damagePercent + "%")),
         cx,
         this.panelY + 128,
         -1
      );
      int sizeColor = KiAttackData.usesCustomSize(this.creatorType) ? -1 : -8947849;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.size", new Object[0]).append(": ").append(this.txt(String.format(Locale.US, "%.1f", this.creatorSize))),
         cx,
         this.panelY + 148,
         sizeColor
      );
      int speedColor = KiAttackData.usesCustomSpeed(this.creatorType) ? -1 : -8947849;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.speed", new Object[0]).append(": ").append(this.txt(String.format(Locale.US, "%.1f", this.creatorSpeed))),
         cx,
         this.panelY + 168,
         speedColor
      );
      int armorColor = KiAttackData.usesCustomArmorPen(this.creatorType) ? -1 : -8947849;
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.armor_pen", new Object[0]).append(": ").append(this.txt(this.creatorArmorPen + "%")),
         cx,
         this.panelY + 188,
         armorColor
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.cast_time", new Object[0]).append(": ").append(this.txt(String.valueOf(this.creatorCast / 20)).append("s")),
         cx,
         this.panelY + 208,
         -5583617
      );
   }

   private void renderSecondaryEffects(GuiGraphics graphics) {
      int cx = this.panelX + 259;
      boolean hasSec = this.creatorSecondaryType != KiAttackData.SecondaryEffectType.NONE;
      int active = -1;
      int inactive = -8947849;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.technique.secondary_effects", new Object[0]), cx, this.panelY + 108, -10496
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.effect_type", new Object[0])
            .append(": ")
            .append(this.tr("gui.dragonminez.technique.effect_type." + this.creatorSecondaryType.name().toLowerCase(Locale.ROOT), new Object[0])),
         cx,
         this.panelY + 128,
         active
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.affected_stat", new Object[0])
            .append(": ")
            .append(this.tr("gui.dragonminez.technique.affected_stat." + this.creatorAffectedStat.name().toLowerCase(Locale.ROOT), new Object[0])),
         cx,
         this.panelY + 148,
         hasSec ? active : inactive
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.intensity", new Object[0]).append(": ").append(this.txt(this.creatorSecondaryIntensity + "%")),
         cx,
         this.panelY + 168,
         hasSec ? active : inactive
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.technique.duration", new Object[0]).append(": ").append(this.txt(this.creatorSecondaryDuration + "s")),
         cx,
         this.panelY + 188,
         hasSec ? active : inactive
      );
   }

   private void renderEffectTooltip(GuiGraphics graphics, int uiMouseX, int uiMouseY) {
      int rowY = this.panelY + 128;
      boolean hovering = uiMouseX >= this.panelX + 30 && uiMouseX <= this.panelX + 150 && uiMouseY >= rowY - 2 && uiMouseY <= rowY + 9;
      if (hovering) {
         boolean heal = this.creatorUtility == KiAttackData.Utility.HEAL && this.allowsUtility(this.creatorType);
         String valueKey = heal ? "gui.dragonminez.technique.effect.tooltip.heal" : "gui.dragonminez.technique.effect.tooltip.damage";
         List<Component> desc = new ArrayList<>();
         desc.add(this.tr(valueKey, new Object[]{this.getDamageHealingExpression()}));
         desc.add(this.tr("gui.dragonminez.technique.effect.tooltip.desc", new Object[0]));
         TextUtil.renderAdvancedTooltip(
            graphics,
            this.font,
            uiMouseX,
            uiMouseY,
            this.getUiWidth(),
            this.getUiHeight(),
            this.tr("gui.dragonminez.technique.damage", new Object[0]),
            desc,
            null,
            -10496
         );
      }
   }

   private void renderColorPickerBackground(GuiGraphics graphics) {
      PoseStack poseStack = graphics.pose();
      poseStack.pushPose();
      poseStack.translate(0.0, 0.0, 200.0);
      int sliderX = this.pickerOriginX();
      int sliderY = this.pickerOriginY();
      graphics.fill(sliderX - 5, sliderY - 5, sliderX + 95, sliderY + 56, -872415232);
      poseStack.popPose();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         if (!this.colorPickerVisible) {
            return false;
         } else {
            int sliderX = this.pickerOriginX();
            int sliderY = this.pickerOriginY();
            int pickerW = 90;
            int pickerH = 56;
            boolean insidePicker = uiMouseX >= (double)(sliderX - 5)
               && uiMouseX <= (double)(sliderX + pickerW + 5)
               && uiMouseY >= (double)(sliderY - 5)
               && uiMouseY <= (double)(sliderY + pickerH + 5);
            int sx = this.panelX + 300;
            boolean insideColorButtons = uiMouseX >= (double)sx
                  && uiMouseX <= (double)(sx + 12)
                  && uiMouseY >= (double)(this.panelY + 30)
                  && uiMouseY <= (double)(this.panelY + 42)
               || uiMouseX >= (double)sx && uiMouseX <= (double)(sx + 12) && uiMouseY >= (double)(this.panelY + 44) && uiMouseY <= (double)(this.panelY + 56)
               || uiMouseX >= (double)sx && uiMouseX <= (double)(sx + 12) && uiMouseY >= (double)(this.panelY + 56) && uiMouseY <= (double)(this.panelY + 68);
            if (!insidePicker && !insideColorButtons) {
               this.hideColorPicker();
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private String getDamageHealingExpression() {
      double baseKiDamage = 0.0;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         double[] value = new double[]{0.0};
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> value[0] = data.getKiDamage());
         baseKiDamage = value[0];
      }

      double configDamage = Math.max(0.0, ConfigManager.getTechniqueConfig().getKiTypeConfig(this.creatorType).getDamageMultiplier());
      boolean heal = this.creatorUtility == KiAttackData.Utility.HEAL && this.allowsUtility(this.creatorType);
      double output = heal ? 0.4F : 1.0;
      return String.format(Locale.US, "%.1f", baseKiDamage * (double)this.creatorDamage * configDamage * output);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void onClose() {
      this.hideColorPicker();
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
