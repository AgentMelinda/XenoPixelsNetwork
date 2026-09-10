package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.AxisSlider;
import com.dragonminez.client.gui.buttons.ColorSlider;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.SwitchButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.hair.HairRenderer;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.hair.HairStrand;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.StatsSyncC2S;
import com.dragonminez.common.network.C2S.UpdateCustomHairC2S;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class HairEditorScreen extends ScaledScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation STAT_BUTTONS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final ResourceLocation PANORAMA_SAIYAN = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/roshi");
   private static final Set<String> DEV_NAMES = Set.of("Dev", "ImYuseix", "ezShokkoh", "narukebaransu");
   private final PanoramaRenderer panorama = new PanoramaRenderer(new CubeMap(PANORAMA_SAIYAN));
   private final Screen previousScreen;
   private final Character character;
   private HairEditorScreen.Tab currentTab = HairEditorScreen.Tab.OVERVIEW;
   private final CustomHair[] workingHairs = new CustomHair[4];
   private final CustomHair[] backupHairs = new CustomHair[4];
   private int selectedStyle = 0;
   private CustomHair.HairFace currentFace = CustomHair.HairFace.FRONT;
   private int selectedStrandIndex = 0;
   private boolean physicsEnabled = true;
   private boolean mirrorEnabled = false;
   private boolean hairBase = true;
   private float playerRotation = 180.0F;
   private float playerPitch = 0.0F;
   private boolean isDraggingModel = false;
   private double lastMouseX = 0.0;
   private double lastMouseY = 0.0;
   private float targetZoom = 150.0F;
   private float currentZoom = 150.0F;
   private EditBox fullCodeBox;
   private EditBox individualCodeBox;
   private AxisSlider lengthSlider;
   private AxisSlider widthSlider;
   private AxisSlider xAxisSlider;
   private AxisSlider zAxisSlider;
   private AxisSlider xBendSlider;
   private AxisSlider zBendSlider;
   private boolean colorPickerVisible = false;
   private ColorSlider hueSlider;
   private ColorSlider saturationSlider;
   private ColorSlider valueSlider;
   private EditBox hexColorField;
   private TexturedTextButton colorButton;
   private boolean isUpdatingFromCode = false;
   private Component actionStatusText = Component.empty();
   private int actionStatusTimer = 0;
   private int actionStatusColor = 16777215;

   public HairEditorScreen(Screen previousScreen, Character character) {
      super(Component.translatable("gui.dragonminez.hair_editor.title").withStyle(Style.EMPTY.withFont(DMZ_FONT)));
      this.previousScreen = previousScreen;
      this.character = character;
      if (character.getHairId() > 0) {
         int id = character.getHairId();
         String color = character.getHairColor();
         character.setHairBase(HairManager.getPresetHair(id, color).copy());
         character.setHairSSJ(HairManager.getPresetHairSSJ(id, color).copy());
         character.setHairSSJ2(HairManager.getPresetHairSSJ2(id, color).copy());
         character.setHairSSJ3(HairManager.getPresetHairSSJ3(id, color).copy());
         character.setHairId(0);
         this.hairBase = character.isRenderHairBase();
      }

      this.workingHairs[0] = character.getHairBase() != null ? character.getHairBase().copy() : new CustomHair();
      this.workingHairs[1] = character.getHairSSJ() != null ? character.getHairSSJ().copy() : this.workingHairs[0].copy();
      this.workingHairs[2] = character.getHairSSJ2() != null ? character.getHairSSJ2().copy() : this.workingHairs[1].copy();
      this.workingHairs[3] = character.getHairSSJ3() != null ? character.getHairSSJ3().copy() : this.workingHairs[2].copy();

      for (int i = 0; i < 4; i++) {
         this.backupHairs[i] = this.workingHairs[i].copy();
      }

      HairRenderer.EDITING_STRAND_ID = -1;
   }

   protected void init() {
      super.init();
      this.clearWidgets();
      this.initGlobalUI();
      switch (this.currentTab) {
         case OVERVIEW:
            this.initOverviewTab();
            break;
         case STYLE:
            this.initStyleTab();
            break;
         case STRAND:
            this.initStrandTab();
      }
   }

   public void tick() {
      super.tick();
      if (this.actionStatusTimer > 0) {
         this.actionStatusTimer--;
      }
   }

   private void initGlobalUI() {
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(12, 12)
            .size(74, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(
               this.currentTab == HairEditorScreen.Tab.OVERVIEW ? this.tr("gui.dragonminez.hair_editor.cancel") : this.tr("gui.dragonminez.customization.back")
            )
            .onPress(btn -> this.navigateBack())
            .build()
      );
      int rightEdge = this.getUiWidth() - 12;
      this.addRenderableWidget(new SwitchButton(rightEdge - 30, 15, this.mirrorEnabled, Component.empty(), btn -> {
         this.mirrorEnabled = !this.mirrorEnabled;
         ((SwitchButton)btn).toggle();
         Minecraft.getInstance().player.playSound(this.mirrorEnabled ? (SoundEvent)MainSounds.SWITCH_ON.get() : (SoundEvent)MainSounds.SWITCH_OFF.get());
      }));
      this.addRenderableWidget(new SwitchButton(rightEdge - 90, 15, this.physicsEnabled, Component.empty(), btn -> {
         this.physicsEnabled = !this.physicsEnabled;
         ((SwitchButton)btn).toggle();
         Minecraft.getInstance().player.playSound(this.physicsEnabled ? (SoundEvent)MainSounds.SWITCH_ON.get() : (SoundEvent)MainSounds.SWITCH_OFF.get());
      }));
      this.addRenderableWidget(new SwitchButton(rightEdge - 170, 15, this.hairBase, Component.empty(), btn -> {
         this.hairBase = !this.hairBase;
         ((SwitchButton)btn).toggle();
         this.character.setRenderHairBase(this.hairBase);
         NetworkHandler.sendToServer(new StatsSyncC2S(this.character));
         Minecraft.getInstance().player.playSound(this.hairBase ? (SoundEvent)MainSounds.SWITCH_ON.get() : (SoundEvent)MainSounds.SWITCH_OFF.get());
      }));
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(rightEdge - 156, this.getUiHeight() - 32)
            .size(74, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.hair_editor.save"))
            .onPress(btn -> this.saveChanges())
            .build()
      );
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(rightEdge - 74, this.getUiHeight() - 32)
            .size(74, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.hair_editor.hair_salon"))
            .onPress(btn -> this.openHairSalon())
            .build()
      );
   }

   private void initOverviewTab() {
      HairRenderer.EDITING_STRAND_ID = -1;
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      String[] styleNames = new String[]{
         "gui.dragonminez.hair_editor.style.0",
         "gui.dragonminez.hair_editor.style.1",
         "gui.dragonminez.hair_editor.style.2",
         "gui.dragonminez.hair_editor.style.3"
      };

      for (int i = 0; i < 4; i++) {
         int finalI = i;
         int yOffset = panelY + 40 + i * 25;
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(leftPanelX + 15, yOffset)
               .size(74, 20)
               .texture(STAT_BUTTONS)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr(styleNames[i]))
               .onPress(btn -> {
                  this.selectedStyle = finalI;
                  this.currentTab = HairEditorScreen.Tab.STYLE;
                  this.rebuildWidgets();
               })
               .build()
         );
         this.addRenderableWidget(
            new CustomTextureButton.Builder()
               .position(leftPanelX + 105, yOffset + 4)
               .size(14, 11)
               .texture(STAT_BUTTONS)
               .textureCoords(10, 0, 10, 10)
               .textureSize(10, 10)
               .onPress(btn -> {
                  this.workingHairs[finalI].clear();
                  this.updateFullCodeBox();
               })
               .build()
         );
      }

      this.fullCodeBox = new EditBox(this.font, leftPanelX + 15, panelY + 150, 110, 16, this.txt(""));
      this.fullCodeBox.setMaxLength(65536);
      this.addRenderableWidget(this.fullCodeBox);
      this.updateFullCodeBox();
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(leftPanelX + 45, panelY + 170)
            .size(20, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(182, 0, 182, 20)
            .textureSize(20, 20)
            .message(Component.empty())
            .onPress(btn -> {
               this.fillEmptyStyles(this.workingHairs);
               this.updateFullCodeBox();
               Minecraft.getInstance().keyboardHandler.setClipboard(this.fullCodeBox.getValue());
               this.actionStatusText = this.tr("gui.dragonminez.hair_editor.status.copied");
               this.actionStatusTimer = 60;
               this.actionStatusColor = 5635925;
            })
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(leftPanelX + 75, panelY + 170)
            .size(20, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(162, 0, 162, 20)
            .textureSize(20, 20)
            .message(Component.empty())
            .onPress(btn -> {
               String code = this.fullCodeBox.getValue();
               if (HairManager.isFullSetCode(code)) {
                  CustomHair[] set = HairManager.fromFullSetCode(code);
                  if (set != null) {
                     System.arraycopy(set, 0, this.workingHairs, 0, 4);
                     this.syncHairToServer();
                     this.actionStatusText = this.tr("gui.dragonminez.hair_editor.status.imported");
                     this.actionStatusTimer = 60;
                     this.actionStatusColor = 5635925;
                  }
               } else {
                  this.actionStatusText = this.tr("gui.dragonminez.hair_editor.status.invalid");
                  this.actionStatusTimer = 60;
                  this.actionStatusColor = 16733525;
               }
            })
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(leftPanelX + 127, panelY + 153)
            .size(14, 11)
            .texture(STAT_BUTTONS)
            .textureCoords(10, 0, 10, 10)
            .textureSize(10, 10)
            .onPress(btn -> {
               if (this.fullCodeBox != null) {
                  this.fullCodeBox.setValue("");
               }
            })
            .build()
      );
   }

   private void initStyleTab() {
      HairRenderer.EDITING_STRAND_ID = -1;
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 82;
      this.individualCodeBox = new EditBox(this.font, leftPanelX + 15, panelY + 150, 110, 16, this.txt(""));
      this.individualCodeBox.setMaxLength(65536);
      this.addRenderableWidget(this.individualCodeBox);
      this.updateIndividualCodeBox();
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(leftPanelX + 45, panelY + 175)
            .size(20, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(182, 0, 182, 20)
            .textureSize(20, 20)
            .message(Component.empty())
            .onPress(btn -> Minecraft.getInstance().keyboardHandler.setClipboard(this.individualCodeBox.getValue()))
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(leftPanelX + 75, panelY + 175)
            .size(20, 20)
            .texture(STAT_BUTTONS)
            .textureCoords(162, 0, 162, 20)
            .textureSize(20, 20)
            .message(Component.empty())
            .onPress(btn -> {
               String code = this.individualCodeBox.getValue().trim();
               CustomHair imported;
               if (HairManager.isFullSetCode(code)) {
                  CustomHair[] set = HairManager.fromFullSetCode(code);
                  imported = set != null && this.selectedStyle < set.length ? set[this.selectedStyle] : null;
               } else {
                  imported = HairManager.fromCode(code);
               }

               if (imported != null) {
                  this.workingHairs[this.selectedStyle] = imported;
                  this.syncHairToServer();
                  this.actionStatusText = this.tr("gui.dragonminez.hair_editor.status.imported");
                  this.actionStatusTimer = 60;
                  this.actionStatusColor = 5635925;
                  this.rebuildWidgets();
               } else {
                  this.actionStatusText = this.tr("gui.dragonminez.hair_editor.status.invalid");
                  this.actionStatusTimer = 60;
                  this.actionStatusColor = 16733525;
               }
            })
            .build()
      );
   }

   private void initStrandTab() {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      HairStrand strand = this.getSelectedStrand();
      if (strand != null) {
         HairRenderer.EDITING_STRAND_ID = strand.getId();
      }

      int startY = panelY + 44;
      int sliderX = leftPanelX + 16;
      int sliderWidth = 85;
      boolean isDev = DEV_NAMES.contains(this.minecraft.getUser().getName());
      boolean isSSJ3 = this.selectedStyle == 3;
      int maxCubes = isDev ? 10 : (isSSJ3 ? 8 : 4);
      float maxWidth = isDev ? 3.0F : 1.5F;
      float curLenMap = strand != null ? (float)strand.getLength() + (strand.getLengthScale() - 1.0F) * 2.0F : 0.0F;
      this.lengthSlider = new AxisSlider.Builder()
         .position(sliderX, startY)
         .size(sliderWidth, 11)
         .range(0.0F, (float)(maxCubes + 1))
         .value(curLenMap)
         .step(0.5F)
         .axis(AxisSlider.Axis.Y)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               int len = Math.min(maxCubes, (int)Math.floor((double)val.floatValue()));
               float scale = val > (float)maxCubes ? 1.0F + (val - (float)maxCubes) * 0.5F : 1.0F;
               s.setLength(len);
               s.setLengthScale(scale);
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.widthSlider = new AxisSlider.Builder()
         .position(sliderX, startY + 25)
         .size(sliderWidth, 11)
         .range(0.5F, maxWidth)
         .value(strand != null ? strand.getScaleX() : 1.0F)
         .step(0.1F)
         .axis(AxisSlider.Axis.Y)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               s.setScale(val, s.getScaleY(), val);
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.xAxisSlider = new AxisSlider.Builder()
         .position(sliderX, startY + 50)
         .size(sliderWidth, 11)
         .range(-180.0F, 180.0F)
         .value(strand != null ? strand.getRotationX() : 0.0F)
         .step(1.0F)
         .axis(AxisSlider.Axis.X)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               s.setRotation(val, s.getRotationY(), s.getRotationZ());
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.zAxisSlider = new AxisSlider.Builder()
         .position(sliderX, startY + 75)
         .size(sliderWidth, 11)
         .range(-180.0F, 180.0F)
         .value(strand != null ? strand.getRotationZ() : 0.0F)
         .step(1.0F)
         .axis(AxisSlider.Axis.Z)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               s.setRotation(s.getRotationX(), s.getRotationY(), val);
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.xBendSlider = new AxisSlider.Builder()
         .position(sliderX, startY + 100)
         .size(sliderWidth, 11)
         .range(-180.0F, 180.0F)
         .value(strand != null ? strand.getCurveX() : 0.0F)
         .step(1.0F)
         .axis(AxisSlider.Axis.X)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               s.setCurve(val, s.getCurveY(), s.getCurveZ());
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.zBendSlider = new AxisSlider.Builder()
         .position(sliderX, startY + 125)
         .size(sliderWidth, 11)
         .range(-180.0F, 180.0F)
         .value(strand != null ? strand.getCurveZ() : 0.0F)
         .step(1.0F)
         .axis(AxisSlider.Axis.Z)
         .onValueChange(val -> {
            HairStrand s = this.getSelectedStrand();
            if (s != null) {
               s.setCurve(s.getCurveX(), s.getCurveY(), val);
               this.applyMirror();
               this.syncHairToServer();
            }
         })
         .build();
      this.addRenderableWidget(this.lengthSlider);
      this.addRenderableWidget(this.widthSlider);
      this.addRenderableWidget(this.xAxisSlider);
      this.addRenderableWidget(this.zAxisSlider);
      this.addRenderableWidget(this.xBendSlider);
      this.addRenderableWidget(this.zBendSlider);
      String currentColor = this.getCurrentStrandColor();
      this.colorButton = new TexturedTextButton.Builder()
         .position(sliderX + 30, startY + 140)
         .size(20, 20)
         .texture(STAT_BUTTONS)
         .textureCoords(42, 15, 42, 15)
         .textureSize(5, 5)
         .message(Component.empty())
         .backgroundColor(ColorUtils.hexToInt(currentColor))
         .onPress(btn -> this.toggleColorPicker())
         .build();
      this.addRenderableWidget(this.colorButton);
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(sliderX + 60, startY + 145)
            .size(14, 11)
            .texture(STAT_BUTTONS)
            .textureCoords(10, 0, 10, 10)
            .textureSize(10, 10)
            .onPress(btn -> {
               HairStrand s = this.getSelectedStrand();
               if (s != null) {
                  s.setColor(null);
                  this.applyMirrorColor(null);
                  this.colorButton.setBackgroundColor(ColorUtils.hexToInt(this.getCurrentStrandColor()));
                  this.syncHairToServer();
               }
            })
            .build()
      );
      this.initColorPicker();
   }

   private HairStrand getMirrorTarget() {
      int col = this.selectedStrandIndex % this.currentFace.cols;
      int row = this.selectedStrandIndex / this.currentFace.cols;
      int mirrorCol = this.currentFace.cols - 1 - col;
      CustomHair.HairFace mirrorFace;
      if (this.currentFace == CustomHair.HairFace.LEFT) {
         mirrorFace = CustomHair.HairFace.RIGHT;
      } else if (this.currentFace == CustomHair.HairFace.RIGHT) {
         mirrorFace = CustomHair.HairFace.LEFT;
      } else {
         if (mirrorCol == col) {
            return null;
         }

         mirrorFace = this.currentFace;
      }

      int mirrorIndex = row * this.currentFace.cols + mirrorCol;
      return this.workingHairs[this.selectedStyle].getStrand(mirrorFace, mirrorIndex);
   }

   private void applyMirror() {
      if (this.mirrorEnabled) {
         HairStrand source = this.getSelectedStrand();
         if (source != null) {
            HairStrand target = this.getMirrorTarget();
            if (target != null && target != source) {
               target.setLength(source.getLength());
               target.setLengthScale(source.getLengthScale());
               target.setScale(source.getScaleX(), source.getScaleY(), source.getScaleZ());
               target.setRotation(source.getRotationX(), source.getRotationY(), -source.getRotationZ());
               target.setCurve(source.getCurveX(), source.getCurveY(), -source.getCurveZ());
               target.setColor(source.getColor());
            }
         }
      }
   }

   private void applyMirrorColor(String color) {
      if (this.mirrorEnabled) {
         HairStrand target = this.getMirrorTarget();
         if (target != null && target != this.getSelectedStrand()) {
            target.setColor(color);
         }
      }
   }

   private void initColorPicker() {
      int leftPanelX = 12;
      int sliderX = leftPanelX + 150;
      int sliderY = this.getUiHeight() / 2 - 40;
      this.hueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY)
         .size(100, 10)
         .range(0, 360)
         .value(0)
         .message(this.txt("H"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.saturationSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 12)
         .size(100, 10)
         .range(100, 0)
         .value(100)
         .message(this.txt("S"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.valueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 24)
         .size(100, 10)
         .range(100, 0)
         .value(100)
         .message(this.txt("V"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.hexColorField = new EditBox(this.font, sliderX, sliderY + 36, 100, 12, this.txt("Hex"));
      this.hexColorField.setMaxLength(7);
      this.hexColorField.setResponder(hex -> {
         if (!this.isUpdatingFromCode) {
            if (hex.startsWith("#")) {
               hex = hex.substring(1);
            }

            if (hex.length() == 6) {
               this.isUpdatingFromCode = true;

               try {
                  float[] hsv = ColorUtils.hexToHsv("#" + hex);
                  this.hueSlider.setValue((int)hsv[0]);
                  this.saturationSlider.setValue(hsv[1] == 0.0F ? 100 : (int)hsv[1]);
                  this.valueSlider.setValue(hsv[2] == 0.0F ? 100 : (int)hsv[2]);
                  this.saturationSlider.setCurrentHue(hsv[0]);
                  this.valueSlider.setCurrentHue(hsv[0]);
                  this.valueSlider.setCurrentSaturation(hsv[1] == 0.0F ? 100.0F : hsv[1]);
                  this.applyColorToStrand("#" + hex);
               } catch (Exception var3x) {
               }

               this.isUpdatingFromCode = false;
            }
         }
      });
      this.addRenderableWidget(this.hueSlider);
      this.addRenderableWidget(this.saturationSlider);
      this.addRenderableWidget(this.valueSlider);
      this.addRenderableWidget(this.hexColorField);
      this.setSlidersVisible(false);
   }

   private void toggleColorPicker() {
      this.colorPickerVisible = !this.colorPickerVisible;
      if (this.colorPickerVisible) {
         String currentColor = this.getCurrentStrandColor();
         float[] hsv = ColorUtils.hexToHsv(currentColor);
         this.hueSlider.setValue((int)hsv[0]);
         this.saturationSlider.setValue(hsv[1] == 0.0F ? 100 : (int)hsv[1]);
         this.valueSlider.setValue(hsv[2] == 0.0F ? 100 : (int)hsv[2]);
         this.saturationSlider.setCurrentHue(hsv[0]);
         this.valueSlider.setCurrentHue(hsv[0]);
         this.valueSlider.setCurrentSaturation(hsv[1] == 0.0F ? 100.0F : hsv[1]);
         this.isUpdatingFromCode = true;
         this.hexColorField.setValue(currentColor);
         this.isUpdatingFromCode = false;
      }

      this.setSlidersVisible(this.colorPickerVisible);
   }

   private void updateColorFromSliders() {
      if (this.colorPickerVisible) {
         float h = (float)this.hueSlider.getValue();
         float s = (float)this.saturationSlider.getValue();
         float v = (float)this.valueSlider.getValue();
         this.saturationSlider.setCurrentHue(h);
         this.valueSlider.setCurrentHue(h);
         this.valueSlider.setCurrentSaturation(s);
         String newColor = ColorUtils.hsvToHex(h, s, v);
         this.isUpdatingFromCode = true;
         if (!this.hexColorField.isFocused()) {
            this.hexColorField.setValue(newColor);
         }

         this.isUpdatingFromCode = false;
         this.applyColorToStrand(newColor);
      }
   }

   private void applyColorToStrand(String color) {
      HairStrand strand = this.getSelectedStrand();
      if (strand != null) {
         strand.setColor(color);
         this.colorButton.setBackgroundColor(ColorUtils.hexToInt(color));
         this.applyMirrorColor(color);
         this.syncHairToServer();
      }
   }

   private void setSlidersVisible(boolean visible) {
      if (this.hueSlider != null) {
         this.hueSlider.visible = visible;
      }

      if (this.saturationSlider != null) {
         this.saturationSlider.visible = visible;
      }

      if (this.valueSlider != null) {
         this.valueSlider.visible = visible;
      }

      if (this.hexColorField != null) {
         this.hexColorField.visible = visible;
      }
   }

   private HairStrand getSelectedStrand() {
      return this.workingHairs[this.selectedStyle].getStrand(this.currentFace, this.selectedStrandIndex);
   }

   private String getCurrentStrandColor() {
      HairStrand strand = this.getSelectedStrand();
      return strand != null && strand.hasCustomColor() ? strand.getColor() : this.workingHairs[this.selectedStyle].getGlobalColor();
   }

   private void fillEmptyStyles(CustomHair[] set) {
      if (set[0] == null || set[0].isEmpty()) {
         set[0] = new CustomHair();
      }

      if (set[1] == null || set[1].isEmpty()) {
         set[1] = set[0].copy();
      }

      if (set[2] == null || set[2].isEmpty()) {
         set[2] = set[1].copy();
      }

      if (set[3] == null || set[3].isEmpty()) {
         set[3] = set[2].copy();
      }
   }

   private void updateFullCodeBox() {
      if (this.fullCodeBox != null) {
         CustomHair[] temp = new CustomHair[4];

         for (int i = 0; i < 4; i++) {
            temp[i] = this.workingHairs[i].copy();
         }

         this.fillEmptyStyles(temp);
         this.fullCodeBox.setValue(HairManager.toFullSetCode(temp[0], temp[1], temp[2], temp[3]));
      }
   }

   private void updateIndividualCodeBox() {
      if (this.individualCodeBox != null) {
         this.individualCodeBox.setValue(HairManager.toCode(this.workingHairs[this.selectedStyle]));
      }
   }

   private void syncHairToServer() {
      this.character.setHairId(0);

      for (int i = 0; i < 4; i++) {
         switch (i) {
            case 0:
               this.character.setHairBase(this.workingHairs[i]);
               break;
            case 1:
               this.character.setHairSSJ(this.workingHairs[i]);
               break;
            case 2:
               this.character.setHairSSJ2(this.workingHairs[i]);
               break;
            case 3:
               this.character.setHairSSJ3(this.workingHairs[i]);
         }

         NetworkHandler.sendToServer(new UpdateCustomHairC2S(i, this.workingHairs[i]));
      }
   }

   private void navigateBack() {
      if (this.currentTab == HairEditorScreen.Tab.STRAND) {
         this.currentTab = HairEditorScreen.Tab.STYLE;
         this.colorPickerVisible = false;
         HairRenderer.EDITING_STRAND_ID = -1;
         this.rebuildWidgets();
      } else if (this.currentTab == HairEditorScreen.Tab.STYLE) {
         this.currentTab = HairEditorScreen.Tab.OVERVIEW;
         this.rebuildWidgets();
      } else {
         for (int i = 0; i < 4; i++) {
            this.workingHairs[i] = this.backupHairs[i].copy();
         }

         this.syncHairToServer();
         Minecraft.getInstance().setScreen(this.previousScreen);
      }
   }

   private void saveChanges() {
      if (this.currentTab != HairEditorScreen.Tab.OVERVIEW) {
         this.syncHairToServer();
         this.updateFullCodeBox();
      } else {
         this.syncHairToServer();
         Minecraft.getInstance().setScreen(this.previousScreen);
      }
   }

   private void openHairSalon() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
               Util.getPlatform().openUri("https://dragonminez.com/hairsalon");
            }

            this.minecraft.setScreen(this);
         }, "https://dragonminez.com/hairsalon", true));
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.panorama.render(graphics, this.width, this.height, 1.0F, partialTick);
      this.renderCinematicBars(graphics);
      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.currentZoom = Mth.lerp(0.3F, this.currentZoom, this.targetZoom);
      int previewZoneLeft = 169;
      int previewZoneRight = this.getUiWidth() - 16;
      int baseX = previewZoneLeft + (previewZoneRight - previewZoneLeft) / 2;
      int baseY = (int)((float)(this.getUiHeight() / 2 + 112) + (this.currentZoom - 95.0F) * 2.436F);
      this.renderPlayerModel(graphics, baseX, baseY, (int)this.currentZoom);
      this.renderPanelBackground(graphics);
      if (this.currentTab == HairEditorScreen.Tab.OVERVIEW) {
         this.renderOverviewContent(graphics);
      } else if (this.currentTab == HairEditorScreen.Tab.STYLE) {
         this.renderStyleContent(graphics, uiMouseX, uiMouseY);
      } else {
         this.renderStrandContent(graphics);
      }

      if (this.colorPickerVisible) {
         this.renderColorPickerBackground(graphics);
      }

      this.drawTopRightLabels(graphics);
      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 400.0);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      graphics.pose().popPose();
      this.endUiScale(graphics);
   }

   private void renderCinematicBars(GuiGraphics graphics) {
      int barH = (int)((double)this.height * 0.12);
      graphics.fill(0, 0, this.width, barH - 60, -16777216);
      graphics.fillGradient(0, barH - 60, this.width, barH, -16777216, 0);
      graphics.fillGradient(0, this.height - barH, this.width, this.height - barH + 60, 0, -16777216);
      graphics.fill(0, this.height - barH + 60, this.width, this.height, -16777216);
   }

   private void renderPanelBackground(GuiGraphics graphics) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, leftPanelX, panelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, leftPanelX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
   }

   private void drawTopRightLabels(GuiGraphics graphics) {
      int rightEdge = this.getUiWidth() - 12;
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.hair_editor.mirror"),
         rightEdge - 30 - this.font.width(this.tr("gui.dragonminez.hair_editor.mirror")) - 5,
         17,
         16777215
      );
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.hair_editor.physics"),
         rightEdge - 90 - this.font.width(this.tr("gui.dragonminez.hair_editor.physics")) - 5,
         17,
         16777215
      );
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.hair_editor.hairbase"),
         rightEdge - 170 - this.font.width(this.tr("gui.dragonminez.hair_editor.hairbase")) - 5,
         17,
         16777215
      );
   }

   private void renderOverviewContent(GuiGraphics graphics) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.hair_editor.styles").withStyle(ChatFormatting.BOLD), leftPanelX + 70, panelY + 17, -10496
      );
      TextUtil.drawStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.hair_editor.fullcode"), leftPanelX + 15, panelY + 138, 16777215);
      if (this.actionStatusTimer > 0) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.actionStatusText, leftPanelX + 70, panelY + 195, this.actionStatusColor);
      }
   }

   private void renderStyleContent(GuiGraphics graphics, int mouseX, int mouseY) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.hair_editor.hair_strands").withStyle(ChatFormatting.BOLD), leftPanelX + 70, panelY + 17, -10496
      );
      this.renderFaceSelector(graphics, leftPanelX, panelY, mouseX, mouseY);
      this.renderStrandsGrid(graphics, leftPanelX, panelY, mouseX, mouseY);
      TextUtil.drawStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.hair_editor.stylecode"), leftPanelX + 15, panelY + 162, 16777215);
      if (this.actionStatusTimer > 0) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.actionStatusText, leftPanelX + 70, panelY + 205, this.actionStatusColor);
      }
   }

   private void renderStrandContent(GuiGraphics graphics) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.hair_editor.edit_values").withStyle(ChatFormatting.BOLD), leftPanelX + 70, panelY + 17, -10496
      );
      if (this.lengthSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.length"), this.lengthSlider.getX(), this.lengthSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.lengthSlider.getValue())),
            this.lengthSlider.getX() + this.lengthSlider.getWidth() + 2,
            this.lengthSlider.getY() + 2,
            16777215
         );
      }

      if (this.widthSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.width"), this.widthSlider.getX(), this.widthSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.widthSlider.getValue())),
            this.widthSlider.getX() + this.widthSlider.getWidth() + 2,
            this.widthSlider.getY() + 2,
            16777215
         );
      }

      if (this.xAxisSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.x_axis"), this.xAxisSlider.getX(), this.xAxisSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.xAxisSlider.getValue())),
            this.xAxisSlider.getX() + this.xAxisSlider.getWidth() + 2,
            this.xAxisSlider.getY() + 2,
            16777215
         );
      }

      if (this.zAxisSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.z_axis"), this.zAxisSlider.getX(), this.zAxisSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.zAxisSlider.getValue())),
            this.zAxisSlider.getX() + this.zAxisSlider.getWidth() + 2,
            this.zAxisSlider.getY() + 2,
            16777215
         );
      }

      if (this.xBendSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.x_bend"), this.xBendSlider.getX(), this.xBendSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.xBendSlider.getValue())),
            this.xBendSlider.getX() + this.xBendSlider.getWidth() + 2,
            this.xBendSlider.getY() + 2,
            16777215
         );
      }

      if (this.zBendSlider != null) {
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.hair_editor.z_bend"), this.zBendSlider.getX(), this.zBendSlider.getY() - 10, 16777215
         );
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.txt(String.format("%.1f", this.zBendSlider.getValue())),
            this.zBendSlider.getX() + this.zBendSlider.getWidth() + 2,
            this.zBendSlider.getY() + 2,
            16777215
         );
      }
   }

   private void renderFaceSelector(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      int btnY = panelY + 35;
      int btnX = panelX + 28;
      String[] faceNames = new String[]{"F", "B", "L", "R", "T"};
      CustomHair.HairFace[] faces = CustomHair.HairFace.values();

      for (int i = 0; i < faces.length; i++) {
         boolean isSelected = this.currentFace == faces[i];
         int width = this.font.width(faceNames[i]) + 6;
         boolean hovered = mouseX >= btnX && mouseX < btnX + width && mouseY >= btnY && mouseY < btnY + 14;
         int bgColor = isSelected ? -16733696 : (hovered ? -11184811 : -13421773);
         graphics.fill(btnX, btnY, btnX + width, btnY + 14, bgColor);
         graphics.fill(btnX + 1, btnY + 1, btnX + width - 1, btnY + 13, isSelected ? -16755456 : -14540254);
         graphics.drawString(this.font, faceNames[i], btnX + 3, btnY + 3, isSelected ? 16777215 : 11184810, false);
         btnX += width + 6;
      }
   }

   private void renderStrandsGrid(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      int startY = panelY + 55;
      HairStrand[] strands = this.workingHairs[this.selectedStyle].getStrands(this.currentFace);
      if (strands != null) {
         int cols = this.currentFace.cols;
         int boxSize = 24;
         int spacing = 3;
         int gridStartX = panelX + (141 - (cols * boxSize + (cols - 1) * spacing)) / 2;

         for (int i = 0; i < strands.length; i++) {
            int boxX = gridStartX + i % cols * (boxSize + spacing);
            int boxY = startY + i / cols * (boxSize + spacing);
            boolean isSelected = i == this.selectedStrandIndex;
            boolean isVisible = strands[i].isVisible();
            boolean hovered = mouseX >= boxX && mouseX < boxX + boxSize && mouseY >= boxY && mouseY < boxY + boxSize;
            int bgColor = isSelected ? -16733696 : (hovered ? -11184811 : (isVisible ? -10066432 : -13421773));
            graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, bgColor);
            graphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, isSelected ? -16755456 : -14540254);
            String numText = String.valueOf(i);
            graphics.drawString(
               this.font,
               numText,
               boxX + (boxSize - this.font.width(numText)) / 2,
               boxY + (boxSize - 9) / 2,
               isSelected ? '\uff00' : (isVisible ? 16776960 : 8947848),
               false
            );
         }
      }
   }

   private void renderColorPickerBackground(GuiGraphics graphics) {
      int sliderX = 162;
      int sliderY = this.getUiHeight() / 2 - 40;
      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 200.0);
      graphics.fill(sliderX - 5, sliderY - 5, sliderX + 110, sliderY + 56, -2013265920);
      graphics.pose().popPose();
   }

   private void renderPlayerModel(GuiGraphics graphics, int x, int y, int scale) {
      LivingEntity player = this.minecraft.player;
      if (player != null) {
         boolean oldPhysics = HairRenderer.PHYSICS_ENABLED;
         HairRenderer.PHYSICS_ENABLED = this.physicsEnabled;
         int originalHairId = this.character.getHairId();
         CustomHair originalBaseHair = this.character.getHairBase();
         this.character.setHairId(0);
         if (this.currentTab == HairEditorScreen.Tab.OVERVIEW) {
            this.character.setHairBase(this.workingHairs[0]);
         } else {
            this.character.setHairBase(this.workingHairs[this.selectedStyle]);
         }

         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX(0.0F));
         float yBodyRotO = player.yBodyRot;
         float yBodyRotO_field = player.yBodyRotO;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float xRotO_field = player.xRotO;
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = this.playerRotation;
         player.yBodyRotO = this.playerRotation;
         player.setYRot(this.playerRotation);
         player.setXRot(this.playerPitch);
         player.xRotO = this.playerPitch;
         player.yHeadRot = this.playerRotation;
         player.yHeadRotO = this.playerRotation;
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         EntityPreviewRenderContext.renderEntityInInventory(
            graphics, x, y, scale, new Vector3f(0.0F, 0.0F, 0.0F), pose, new Quaternionf().rotateX(0.0F), player
         );
         graphics.pose().popPose();
         player.yBodyRot = yBodyRotO;
         player.yBodyRotO = yBodyRotO_field;
         player.setYRot(yRotO);
         player.setXRot(xRotO);
         player.xRotO = xRotO_field;
         player.yHeadRotO = yHeadRotO;
         player.yHeadRot = yHeadRot;
         HairRenderer.PHYSICS_ENABLED = oldPhysics;
         this.character.setHairBase(originalBaseHair);
         this.character.setHairId(originalHairId);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         if (this.colorPickerVisible) {
            int sliderX = 162;
            int sliderY = this.getUiHeight() / 2 - 40;
            if (!(uiMouseX < (double)(sliderX - 5))
               && !(uiMouseX > (double)(sliderX + 110))
               && !(uiMouseY < (double)(sliderY - 5))
               && !(uiMouseY > (double)(sliderY + 56))) {
               return false;
            } else {
               this.setSlidersVisible(false);
               this.colorPickerVisible = false;
               return true;
            }
         } else {
            if (this.currentTab == HairEditorScreen.Tab.STYLE) {
               if (this.handleFaceSelectorClick(uiMouseX, uiMouseY)) {
                  return true;
               }

               if (this.handleStrandGridClick(uiMouseX, uiMouseY)) {
                  return true;
               }
            }

            int previewZoneLeft = 169;
            int previewZoneRight = this.getUiWidth() - 16;
            if (uiMouseX >= (double)previewZoneLeft && uiMouseX <= (double)previewZoneRight && uiMouseY >= 45.0) {
               this.isDraggingModel = true;
               this.lastMouseX = uiMouseX;
               this.lastMouseY = uiMouseY;
               return true;
            } else {
               return false;
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isDraggingModel = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isDraggingModel && !this.colorPickerVisible) {
         double deltaX = this.toUiX(mouseX) - this.lastMouseX;
         double deltaY = this.toUiY(mouseY) - this.lastMouseY;
         this.playerRotation -= (float)deltaX;
         this.playerPitch = Math.max(-90.0F, Math.min(90.0F, this.playerPitch + (float)deltaY));
         this.lastMouseX = this.toUiX(mouseX);
         this.lastMouseY = this.toUiY(mouseY);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      int previewZoneLeft = 169;
      if (uiMouseX >= (double)previewZoneLeft && !this.colorPickerVisible) {
         this.targetZoom += (float)scrollY * 20.0F;
         this.targetZoom = Mth.clamp(this.targetZoom, 95.0F, 250.0F);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private boolean handleFaceSelectorClick(double mouseX, double mouseY) {
      int btnY = this.getUiHeight() / 2 - 105 + 35;
      int btnX = 40;
      CustomHair.HairFace[] faces = CustomHair.HairFace.values();
      String[] names = new String[]{"F", "B", "L", "R", "T"};

      for (int i = 0; i < faces.length; i++) {
         int width = this.font.width(names[i]) + 6;
         if (mouseX >= (double)btnX && mouseX < (double)(btnX + width) && mouseY >= (double)btnY && mouseY < (double)(btnY + 14)) {
            this.currentFace = faces[i];
            this.selectedStrandIndex = 0;
            this.rebuildWidgets();
            return true;
         }

         btnX += width + 6;
      }

      return false;
   }

   private boolean handleStrandGridClick(double mouseX, double mouseY) {
      HairStrand[] strands = this.workingHairs[this.selectedStyle].getStrands(this.currentFace);
      if (strands == null) {
         return false;
      } else {
         int cols = this.currentFace.cols;
         int boxSize = 24;
         int spacing = 3;
         int gridStartX = 12 + (141 - (cols * boxSize + (cols - 1) * spacing)) / 2;
         int startY = this.getUiHeight() / 2 - 105 + 55;

         for (int i = 0; i < strands.length; i++) {
            int boxX = gridStartX + i % cols * (boxSize + spacing);
            int boxY = startY + i / cols * (boxSize + spacing);
            if (mouseX >= (double)boxX && mouseX < (double)(boxX + boxSize) && mouseY >= (double)boxY && mouseY < (double)(boxY + boxSize)) {
               this.selectedStrandIndex = i;
               this.currentTab = HairEditorScreen.Tab.STRAND;
               this.rebuildWidgets();
               return true;
            }
         }

         return false;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.navigateBack();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void onClose() {
      this.navigateBack();
   }

   @Override
   public MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   @Override
   public MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private static enum Tab {
      OVERVIEW,
      STYLE,
      STRAND;
   }
}
