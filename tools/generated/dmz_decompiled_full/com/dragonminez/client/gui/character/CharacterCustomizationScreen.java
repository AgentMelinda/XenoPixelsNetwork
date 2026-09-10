package com.dragonminez.client.gui.character;

import com.dragonminez.client.events.ForgeClientEvents;
import com.dragonminez.client.gui.HairEditorScreen;
import com.dragonminez.client.gui.buttons.ColorSlider;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.hair.HairRenderer;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.client.render.shader.UtilityMenuBlur;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.config.RaceStatsConfig;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.CreateCharacterC2S;
import com.dragonminez.common.network.C2S.StatsSyncC2S;
import com.dragonminez.common.network.C2S.UpdateCharacterC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.util.TransformationsHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class CharacterCustomizationScreen extends ScaledScreen {
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final int LEFT_PANEL_X = 12;
   private static final int LEFT_PANEL_WIDTH = 141;
   private static final int AURA_MODEL_OFFSET = 55;
   private static final int PANEL_Z = 350;
   private static final int LEFT_PANEL_HEIGHT = 213;
   private static final int LEFT_PANEL_PADDING = 12;
   private static final int PREVIEW_GRID_COLUMNS = 3;
   private static final int PREVIEW_GRID_VISIBLE_ROWS = 3;
   private static final int PREVIEW_CARD_WIDTH = 34;
   private static final int PREVIEW_CARD_HEIGHT = 44;
   private static final int PREVIEW_CARD_GAP = 5;
   private float displayedProgress = 0.0F;
   private float displayedScale = 95.0F;
   private float displayedBaseY = 0.0F;
   private boolean initializedAnimations = false;
   private static final List<String> PREVIEW_FORM_TYPE_ORDER = List.of("superforms", "androidforms", "legendaryforms", "godforms");
   private final List<CharacterCustomizationScreen.TabId> activeTabs = new ArrayList<>();
   private final Map<String, PanoramaRenderer> panoramaCache = new HashMap<>();
   private final Screen previousScreen;
   private final Character character;
   private int currentClassIndex = 0;
   private final List<CharacterCustomizationScreen.PreviewFormOption> previewFormOptions = new ArrayList<>();
   private final Map<String, TexturedTextButton> colorButtons = new HashMap<>();
   private int previewFormIndex = -1;
   private int currentTabIndex = 0;
   private int bodyTypePreviewScrollRows = 0;
   private int hairPreviewScrollRows = 0;
   private int eyesPreviewScrollRows = 0;
   private int nosePreviewScrollRows = 0;
   private int mouthPreviewScrollRows = 0;
   private int tattooPreviewScrollRows = 0;
   private final ScrollbarState bodyTypeBar = new ScrollbarState();
   private final ScrollbarState hairBar = new ScrollbarState();
   private final ScrollbarState eyesBar = new ScrollbarState();
   private final ScrollbarState noseBar = new ScrollbarState();
   private final ScrollbarState mouthBar = new ScrollbarState();
   private final ScrollbarState tattooBar = new ScrollbarState();
   private float playerRotation = 180.0F;
   private float playerPitch = 12.0F;
   private boolean isDraggingModel = false;
   private double lastMouseX = 0.0;
   private double lastMouseY = 0.0;
   private ColorSlider hueSlider;
   private ColorSlider saturationSlider;
   private ColorSlider valueSlider;
   private EditBox hexColorField;
   private boolean colorPickerVisible = false;
   private String currentColorField = "";
   private boolean isUpdatingFromCode = false;

   public CharacterCustomizationScreen(Screen previousScreen, Character character) {
      super(Component.translatable("gui.dragonminez.customization.title"));
      this.previousScreen = previousScreen;
      this.character = character;
      this.initializeDefaultColors();
   }

   protected void init() {
      UtilityMenuBlur.stop();
      super.init();
      this.activeTabs.clear();
      this.activeTabs.add(CharacterCustomizationScreen.TabId.PRESET);
      if (this.getMaxHairForCurrentState() > 0) {
         this.activeTabs.add(CharacterCustomizationScreen.TabId.HAIR);
      }

      this.activeTabs.add(CharacterCustomizationScreen.TabId.EYES);
      this.activeTabs.add(CharacterCustomizationScreen.TabId.FACE);
      this.activeTabs.add(CharacterCustomizationScreen.TabId.BODY);
      this.activeTabs.add(CharacterCustomizationScreen.TabId.AURA_CLASS);
      this.resolveClassIndex();
      this.reloadPreviewFormOptions();
      this.refreshScreenWidgets();
   }

   private PanoramaRenderer getPanorama(String raceName) {
      return this.panoramaCache
         .computeIfAbsent(
            raceName,
            k -> {
               ResourceLocation testLoc = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/" + k + "_panorama_0.png");
               boolean exists = false;
               if (Minecraft.getInstance().getResourceManager() != null) {
                  exists = Minecraft.getInstance().getResourceManager().getResource(testLoc).isPresent();
               }

               ResourceLocation baseLoc = exists
                  ? ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/" + k + "_panorama")
                  : ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/roshi");
               return new PanoramaRenderer(new CubeMap(baseLoc));
            }
         );
   }

   protected void refreshScreenWidgets() {
      this.colorButtons.clear();
      this.clearWidgets();
      this.initTabWidgets();
      this.initNavigationButtons();
      this.initColorPickerSliders();
   }

   private void initTabWidgets() {
      int top = this.getUiHeight() / 2 - 106 + 12;
      CharacterCustomizationScreen.TabId tab = this.activeTabs.get(this.currentTabIndex);
      switch (tab) {
         case PRESET:
            this.initPresetTab(top);
            break;
         case HAIR:
            this.initHairTab(top);
            break;
         case EYES:
            this.initEyesTab(top);
         case FACE:
         case BODY:
         default:
            break;
         case AURA_CLASS:
            this.initAuraClassTab(top);
      }
   }

   private void initPresetTab(int top) {
      int bodyColorY = top + 12;
      boolean isBioAndroid = this.character.getRace().equalsIgnoreCase("bioandroid");
      int buttonCount = isBioAndroid ? 4 : 3;
      int buttonWidth = 20;
      int spacing = 10;
      int totalWidth = buttonCount * buttonWidth + (buttonCount - 1) * spacing;
      int startX = 82 - totalWidth / 2;
      this.addRenderableWidget(this.createColorButton(startX, bodyColorY, "bodyColor"));
      this.addRenderableWidget(this.createColorButton(startX + buttonWidth + spacing, bodyColorY, "bodyColor2"));
      this.addRenderableWidget(this.createColorButton(startX + (buttonWidth + spacing) * 2, bodyColorY, "bodyColor3"));
      if (isBioAndroid) {
         this.addRenderableWidget(this.createColorButton(startX + (buttonWidth + spacing) * 3, bodyColorY, "hairColor"));
      }

      if ("female".equalsIgnoreCase(this.character.getGender())) {
         this.addRenderableWidget(
            new ColorSlider.Builder()
               .position(32, top + 160)
               .size(100, 8)
               .range(15, 25)
               .value(Math.round(Math.max(0.75F, Math.min(1.25F, this.character.getBoobScale())) * 20.0F))
               .message(this.txt("BoobScale"))
               .onValueChange(v -> {
                  float newScale = (float)v.intValue() / 20.0F;
                  if (this.character.getBoobScale() != newScale) {
                     this.character.setBoobScale(newScale);
                     this.syncCharacter();
                  }
               })
               .build()
         );
      }

      if (this.shouldRenderFormPreviewInPreset()) {
         this.initPreviewTransformationArrows(top + 174);
      }
   }

   private void initHairTab(int top) {
      int y = top + 28;
      this.addRenderableWidget(this.createColorButton(72, y - 18, "hairColor"));
      if (HairManager.canUseHair(this.character)) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(45, this.getUiHeight() - 40)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.customization.edit", new Object[0]))
               .onPress(btn -> {
                  if (this.minecraft != null) {
                     this.minecraft.setScreen(new HairEditorScreen(this, this.character));
                  }
               })
               .build()
         );
      }

      this.initPreviewTransformationArrows(top + 174);
   }

   private void initPreviewTransformationArrows(int arrowsY) {
      if (this.previewFormIndex > 0) {
         this.addRenderableWidget(this.createArrowButton(30, arrowsY, true, btn -> {
            this.changePreviewTransformation(-1);
            this.refreshScreenWidgets();
         }));
      }

      if (this.previewFormIndex >= 0 && this.previewFormIndex < this.previewFormOptions.size() - 1) {
         this.addRenderableWidget(this.createArrowButton(125, arrowsY, false, btn -> {
            this.changePreviewTransformation(1);
            this.refreshScreenWidgets();
         }));
      }
   }

   private boolean shouldRenderFormPreviewInPreset() {
      return !this.activeTabs.contains(CharacterCustomizationScreen.TabId.HAIR);
   }

   private void initEyesTab(int top) {
      int y = top + 8;
      this.addRenderableWidget(this.createColorButton(45, y + 2, "eye1Color"));
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(77, y + 7)
            .size(10, 10)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(102, 0, 102, 10)
            .textureSize(10, 10)
            .message(Component.empty())
            .onPress(btn -> {
               this.character.setEye2Color(this.character.getEye1Color());
               this.syncCharacter();
               this.refreshScreenWidgets();
            })
            .build()
      );
      this.addRenderableWidget(this.createColorButton(97, y + 2, "eye2Color"));
   }

   private void initAuraClassTab(int top) {
      int y = top + 8;
      this.addRenderableWidget(this.createColorButton(72, top + 136, "auraColor"));
      String[] classes = this.getRaceClasses();
      if (classes.length > 0) {
         this.character.setCharacterClass(classes[this.currentClassIndex]);
         if (this.currentClassIndex > 0) {
            this.addRenderableWidget(this.createArrowButton(30, y + 6, true, btn -> {
               if (this.currentClassIndex > 0) {
                  this.currentClassIndex--;
                  this.character.setCharacterClass(classes[this.currentClassIndex]);
                  this.syncCharacter();
                  this.refreshScreenWidgets();
               }
            }));
         }

         if (this.currentClassIndex < classes.length - 1) {
            this.addRenderableWidget(this.createArrowButton(125, y + 6, false, btn -> {
               if (this.currentClassIndex < classes.length - 1) {
                  this.currentClassIndex++;
                  this.character.setCharacterClass(classes[this.currentClassIndex]);
                  this.syncCharacter();
                  this.refreshScreenWidgets();
               }
            }));
         }
      }
   }

   private void onTabChanged() {
      this.hideColorPicker();
      CharacterCustomizationScreen.TabId newTab = this.activeTabs.get(this.currentTabIndex);
      if (newTab != CharacterCustomizationScreen.TabId.HAIR && (newTab != CharacterCustomizationScreen.TabId.PRESET || !this.shouldRenderFormPreviewInPreset())
         )
       {
         this.previewFormIndex = this.previewFormOptions.isEmpty() ? -1 : 0;
      }

      this.refreshScreenWidgets();
   }

   private void initNavigationButtons() {
      int buttonY = this.getUiHeight() - 28;
      int nextX = this.getUiWidth() - 86;
      int backX = nextX - 78;
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(backX, buttonY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.customization.back", new Object[0]))
            .onPress(btn -> {
               if (this.currentTabIndex > 0) {
                  this.currentTabIndex--;
                  this.onTabChanged();
               } else {
                  this.closeToPrevious();
               }
            })
            .build()
      );
      Component rightText = this.currentTabIndex == this.activeTabs.size() - 1
         ? (
            this.previousScreen == null
               ? this.tr("gui.dragonminez.customization.update", new Object[0])
               : this.tr("gui.dragonminez.customization.confirm", new Object[0])
         )
         : this.tr("gui.dragonminez.customization.next", new Object[0]);
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(nextX, buttonY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(rightText)
            .onPress(btn -> {
               if (this.currentTabIndex < this.activeTabs.size() - 1) {
                  this.currentTabIndex++;
                  this.onTabChanged();
               } else {
                  this.finish();
               }
            })
            .build()
      );
   }

   private void initColorPickerSliders() {
      int sliderX = 161;
      int sliderY = this.getUiHeight() / 2 - 40;
      int sliderWidth = 80;
      this.hueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY)
         .size(sliderWidth, 10)
         .range(0, 360)
         .value(0)
         .message(this.txt("Hue"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.saturationSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 12)
         .size(sliderWidth, 10)
         .range(100, 0)
         .value(100)
         .message(this.txt("Saturation"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.valueSlider = new ColorSlider.Builder()
         .position(sliderX, sliderY + 24)
         .size(sliderWidth, 10)
         .range(100, 0)
         .value(100)
         .message(this.txt("Value"))
         .onValueChange(val -> this.updateColorFromSliders())
         .build();
      this.addRenderableWidget(this.hueSlider);
      this.addRenderableWidget(this.saturationSlider);
      this.addRenderableWidget(this.valueSlider);
      this.hexColorField = new EditBox(this.font, sliderX, sliderY + 36, sliderWidth, 12, this.txt("Hex"));
      this.hexColorField.setMaxLength(7);
      this.hexColorField.setResponder(this::onHexFieldChange);
      this.addRenderableWidget(this.hexColorField);
      this.setSlidersVisible();
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderPanorama(graphics, partialTick);
      this.renderCinematicBars(graphics);
      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.renderPlayerModel(graphics, partialTick);
      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 350.0);
      this.renderLeftPanel(graphics);
      this.renderTabText(graphics, uiMouseX, uiMouseY);
      graphics.pose().popPose();
      this.renderProgress(graphics);
      if (this.colorPickerVisible) {
         this.renderColorPickerBackground(graphics);
         this.renderColorPreviewSquare(graphics);
      }

      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 400.0);
      graphics.flush();
      RenderSystem.clear(256, Minecraft.ON_OSX);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      graphics.pose().popPose();
      this.endUiScale(graphics);
   }

   private void renderLeftPanel(GuiGraphics graphics) {
      int panelY = this.getUiHeight() / 2 - 106;
      RenderSystem.enableBlend();
      graphics.blit(MENU_BIG, 12, panelY, 0, 0, 141, 213);
      RenderSystem.disableBlend();
   }

   private void renderProgress(GuiGraphics graphics) {
      int barW = 152;
      int barX = this.getUiWidth() - 164;
      int barY = this.getUiHeight() - 40;
      int barH = 6;
      float targetProgress = (float)(this.currentTabIndex + 1) / (float)this.activeTabs.size();
      this.displayedProgress = Mth.lerp(0.15F, this.displayedProgress, targetProgress);
      int fillW = Mth.floor((float)barW * this.displayedProgress);
      graphics.pose().pushPose();
      graphics.pose().translate(0.0, 0.0, 500.0);
      String text = this.currentTabIndex + 1 + "/" + this.activeTabs.size();
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(text), barX + barW / 2, barY - 12, 16777215);
      graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, -1);
      graphics.fill(barX, barY, barX + barW, barY + barH, -15658735);
      graphics.fill(barX, barY, barX + fillW, barY + barH, -16751616);
      graphics.pose().popPose();
   }

   private void renderTabText(GuiGraphics graphics, int mouseX, int mouseY) {
      int panelY = this.getUiHeight() / 2 - 106;
      int centerX = 82;
      int top = panelY + 12;
      CharacterCustomizationScreen.TabId tab = this.activeTabs.get(this.currentTabIndex);
      this.bodyTypeBar.clear();
      this.hairBar.clear();
      this.eyesBar.clear();
      this.noseBar.clear();
      this.mouthBar.clear();
      this.tattooBar.clear();
      switch (tab) {
         case PRESET:
            this.renderPresetText(graphics, centerX, top);
            break;
         case HAIR:
            this.renderHairText(graphics, centerX, top);
            break;
         case EYES:
            this.renderEyesText(graphics, centerX, top);
            break;
         case FACE:
            this.renderFaceText(graphics, centerX, top);
            break;
         case BODY:
            this.renderBodyText(graphics, centerX, top);
            break;
         case AURA_CLASS:
            this.renderAuraClassText(graphics, centerX, top, mouseX, mouseY);
      }
   }

   private void renderPresetText(GuiGraphics graphics, int centerX, int top) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.body_type", new Object[0]), centerX, top + 2, 16751515);
      this.renderPreviewGrid(
         graphics,
         this.bodyTypeBar,
         top + 40,
         0,
         this.getCombinedBodyTypeCount(),
         this.getCurrentCombinedBodyTypeValue(),
         CharacterCustomizationScreen.PreviewRenderMode.FULL_BODY,
         false,
         3,
         this.bodyTypePreviewScrollRows
      );
      if ("female".equalsIgnoreCase(this.character.getGender())) {
         TextUtil.drawCenteredStringWithBorder(
            graphics,
            this.font,
            this.txt(
               this.tr("gui.dragonminez.customization.chest_size", new Object[0]).getString() + " x" + String.format("%.2f", this.character.getBoobScale())
            ),
            centerX,
            top + 150,
            16751515
         );
      }

      if (this.shouldRenderFormPreviewInPreset()) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(this.getCurrentPreviewTransformationName()), centerX, top + 178, 16777215);
      }
   }

   private void renderHairText(GuiGraphics graphics, int centerX, int top) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.hair", new Object[0]), centerX, top + 2, 16751515);
      int maxHairIndex = Math.max(0, this.getMaxHairForCurrentState() - 1);
      Set<Integer> selected = this.getSelectedHeadBoneValues();
      this.renderPreviewGrid(
         graphics,
         this.hairBar,
         top + 30,
         0,
         maxHairIndex,
         selected::contains,
         CharacterCustomizationScreen.PreviewRenderMode.HAIR_ONLY,
         true,
         3,
         this.hairPreviewScrollRows
      );
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(this.getCurrentPreviewTransformationName()), centerX, top + 178, 16777215);
   }

   private void renderEyesText(GuiGraphics graphics, int centerX, int top) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.eyes", new Object[0]), centerX, top + 2, 16751515);
      this.renderPreviewGrid(
         graphics,
         this.eyesBar,
         top + 30,
         0,
         Math.max(1, TextureCounter.getMaxEyesTypes(this.getEffectiveModelBase())),
         this.character.getEyesType(),
         CharacterCustomizationScreen.PreviewRenderMode.EYES_ONLY,
         true,
         3,
         this.eyesPreviewScrollRows
      );
   }

   private void renderFaceText(GuiGraphics graphics, int centerX, int top) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.nose", new Object[0]), centerX, top + 2, 16751515);
      this.renderPreviewGrid(
         graphics,
         this.noseBar,
         top + 20,
         0,
         Math.max(1, TextureCounter.getMaxNoseTypes(this.getEffectiveModelBase())),
         this.character.getNoseType(),
         CharacterCustomizationScreen.PreviewRenderMode.NOSE_ONLY,
         true,
         1,
         this.nosePreviewScrollRows
      );
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.mouth", new Object[0]), centerX, top + 74, 16751515);
      this.renderPreviewGrid(
         graphics,
         this.mouthBar,
         top + 94,
         0,
         Math.max(1, TextureCounter.getMaxMouthTypes(this.getEffectiveModelBase())),
         this.character.getMouthType(),
         CharacterCustomizationScreen.PreviewRenderMode.MOUTH_ONLY,
         true,
         2,
         this.mouthPreviewScrollRows
      );
   }

   private void renderBodyText(GuiGraphics graphics, int centerX, int top) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.tattoo", new Object[0]), centerX, top + 2, 16751515);
      this.renderPreviewGrid(
         graphics,
         this.tattooBar,
         top + 30,
         0,
         Math.max(1, TextureCounter.getMaxTattooTypes(this.getEffectiveModelBase())),
         this.character.getTattooType(),
         CharacterCustomizationScreen.PreviewRenderMode.TATTOO_ONLY,
         false,
         3,
         this.tattooPreviewScrollRows
      );
   }

   private void renderAuraClassText(GuiGraphics graphics, int centerX, int top, int mouseX, int mouseY) {
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.class", new Object[0]), centerX, top + 8, 16751515);
      Component className = this.tr("class.dragonminez." + this.character.getCharacterClass(), new Object[0]);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, className, centerX, top + 20, 16777215);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.customization.aura", new Object[0]), centerX, top + 124, 16751515);
      this.renderBaseStatsInline(graphics, centerX, top + 44, mouseX, mouseY);
      this.renderPassiveDescription(graphics);
   }

   private void renderPassiveDescription(GuiGraphics graphics) {
      int panelWidth = 130;
      int marginFromEdge = 10;
      int boxEndX = this.getUiWidth() - marginFromEdge;
      int centerX = boxEndX - panelWidth / 2;
      int startY = this.getUiHeight() / 2 - 50;
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("class.dragonminez.passive", new Object[0]), centerX, startY - 12, -11776);
      Component desc = this.tr("class.dragonminez." + this.character.getCharacterClass() + ".passive.desc", new Object[0]);
      int textY = startY;

      for (String line : this.wrapText(desc.getString(), panelWidth)) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(line), centerX, textY, -3355444);
         textY += 12;
      }
   }

   private List<String> wrapText(String text, int maxWidth) {
      return TextUtil.wrap(this.font, text, maxWidth, DMZ_FONT);
   }

   private void renderBaseStatsInline(GuiGraphics graphics, int centerX, int startY, int mouseX, int mouseY) {
      RaceStatsConfig statsConfig = ConfigManager.getRaceStats(this.character.getRace());
      if (statsConfig != null) {
         RaceStatsConfig.ClassStats classStats = statsConfig.getClassStats(this.character.getCharacterClass());
         if (classStats != null && classStats.getBaseStats() != null && classStats.getStatScaling() != null) {
            RaceStatsConfig.BaseStats base = classStats.getBaseStats();
            RaceStatsConfig.StatScaling scaling = classStats.getStatScaling();
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.character_stats.base_stats", new Object[0]), centerX, startY, 16751515
            );
            int row1Y = startY + 16;
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.character_stats.str", new Object[0]), centerX - 40, row1Y, 8191446
            );
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.character_stats.skp", new Object[0]), centerX, row1Y, 8191446);
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.character_stats.res", new Object[0]), centerX + 40, row1Y, 8191446
            );
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getStrength())), centerX - 40, row1Y + 12, 16777215);
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getStrikePower())), centerX, row1Y + 12, 16777215);
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getResistance())), centerX + 40, row1Y + 12, 16777215);
            int row2Y = startY + 48;
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.character_stats.vit", new Object[0]), centerX - 40, row2Y, 8191446
            );
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.character_stats.pwr", new Object[0]), centerX, row2Y, 8191446);
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.character_stats.ene", new Object[0]), centerX + 40, row2Y, 8191446
            );
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getVitality())), centerX - 40, row2Y + 12, 16777215);
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getKiPower())), centerX, row2Y + 12, 16777215);
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(String.valueOf(base.getEnergy())), centerX + 40, row2Y + 12, 16777215);
            int tpY = startY + 116;
            Double tpGain = classStats.getTpGainMultiplier() != null ? classStats.getTpGainMultiplier() : 1.0;
            Double tpCost = classStats.getTpCostMultiplier() != null ? classStats.getTpCostMultiplier() : 1.0;
            Component tpGainComp = this.txt("")
               .append(Component.translatable("gui.dragonminez.character_stats.tp_multiplier").withStyle(ChatFormatting.AQUA))
               .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
               .append(Component.literal("x" + String.format(Locale.US, "%.2f", tpGain)).withStyle(ChatFormatting.YELLOW));
            Component tpCostComp = this.txt("")
               .append(Component.translatable("gui.dragonminez.character_stats.tpc_multiplier").withStyle(ChatFormatting.AQUA))
               .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
               .append(Component.literal("x" + String.format(Locale.US, "%.2f", tpCost)).withStyle(ChatFormatting.YELLOW));
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, tpGainComp, centerX, tpY, 16777215);
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, tpCostComp, centerX, tpY + 12, 16777215);
            Component title = null;
            List<Component> desc = new ArrayList<>();
            List<Component> extras = new ArrayList<>();
            int headerColor = 14095410;
            if (mouseY >= row1Y && mouseY <= row1Y + 22) {
               if (mouseX >= centerX - 55 && mouseX <= centerX - 25) {
                  title = this.tr("gui.dragonminez.character_stats.str", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.str.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getStrengthScaling())).withStyle(ChatFormatting.GREEN))
                  );
               } else if (mouseX >= centerX - 15 && mouseX <= centerX + 15) {
                  title = this.tr("gui.dragonminez.character_stats.skp", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.skp.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getStrikePowerScaling())).withStyle(ChatFormatting.GREEN))
                  );
               } else if (mouseX >= centerX + 25 && mouseX <= centerX + 55) {
                  title = this.tr("gui.dragonminez.character_stats.res", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.res.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling.def", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getDefenseScaling())).withStyle(ChatFormatting.GREEN))
                  );
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling.stm", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getStaminaScaling())).withStyle(ChatFormatting.GREEN))
                  );
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.regen.stm", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.1f/s", classStats.getBaseSp5() * 0.2)).withStyle(ChatFormatting.YELLOW))
                        .append(
                           Component.literal(" (+" + String.format(Locale.US, "%.2f", classStats.getSp5StmScaling() * 0.2) + "/STM)")
                              .withStyle(ChatFormatting.DARK_GRAY)
                        )
                  );
               }
            } else if (mouseY >= row2Y && mouseY <= row2Y + 22) {
               if (mouseX >= centerX - 55 && mouseX <= centerX - 25) {
                  title = this.tr("gui.dragonminez.character_stats.vit", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.vit.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling.hp", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getVitalityScaling())).withStyle(ChatFormatting.GREEN))
                  );
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.regen.hp", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.1f/s", classStats.getBaseHp5() * 0.2)).withStyle(ChatFormatting.YELLOW))
                        .append(
                           Component.literal(" (+" + String.format(Locale.US, "%.2f", classStats.getHp5VitScaling() * 0.2) + "/VIT)")
                              .withStyle(ChatFormatting.DARK_GRAY)
                        )
                  );
               } else if (mouseX >= centerX - 15 && mouseX <= centerX + 15) {
                  title = this.tr("gui.dragonminez.character_stats.pwr", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.pwr.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getKiPowerScaling())).withStyle(ChatFormatting.GREEN))
                  );
               } else if (mouseX >= centerX + 25 && mouseX <= centerX + 55) {
                  title = this.tr("gui.dragonminez.character_stats.ene", new Object[0]).withStyle(ChatFormatting.BOLD);
                  desc.add(this.tr("gui.dragonminez.character_stats.ene.desc", new Object[0]));
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.scaling.ki", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.2f", scaling.getEnergyScaling())).withStyle(ChatFormatting.GREEN))
                  );
                  extras.add(
                     this.tr("gui.dragonminez.customization.stat.regen.ki", new Object[0])
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(Locale.US, "%.1f/s", classStats.getBaseEp5() * 0.2)).withStyle(ChatFormatting.YELLOW))
                        .append(
                           Component.literal(" (+" + String.format(Locale.US, "%.2f", classStats.getEp5EneScaling() * 0.2) + "/ENE)")
                              .withStyle(ChatFormatting.DARK_GRAY)
                        )
                  );
               }
            }

            boolean isTooltipActive = title != null;

            for (TexturedTextButton colorBtn : this.colorButtons.values()) {
               if (colorBtn != null) {
                  colorBtn.visible = !isTooltipActive;
               }
            }

            if (title != null) {
               TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, headerColor);
            }
         }
      }
   }

   private void renderPlayerModel(GuiGraphics graphics, float partialTick) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         CharacterCustomizationScreen.ActiveFormSnapshot snapshot = this.captureLocalPlayerFormSnapshot(player);
         boolean previewApplied = this.applyPreviewTransformationToPlayer(player);
         int previewZoneLeft = 169;
         int previewZoneRight = this.getUiWidth() - 16;
         int baseX = previewZoneLeft + (previewZoneRight - previewZoneLeft) / 2;
         float targetScale = 95.0F;
         float targetBaseY = (float)(this.getUiHeight() / 2 + 112);
         CharacterCustomizationScreen.TabId tab = this.activeTabs.get(this.currentTabIndex);
         if (tab == CharacterCustomizationScreen.TabId.HAIR || tab == CharacterCustomizationScreen.TabId.EYES || tab == CharacterCustomizationScreen.TabId.FACE
            )
          {
            targetScale = 150.0F;
            targetBaseY = (float)(this.getUiHeight() / 2 + 246);
         }

         if (tab == CharacterCustomizationScreen.TabId.AURA_CLASS) {
            baseX -= 55;
         }

         if (!this.initializedAnimations) {
            this.displayedScale = targetScale;
            this.displayedBaseY = targetBaseY;
            this.displayedProgress = (float)(this.currentTabIndex + 1) / (float)this.activeTabs.size();
            this.initializedAnimations = true;
         }

         this.displayedScale = Mth.lerp(0.15F, this.displayedScale, targetScale);
         this.displayedBaseY = Mth.lerp(0.15F, this.displayedBaseY, targetBaseY);
         int adjustedScale = this.getAdjustedModelScale(player, (int)this.displayedScale);
         int currentBaseY = (int)this.displayedBaseY;
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(0.0F);
         pose.mul(cameraOrientation);
         float yBodyRot = player.yBodyRot;
         float yBodyRotO = player.yBodyRotO;
         float yRot = player.getYRot();
         float yRotO = player.yRotO;
         float xRot = player.getXRot();
         float xRotO = player.xRotO;
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = this.playerRotation;
         player.yBodyRotO = this.playerRotation;
         player.setYRot(this.playerRotation);
         player.yRotO = this.playerRotation;
         player.setXRot(this.playerPitch);
         player.xRotO = this.playerPitch;
         player.yHeadRot = this.playerRotation;
         player.yHeadRotO = this.playerRotation;
         Minecraft mc = Minecraft.getInstance();
         float sw = (float)mc.getWindow().getGuiScaledWidth();
         float sh = (float)mc.getWindow().getGuiScaledHeight();
         Matrix4f guiProjection = new Matrix4f().ortho(0.0F, sw, sh, 0.0F, -10000.0F, 10000.0F);
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 320.0);
         DMZSkinLayer.PREVIEW_MODE = previewApplied;

         try {
            EntityPreviewRenderContext.renderEntityInInventory(
               graphics, baseX, currentBaseY, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player
            );
            if (tab == CharacterCustomizationScreen.TabId.AURA_CLASS) {
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               AuraRenderer.renderGuiAura(player, graphics.pose(), guiProjection, baseX, currentBaseY, adjustedScale, partialTick, true);
            }
         } finally {
            DMZSkinLayer.PREVIEW_MODE = false;
            graphics.pose().popPose();
            if (previewApplied && snapshot != null) {
               this.restoreLocalPlayerFormSnapshot(player, snapshot);
            }

            player.yBodyRot = yBodyRot;
            player.yBodyRotO = yBodyRotO;
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            player.yHeadRotO = yHeadRotO;
            player.yHeadRot = yHeadRot;
         }
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
            int sliderX = 161;
            int sliderY = this.getUiHeight() / 2 - 40;
            int totalWidth = 126;
            int totalHeight = 56;
            boolean inside = uiMouseX >= (double)(sliderX - 5)
               && uiMouseX <= (double)(sliderX + totalWidth)
               && uiMouseY >= (double)(sliderY - 5)
               && uiMouseY <= (double)(sliderY + totalHeight);
            if (!inside) {
               this.hideColorPicker();
               return true;
            } else {
               return false;
            }
         } else if (button == 0 && this.tryStartGridScrollDrag(uiMouseX, uiMouseY)) {
            return true;
         } else if (button == 0 && this.handlePreviewGridClick(uiMouseX, uiMouseY)) {
            return true;
         } else {
            int previewZoneLeft = 169;
            int previewZoneRight = this.getUiWidth() - 16;
            int centerX = previewZoneLeft + (previewZoneRight - previewZoneLeft) / 2;
            int centerY = this.getUiHeight() / 2 + 112;
            if (this.activeTabs.get(this.currentTabIndex) == CharacterCustomizationScreen.TabId.HAIR
               || this.activeTabs.get(this.currentTabIndex) == CharacterCustomizationScreen.TabId.EYES
               || this.activeTabs.get(this.currentTabIndex) == CharacterCustomizationScreen.TabId.FACE) {
               centerY = this.getUiHeight() / 2 + 246;
            }

            if (uiMouseX >= (double)(centerX - 90)
               && uiMouseX <= (double)(centerX + 90)
               && uiMouseY >= (double)(centerY - 370)
               && uiMouseY <= (double)(centerY + 28)) {
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
      this.stopGridScrollDrag();
      return super.mouseReleased(mouseX, mouseY, button);
   }

   private boolean tryStartGridScrollDrag(double mx, double my) {
      if (this.bodyTypeBar.tryStartDrag(mx, my)) {
         this.bodyTypePreviewScrollRows = Math.round(this.bodyTypeBar.scrollFor(my));
         return true;
      } else if (this.hairBar.tryStartDrag(mx, my)) {
         this.hairPreviewScrollRows = Math.round(this.hairBar.scrollFor(my));
         return true;
      } else if (this.eyesBar.tryStartDrag(mx, my)) {
         this.eyesPreviewScrollRows = Math.round(this.eyesBar.scrollFor(my));
         return true;
      } else if (this.noseBar.tryStartDrag(mx, my)) {
         this.nosePreviewScrollRows = Math.round(this.noseBar.scrollFor(my));
         return true;
      } else if (this.mouthBar.tryStartDrag(mx, my)) {
         this.mouthPreviewScrollRows = Math.round(this.mouthBar.scrollFor(my));
         return true;
      } else if (this.tattooBar.tryStartDrag(mx, my)) {
         this.tattooPreviewScrollRows = Math.round(this.tattooBar.scrollFor(my));
         return true;
      } else {
         return false;
      }
   }

   private boolean updateGridScrollDrag(double my) {
      if (this.bodyTypeBar.isDragging()) {
         this.bodyTypePreviewScrollRows = Math.round(this.bodyTypeBar.scrollFor(my));
         return true;
      } else if (this.hairBar.isDragging()) {
         this.hairPreviewScrollRows = Math.round(this.hairBar.scrollFor(my));
         return true;
      } else if (this.eyesBar.isDragging()) {
         this.eyesPreviewScrollRows = Math.round(this.eyesBar.scrollFor(my));
         return true;
      } else if (this.noseBar.isDragging()) {
         this.nosePreviewScrollRows = Math.round(this.noseBar.scrollFor(my));
         return true;
      } else if (this.mouthBar.isDragging()) {
         this.mouthPreviewScrollRows = Math.round(this.mouthBar.scrollFor(my));
         return true;
      } else if (this.tattooBar.isDragging()) {
         this.tattooPreviewScrollRows = Math.round(this.tattooBar.scrollFor(my));
         return true;
      } else {
         return false;
      }
   }

   private void stopGridScrollDrag() {
      this.bodyTypeBar.stopDrag();
      this.hairBar.stopDrag();
      this.eyesBar.stopDrag();
      this.noseBar.stopDrag();
      this.mouthBar.stopDrag();
      this.tattooBar.stopDrag();
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.updateGridScrollDrag(this.toUiY(mouseY))) {
         return true;
      } else if (this.isDraggingModel && !this.colorPickerVisible) {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         double deltaX = uiMouseX - this.lastMouseX;
         double deltaY = uiMouseY - this.lastMouseY;
         this.playerRotation -= (float)deltaX;
         this.playerPitch = Mth.clamp(this.playerPitch + (float)deltaY, -90.0F, 90.0F);
         this.lastMouseX = uiMouseX;
         this.lastMouseY = uiMouseY;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int top = this.getUiHeight() / 2 - 106 + 12;
      int direction = scrollY < 0.0 ? 1 : -1;
      CharacterCustomizationScreen.TabId tab = this.activeTabs.get(this.currentTabIndex);
      switch (tab) {
         case PRESET:
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 40, 0, this.getCombinedBodyTypeCount(), 3, direction, this.bodyTypePreviewScrollRows)) {
               this.bodyTypePreviewScrollRows = this.clampScrollRows(0, this.getCombinedBodyTypeCount(), 3, this.bodyTypePreviewScrollRows + direction);
               return true;
            }
            break;
         case HAIR:
            int maxHair = Math.max(0, this.getMaxHairForCurrentState() - 1);
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 30, 0, maxHair, 3, direction, this.hairPreviewScrollRows)) {
               this.hairPreviewScrollRows = this.clampScrollRows(0, maxHair, 3, this.hairPreviewScrollRows + direction);
               return true;
            }
            break;
         case EYES:
            int maxEyes = Math.max(1, TextureCounter.getMaxEyesTypes(this.getEffectiveModelBase()));
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 30, 0, maxEyes, 3, direction, this.eyesPreviewScrollRows)) {
               this.eyesPreviewScrollRows = this.clampScrollRows(0, maxEyes, 3, this.eyesPreviewScrollRows + direction);
               return true;
            }
            break;
         case FACE:
            int maxNose = Math.max(1, TextureCounter.getMaxNoseTypes(this.getEffectiveModelBase()));
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 20, 0, maxNose, 1, direction, this.nosePreviewScrollRows)) {
               this.nosePreviewScrollRows = this.clampScrollRows(0, maxNose, 1, this.nosePreviewScrollRows + direction);
               return true;
            }

            int maxMouth = Math.max(1, TextureCounter.getMaxMouthTypes(this.getEffectiveModelBase()));
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 94, 0, maxMouth, 2, direction, this.mouthPreviewScrollRows)) {
               this.mouthPreviewScrollRows = this.clampScrollRows(0, maxMouth, 2, this.mouthPreviewScrollRows + direction);
               return true;
            }
            break;
         case BODY:
            int maxTattoo = Math.max(1, TextureCounter.getMaxTattooTypes(this.getEffectiveModelBase()));
            if (this.tryScrollGrid(uiMouseX, uiMouseY, top + 30, 0, maxTattoo, 3, direction, this.tattooPreviewScrollRows)) {
               this.tattooPreviewScrollRows = this.clampScrollRows(0, maxTattoo, 3, this.tattooPreviewScrollRows + direction);
               return true;
            }
      }

      return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256
         && this.minecraft != null
         && this.minecraft.player != null
         && ConfigManager.getServerConfig().getGameplay().getForceCharacterCreation()) {
         boolean[] creationRequired = new boolean[]{false};
         StatsProvider.get(StatsCapability.INSTANCE, this.minecraft.player).ifPresent(data -> {
            if (!data.getStatus().isHasCreatedCharacter()) {
               creationRequired[0] = true;
            }
         });
         if (creationRequired[0]) {
            ForgeClientEvents.requestCharacterCreationReopen();
            this.minecraft.setScreen(new PauseScreen(true));
            return true;
         }
      }

      if (keyCode == 256) {
         if (this.colorPickerVisible) {
            this.hideColorPicker();
            return true;
         } else if (this.currentTabIndex > 0) {
            this.currentTabIndex--;
            this.onTabChanged();
            return true;
         } else {
            this.closeToPrevious();
            return true;
         }
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void onClose() {
      this.clearLocalPlayerTransformState();
      this.previewFormIndex = -1;
      this.previewFormOptions.clear();
      this.closeToPrevious();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void closeToPrevious() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.previousScreen);
      }
   }

   private void finish() {
      if (this.minecraft != null) {
         this.clearAllTransformSelections(this.character);
         this.clearLocalPlayerTransformState();
         this.previewFormIndex = -1;
         this.previewFormOptions.clear();
         if (this.previousScreen == null) {
            ForgeClientEvents.markCharacterCreatedLocally();
            NetworkHandler.sendToServer(new UpdateCharacterC2S(this.character));
         } else {
            ForgeClientEvents.markCharacterCreatedLocally();
            NetworkHandler.sendToServer(new CreateCharacterC2S(this.character));
         }

         this.minecraft.setScreen(null);
      }
   }

   private void initializeDefaultColors() {
      RaceCharacterConfig config = ConfigManager.getRaceCharacter(this.character.getRace());
      if (config != null) {
         boolean hasChanges = false;
         if (this.character.getBodyColor() == null || this.character.getBodyColor().isEmpty()) {
            this.character.setBodyColor(config.getDefaultBodyColor());
            hasChanges = true;
         }

         if (this.character.getBodyColor2() == null || this.character.getBodyColor2().isEmpty()) {
            this.character.setBodyColor2(config.getDefaultBodyColor2());
            hasChanges = true;
         }

         if (this.character.getBodyColor3() == null || this.character.getBodyColor3().isEmpty()) {
            this.character.setBodyColor3(config.getDefaultBodyColor3());
            hasChanges = true;
         }

         if (this.character.getHairColor() == null || this.character.getHairColor().isEmpty()) {
            this.character.setHairColor(config.getDefaultHairColor());
            hasChanges = true;
         }

         if (this.character.getEye1Color() == null || this.character.getEye1Color().isEmpty()) {
            this.character.setEye1Color(config.getDefaultEye1Color());
            hasChanges = true;
         }

         if (this.character.getEye2Color() == null || this.character.getEye2Color().isEmpty()) {
            this.character.setEye2Color(config.getDefaultEye2Color());
            hasChanges = true;
         }

         if (this.character.getAuraColor() == null || this.character.getAuraColor().isEmpty()) {
            this.character.setAuraColor(config.getDefaultAuraColor());
            hasChanges = true;
         }

         if (hasChanges) {
            this.syncCharacter();
         }
      }
   }

   private void resolveClassIndex() {
      RaceStatsConfig statsConfig = ConfigManager.getRaceStats(this.character.getRace());
      if (statsConfig != null) {
         List<String> classes = new ArrayList<>(statsConfig.getAllClasses());
         if (!classes.isEmpty()) {
            if (this.character.getCharacterClass() != null && !this.character.getCharacterClass().isEmpty()) {
               int idx = classes.indexOf(this.character.getCharacterClass());
               this.currentClassIndex = idx >= 0 ? idx : 0;
            } else {
               this.character.setCharacterClass(classes.get(0));
               this.currentClassIndex = 0;
            }
         }
      }
   }

   private String[] getRaceClasses() {
      RaceStatsConfig statsConfig = ConfigManager.getRaceStats(this.character.getRace());
      return statsConfig == null ? new String[0] : statsConfig.getAllClasses().toArray(new String[0]);
   }

   private int getCombinedBodyTypeCount() {
      int maleCount = Math.max(0, TextureCounter.getMaxBodyTypes(this.getEffectiveModelBase(), "male")) + 1;
      int femaleCount = this.character.canHaveGender() ? Math.max(0, TextureCounter.getMaxBodyTypes(this.getEffectiveModelBase(), "female")) + 1 : 0;
      return maleCount + femaleCount - 1;
   }

   private int getCurrentCombinedBodyTypeValue() {
      int maleCount = Math.max(0, TextureCounter.getMaxBodyTypes(this.getEffectiveModelBase(), "male")) + 1;
      return this.character.getGender().equals("female") ? this.character.getBodyType() + maleCount : this.character.getBodyType();
   }

   private int getMaxHairForCurrentState() {
      boolean supportsHair = HairManager.canUseHair(this.character);
      int count = supportsHair ? HairManager.getPresetCount() : 0;
      List<String> extraBones = this.getAvailableExtraHeadBonesForCurrentState();
      count += extraBones.size();
      return count + this.headBoneEmptyCount();
   }

   private boolean hasEmptyHeadBoneOption() {
      return ConfigManager.getRaceCharacter(this.character.getRace()) != null
         && ConfigManager.getRaceCharacter(this.character.getRace()).getHeadBones().length >= 1;
   }

   private int headBoneEmptyCount() {
      return this.hasEmptyHeadBoneOption() ? 1 : 0;
   }

   private String headBoneCategory(String bone) {
      if (bone == null) {
         return "";
      } else {
         int i = bone.length();

         while (i > 0 && java.lang.Character.isDigit(bone.charAt(i - 1))) {
            i--;
         }

         return bone.substring(0, i);
      }
   }

   private Set<String> parseActiveBoneTokens() {
      Set<String> tokens = new LinkedHashSet<>();
      String bone = this.character.getActiveHeadBone();
      if (bone != null && !bone.isEmpty()) {
         for (String token : bone.split("\\+")) {
            if (!token.isEmpty()) {
               tokens.add(token);
            }
         }

         return tokens;
      } else {
         return tokens;
      }
   }

   private Set<Integer> getSelectedHeadBoneValues() {
      Set<Integer> selected = new HashSet<>();
      int emptyCount = this.headBoneEmptyCount();
      List<String> extraBones = this.getAvailableExtraHeadBonesForCurrentState();
      boolean supportsHair = HairManager.canUseHair(this.character);
      int hairPresets = supportsHair ? HairManager.getPresetCount() : 0;
      Set<String> tokens = this.parseActiveBoneTokens();
      boolean anySelected = false;

      for (int i = 0; i < extraBones.size(); i++) {
         if (tokens.contains(extraBones.get(i))) {
            selected.add(emptyCount + i);
            anySelected = true;
         }
      }

      if (supportsHair && tokens.contains("hair") && this.character.getHairId() > 0) {
         int style = this.character.getHairId() - 1;
         if (style >= 0 && style < hairPresets) {
            selected.add(emptyCount + extraBones.size() + style);
            anySelected = true;
         }
      }

      if (this.hasEmptyHeadBoneOption() && !anySelected) {
         selected.add(0);
      }

      return selected;
   }

   private List<String> getAvailableExtraHeadBonesForCurrentState() {
      RaceCharacterConfig config = ConfigManager.getRaceCharacter(this.character.getRace());
      if (config != null && config.getHeadBones() != null && this.character.areExtraHeadBonesEnabled()) {
         List<String> extraBones = new ArrayList<>();

         for (String bone : config.getHeadBones()) {
            if (bone != null && !bone.isEmpty() && !bone.equals("hair")) {
               extraBones.add(bone);
            }
         }

         return extraBones;
      } else {
         return Collections.emptyList();
      }
   }

   private void syncCharacter() {
      NetworkHandler.sendToServer(new StatsSyncC2S(this.character));
   }

   private void setBodyTypeFromPreview(int value) {
      int maleCount = Math.max(0, TextureCounter.getMaxBodyTypes(this.getEffectiveModelBase(), "male")) + 1;
      String newGender = "male";
      int newBodyType = value;
      if (this.character.canHaveGender() && value >= maleCount) {
         newGender = "female";
         newBodyType = value - maleCount;
      }

      if (!this.character.getGender().equals(newGender) || this.character.getBodyType() != newBodyType) {
         this.character.setGender(newGender);
         this.character.setBodyType(newBodyType);
         if (this.getEffectiveModelBase().equals("majin") && this.character.getHairId() != 0) {
            this.character.setHairId(0);
         }

         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private void setHairFromPreview(int value) {
      int emptyCount = this.headBoneEmptyCount();
      List<String> extraBones = this.getAvailableExtraHeadBonesForCurrentState();
      boolean supportsHair = HairManager.canUseHair(this.character);
      if (this.hasEmptyHeadBoneOption() && value == 0) {
         this.character.setHairId(0);
         this.character.setActiveHeadBone("");
         this.character.setHairBase(new CustomHair());
         this.character.setHairSSJ(new CustomHair());
         this.character.setHairSSJ2(new CustomHair());
         this.character.setHairSSJ3(new CustomHair());
         this.syncCharacter();
         this.refreshScreenWidgets();
      } else {
         Set<String> tokens = this.parseActiveBoneTokens();
         int local = value - emptyCount;
         if (local < extraBones.size()) {
            String bone = extraBones.get(local);
            String category = this.headBoneCategory(bone);
            boolean wasSelected = tokens.contains(bone);
            tokens.removeIf(token -> !token.equals("hair") && this.headBoneCategory(token).equals(category));
            if (!wasSelected) {
               tokens.add(bone);
            }
         } else if (supportsHair) {
            int style = local - extraBones.size();
            int newHairId = style + 1;
            boolean wasSelected = tokens.contains("hair") && this.character.getHairId() == newHairId;
            if (wasSelected) {
               tokens.remove("hair");
               this.character.setHairId(0);
            } else {
               tokens.add("hair");
               this.character.setHairId(newHairId);
               this.character.setHairBase(new CustomHair());
               this.character.setHairSSJ(new CustomHair());
               this.character.setHairSSJ2(new CustomHair());
               this.character.setHairSSJ3(new CustomHair());
            }
         }

         this.character.setActiveHeadBone(String.join("+", tokens));
         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private void setEyesFromPreview(int value) {
      if (this.character.getEyesType() != value) {
         this.character.setEyesType(value);
         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private void setNoseFromPreview(int value) {
      if (this.character.getNoseType() != value) {
         this.character.setNoseType(value);
         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private void setMouthFromPreview(int value) {
      if (this.character.getMouthType() != value) {
         this.character.setMouthType(value);
         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private void setTattooFromPreview(int value) {
      if (this.character.getTattooType() != value) {
         this.character.setTattooType(value);
         this.syncCharacter();
         this.refreshScreenWidgets();
      }
   }

   private String getEffectiveModelBase() {
      String race = this.character.getRace().toLowerCase(Locale.ROOT);
      RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
      return config != null && config.hasCustomModel() ? config.getCustomModel().toLowerCase(Locale.ROOT) : race;
   }

   private CustomTextureButton createArrowButton(int x, int y, boolean isLeft, OnPress onPress) {
      return new CustomTextureButton.Builder()
         .position(x, y)
         .size(10, 15)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(isLeft ? 32 : 20, 0, isLeft ? 32 : 20, 14)
         .textureSize(8, 14)
         .message(Component.empty())
         .onPress(onPress)
         .build();
   }

   private TexturedTextButton createColorButton(int x, int y, String fieldName) {
      String currentColor = this.getColorFromField(fieldName);
      if (currentColor.isEmpty()) {
         currentColor = "#FFFFFF";
      }

      int colorInt = ColorUtils.hexToInt(currentColor);
      TexturedTextButton btn = new TexturedTextButton.Builder()
         .position(x, y)
         .size(20, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(42, 15, 42, 15)
         .textureSize(5, 5)
         .message(Component.empty())
         .backgroundColor(colorInt)
         .onPress(b -> this.showColorPicker(fieldName))
         .build();
      this.colorButtons.put(fieldName, btn);
      return btn;
   }

   private void onHexFieldChange(String hex) {
      if (!this.isUpdatingFromCode) {
         if (hex.startsWith("#")) {
            hex = hex.substring(1);
         }

         if (hex.length() == 6) {
            this.isUpdatingFromCode = true;

            try {
               float[] hsv = ColorUtils.hexToHsv("#" + hex);
               if (this.hueSlider != null) {
                  this.hueSlider.setValue((int)hsv[0]);
               }

               if (this.saturationSlider != null) {
                  int satValue = (int)hsv[1];
                  this.saturationSlider.setValue(satValue == 0 ? 100 : satValue);
                  this.saturationSlider.setCurrentHue(hsv[0]);
               }

               if (this.valueSlider != null) {
                  int valValue = (int)hsv[2];
                  this.valueSlider.setValue(valValue == 0 ? 100 : valValue);
                  this.valueSlider.setCurrentHue(hsv[0]);
                  this.valueSlider.setCurrentSaturation(hsv[1] == 0.0F ? 100.0F : hsv[1]);
               }

               this.applyColor("#" + hex);
            } catch (Exception var4) {
            }

            this.isUpdatingFromCode = false;
         }
      }
   }

   private void showColorPicker(String fieldName) {
      this.currentColorField = fieldName;
      this.colorPickerVisible = true;
      String currentColor = this.getColorFromField(fieldName);
      float[] hsv = ColorUtils.hexToHsv(currentColor);
      if (this.hueSlider != null) {
         this.hueSlider.setValue((int)hsv[0]);
      }

      if (this.saturationSlider != null) {
         int satValue = (int)hsv[1];
         this.saturationSlider.setValue(satValue == 0 ? 100 : satValue);
         this.saturationSlider.setCurrentHue(hsv[0]);
      }

      if (this.valueSlider != null) {
         int valValue = (int)hsv[2];
         this.valueSlider.setValue(valValue == 0 ? 100 : valValue);
         this.valueSlider.setCurrentHue(hsv[0]);
         this.valueSlider.setCurrentSaturation(hsv[1] == 0.0F ? 100.0F : hsv[1]);
      }

      this.isUpdatingFromCode = true;
      if (this.hexColorField != null) {
         this.hexColorField.setValue(currentColor);
      }

      this.isUpdatingFromCode = false;
      this.setSlidersVisible();
   }

   private void hideColorPicker() {
      this.colorPickerVisible = false;
      this.currentColorField = "";
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

      if (this.hexColorField != null) {
         this.hexColorField.visible = this.colorPickerVisible;
      }
   }

   private void updateColorFromSliders() {
      if (this.colorPickerVisible && !this.currentColorField.isEmpty()) {
         float h = (float)this.hueSlider.getValue();
         float s = (float)this.saturationSlider.getValue();
         float v = (float)this.valueSlider.getValue();
         this.saturationSlider.setCurrentHue(h);
         this.valueSlider.setCurrentHue(h);
         this.valueSlider.setCurrentSaturation(s);
         String newColor = ColorUtils.hsvToHex(h, s, v);
         this.isUpdatingFromCode = true;
         if (this.hexColorField != null && !this.hexColorField.isFocused()) {
            this.hexColorField.setValue(newColor);
         }

         this.isUpdatingFromCode = false;
         this.applyColor(newColor);
      }
   }

   private String getColorFromField(String fieldName) {
      String color = switch (fieldName) {
         case "hairColor" -> this.character.getHairColor();
         case "bodyColor" -> this.character.getBodyColor();
         case "bodyColor2" -> this.character.getBodyColor2();
         case "bodyColor3" -> this.character.getBodyColor3();
         case "eye1Color" -> this.character.getEye1Color();
         case "eye2Color" -> this.character.getEye2Color();
         case "auraColor" -> this.character.getAuraColor();
         default -> null;
      };
      return color != null && !color.isEmpty() ? color : "#FFFFFF";
   }

   private void applyColor(String color) {
      String btn = this.currentColorField;
      switch (btn) {
         case "hairColor":
            this.character.setHairColor(color);
            break;
         case "bodyColor":
            this.character.setBodyColor(color);
            break;
         case "bodyColor2":
            this.character.setBodyColor2(color);
            break;
         case "bodyColor3":
            this.character.setBodyColor3(color);
            break;
         case "eye1Color":
            this.character.setEye1Color(color);
            break;
         case "eye2Color":
            this.character.setEye2Color(color);
            break;
         case "auraColor":
            this.character.setAuraColor(color);
      }

      TexturedTextButton btn = this.colorButtons.get(this.currentColorField);
      if (btn != null) {
         btn.setBackgroundColor(ColorUtils.hexToInt(color));
      }

      this.syncCharacter();
   }

   private void renderColorPickerBackground(GuiGraphics graphics) {
      PoseStack poseStack = graphics.pose();
      poseStack.pushPose();
      poseStack.translate(0.0, 0.0, 200.0);
      int sliderX = 161;
      int sliderY = this.getUiHeight() / 2 - 40;
      graphics.fill(sliderX - 5, sliderY - 5, sliderX + 126, sliderY + 56, -2013265920);
      poseStack.popPose();
   }

   private void renderColorPreviewSquare(GuiGraphics graphics) {
      if (this.hueSlider != null) {
         PoseStack poseStack = graphics.pose();
         poseStack.pushPose();
         poseStack.translate(0.0, 0.0, 200.0);
         int sliderX = 161;
         int sliderY = this.getUiHeight() / 2 - 40;
         int previewX = sliderX + 85;
         int previewSize = 34;
         float h = (float)this.hueSlider.getValue();
         float s = (float)this.saturationSlider.getValue();
         float v = (float)this.valueSlider.getValue();
         int[] rgb = ColorUtils.hsvToRgb(h, s, v);
         int color = ColorUtils.rgbToInt(rgb[0], rgb[1], rgb[2]);
         graphics.fill(previewX - 1, sliderY - 1, previewX + previewSize + 1, sliderY + previewSize + 1, -1);
         graphics.fill(previewX, sliderY, previewX + previewSize, sliderY + previewSize, 0xFF000000 | color);
         poseStack.popPose();
      }
   }

   protected void renderPanorama(GuiGraphics graphics, float partialTick) {
      String currentRace = this.character.getRace() != null ? this.character.getRace().toLowerCase(Locale.ROOT) : "human";
      PanoramaRenderer panorama = this.getPanorama(currentRace);
      panorama.render(graphics, this.width, this.height, 1.0F, partialTick);
   }

   private void renderCinematicBars(GuiGraphics guiGraphics) {
      int totalBarHeight = (int)((double)this.height * 0.12);
      int fadeSize = 60;
      if (totalBarHeight <= fadeSize) {
         totalBarHeight = fadeSize + 1;
      }

      int solidHeight = totalBarHeight - fadeSize;
      int colorSolid = -16777216;
      int colorTransparent = 0;
      guiGraphics.fill(0, 0, this.width, solidHeight, colorSolid);
      guiGraphics.fillGradient(0, solidHeight, this.width, solidHeight + fadeSize, colorSolid, colorTransparent);
      int bottomBarStartY = this.height - totalBarHeight;
      guiGraphics.fillGradient(0, bottomBarStartY, this.width, bottomBarStartY + fadeSize, colorTransparent, colorSolid);
      guiGraphics.fill(0, bottomBarStartY + fadeSize, this.width, this.height, colorSolid);
   }

   protected int getAdjustedModelScale(LivingEntity player, int baseScale) {
      float currentVisualScale = this.getCurrentVisualModelScale(player);
      if (currentVisualScale <= 0.9375F) {
         return baseScale;
      } else {
         float normalization = 0.9375F / currentVisualScale;
         return Math.max(1, Math.round((float)baseScale * normalization));
      }
   }

   private int getNormalizedPreviewScale(LivingEntity player, int baseScale) {
      float currentVisualScale = this.getCurrentVisualModelScale(player);
      if (currentVisualScale <= 0.0F) {
         return baseScale;
      } else {
         float normalization = 0.9375F / currentVisualScale;
         return Math.max(1, Math.round((float)baseScale * normalization));
      }
   }

   private float getCurrentVisualModelScale(LivingEntity player) {
      float[] currentVisualScale = new float[]{0.9375F};
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
         Character localCharacter = stats.getCharacter();
         Float[] resolved = localCharacter.getResolvedModelScaling();
         float scaleX = this.getSafeScaleValue(resolved, 0, 0.9375F);
         float scaleY = this.getSafeScaleValue(resolved, 1, 0.9375F);
         currentVisualScale[0] = Math.max(0.1F, (scaleX + scaleY) / 2.0F);
      });
      return currentVisualScale[0];
   }

   private float getSafeScaleValue(Float[] scalingValues, int index, float fallback) {
      return scalingValues != null && index >= 0 && index < scalingValues.length && scalingValues[index] != null && !(scalingValues[index] <= 0.0F)
         ? scalingValues[index]
         : fallback;
   }

   private void reloadPreviewFormOptions() {
      String previousGroup = null;
      String previousForm = null;
      if (this.previewFormIndex >= 0 && this.previewFormIndex < this.previewFormOptions.size()) {
         CharacterCustomizationScreen.PreviewFormOption previous = this.previewFormOptions.get(this.previewFormIndex);
         previousGroup = previous.groupName;
         previousForm = previous.formName;
      }

      this.previewFormOptions.clear();
      this.previewFormIndex = -1;
      this.previewFormOptions.add(new CharacterCustomizationScreen.PreviewFormOption("", ""));

      for (TransformationsHelper.OrderedFormEntry entry : TransformationsHelper.getOrderedFormsForRace(this.character.getRace(), PREVIEW_FORM_TYPE_ORDER)) {
         if (entry != null && entry.getFormData() != null) {
            this.previewFormOptions.add(new CharacterCustomizationScreen.PreviewFormOption(entry.getGroupName(), entry.getFormData().getName()));
         }
      }

      if (previousGroup != null && previousForm != null) {
         for (int i = 0; i < this.previewFormOptions.size(); i++) {
            CharacterCustomizationScreen.PreviewFormOption option = this.previewFormOptions.get(i);
            if (option.groupName.equalsIgnoreCase(previousGroup) && option.formName.equalsIgnoreCase(previousForm)) {
               this.previewFormIndex = i;
               return;
            }
         }
      }

      this.previewFormIndex = this.previewFormOptions.isEmpty() ? -1 : 0;
   }

   private CharacterCustomizationScreen.ActiveFormSnapshot captureLocalPlayerFormSnapshot(LivingEntity player) {
      CharacterCustomizationScreen.ActiveFormSnapshot[] snapshot = new CharacterCustomizationScreen.ActiveFormSnapshot[1];
      StatsProvider.get(StatsCapability.INSTANCE, player)
         .ifPresent(
            stats -> {
               Character currentCharacter = stats.getCharacter();
               snapshot[0] = new CharacterCustomizationScreen.ActiveFormSnapshot(
                  currentCharacter.getActiveFormGroup(),
                  currentCharacter.getActiveForm(),
                  currentCharacter.getActiveStackFormGroup(),
                  currentCharacter.getActiveStackForm(),
                  stats.getStatus().isAndroidUpgraded()
               );
            }
         );
      return snapshot[0];
   }

   private boolean applyPreviewTransformationToPlayer(LivingEntity player) {
      if (this.previewFormIndex >= 0 && this.previewFormIndex < this.previewFormOptions.size()) {
         CharacterCustomizationScreen.PreviewFormOption option = this.previewFormOptions.get(this.previewFormIndex);
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            Character currentCharacter = stats.getCharacter();
            currentCharacter.clearActiveForm();
            currentCharacter.clearActiveStackForm();
            if (option.groupName != null && !option.groupName.isEmpty() && option.formName != null && !option.formName.isEmpty()) {
               if (ConfigManager.getStackFormGroup(option.groupName) != null) {
                  currentCharacter.setActiveStackForm(option.groupName, option.formName);
               } else {
                  currentCharacter.setActiveForm(option.groupName, option.formName);
                  if ("androidforms".equalsIgnoreCase(option.groupName)) {
                     stats.getStatus().setAndroidUpgraded(true);
                  }
               }
            }
         });
         return true;
      } else {
         return false;
      }
   }

   private void restoreLocalPlayerFormSnapshot(LivingEntity player, CharacterCustomizationScreen.ActiveFormSnapshot snapshot) {
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
         Character currentCharacter = stats.getCharacter();
         currentCharacter.setActiveForm(snapshot.activeFormGroup, snapshot.activeForm);
         currentCharacter.setActiveStackForm(snapshot.activeStackFormGroup, snapshot.activeStackForm);
         stats.getStatus().setAndroidUpgraded(snapshot.androidUpgraded);
      });
   }

   private void clearLocalPlayerTransformState() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(stats -> this.clearAllTransformSelections(stats.getCharacter()));
      }
   }

   private void clearAllTransformSelections(Character targetCharacter) {
      targetCharacter.clearActiveForm();
      targetCharacter.clearActiveStackForm();
      targetCharacter.setSelectedFormGroup("");
      targetCharacter.setSelectedForm("");
      targetCharacter.setSelectedStackFormGroup("");
      targetCharacter.setSelectedStackForm("");
   }

   private boolean handlePreviewGridClick(double uiMouseX, double uiMouseY) {
      int top = this.getUiHeight() / 2 - 106 + 12;
      CharacterCustomizationScreen.TabId tab = this.activeTabs.get(this.currentTabIndex);

      return switch (tab) {
         case PRESET -> this.handlePreviewGridSelection(
         uiMouseX, uiMouseY, top + 40, 0, this.getCombinedBodyTypeCount(), 3, this.bodyTypePreviewScrollRows, this::setBodyTypeFromPreview
      );
         case HAIR -> this.handlePreviewGridSelection(
         uiMouseX, uiMouseY, top + 30, 0, Math.max(0, this.getMaxHairForCurrentState() - 1), 3, this.hairPreviewScrollRows, this::setHairFromPreview
      );
         case EYES -> this.handlePreviewGridSelection(
         uiMouseX,
         uiMouseY,
         top + 30,
         0,
         Math.max(1, TextureCounter.getMaxEyesTypes(this.getEffectiveModelBase())),
         3,
         this.eyesPreviewScrollRows,
         this::setEyesFromPreview
      );
         case FACE -> this.handlePreviewGridSelection(
            uiMouseX,
            uiMouseY,
            top + 20,
            0,
            Math.max(1, TextureCounter.getMaxNoseTypes(this.getEffectiveModelBase())),
            1,
            this.nosePreviewScrollRows,
            this::setNoseFromPreview
         )
         || this.handlePreviewGridSelection(
            uiMouseX,
            uiMouseY,
            top + 94,
            0,
            Math.max(1, TextureCounter.getMaxMouthTypes(this.getEffectiveModelBase())),
            2,
            this.mouthPreviewScrollRows,
            this::setMouthFromPreview
         );
         case BODY -> this.handlePreviewGridSelection(
         uiMouseX,
         uiMouseY,
         top + 30,
         0,
         Math.max(1, TextureCounter.getMaxTattooTypes(this.getEffectiveModelBase())),
         3,
         this.tattooPreviewScrollRows,
         this::setTattooFromPreview
      );
         default -> false;
      };
   }

   private void changePreviewTransformation(int delta) {
      if (this.previewFormOptions.isEmpty()) {
         this.previewFormIndex = -1;
      } else {
         int nextIndex = this.previewFormIndex + delta;
         if (nextIndex < 0) {
            nextIndex = this.previewFormOptions.size() - 1;
         }

         if (nextIndex >= this.previewFormOptions.size()) {
            nextIndex = 0;
         }

         this.previewFormIndex = nextIndex;
      }
   }

   private String getCurrentPreviewTransformationName() {
      if (this.previewFormIndex >= 0 && this.previewFormIndex < this.previewFormOptions.size()) {
         CharacterCustomizationScreen.PreviewFormOption option = this.previewFormOptions.get(this.previewFormIndex);
         String rawName = option.formName != null ? option.formName : "";
         if (rawName.isEmpty()) {
            return this.tr("forms.dragonminez.base", new Object[0]).getString();
         } else {
            String raceName = this.character.getRace() != null ? this.character.getRace().toLowerCase(Locale.ROOT) : "human";
            String translationKey = "race.dragonminez." + raceName + ".form." + option.groupName + "." + rawName;
            return I18n.exists(translationKey) ? this.tr(translationKey, new Object[0]).getString() : this.formatPreviewFormName(rawName);
         }
      } else {
         return this.tr("forms.dragonminez.base", new Object[0]).getString();
      }
   }

   private String formatPreviewFormName(String rawName) {
      String[] parts = rawName.replace('-', ' ').replace('_', ' ').split("\\s+");
      StringBuilder builder = new StringBuilder();

      for (String part : parts) {
         if (part != null && !part.isEmpty()) {
            if (!builder.isEmpty()) {
               builder.append(' ');
            }

            builder.append(java.lang.Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
         }
      }

      return builder.isEmpty() ? rawName : builder.toString();
   }

   private boolean handlePreviewGridSelection(
      double uiMouseX, double uiMouseY, int startY, int minValue, int maxValue, int visibleRows, int scrollRows, IntConsumer onSelect
   ) {
      if (maxValue < minValue) {
         return false;
      } else {
         int total = maxValue - minValue + 1;
         int startX = 26;
         int firstIndex = Mth.clamp(scrollRows, 0, this.getMaxScrollRows(minValue, maxValue, visibleRows)) * 3;
         int visible = Math.min(3 * visibleRows, Math.max(0, total - firstIndex));

         for (int i = 0; i < visible; i++) {
            int value = minValue + firstIndex + i;
            int col = i % 3;
            int row = i / 3;
            int cardX = startX + col * 39;
            int cardY = startY + row * 49;
            if (uiMouseX >= (double)cardX && uiMouseX <= (double)(cardX + 34) && uiMouseY >= (double)cardY && uiMouseY <= (double)(cardY + 44)) {
               onSelect.accept(value);
               return true;
            }
         }

         return false;
      }
   }

   private void renderPreviewGrid(
      GuiGraphics graphics,
      ScrollbarState bar,
      int startY,
      int minValue,
      int maxValue,
      int selectedValue,
      CharacterCustomizationScreen.PreviewRenderMode mode,
      boolean headZoom,
      int visibleRows,
      int scrollRows
   ) {
      this.renderPreviewGrid(graphics, bar, startY, minValue, maxValue, v -> v == selectedValue, mode, headZoom, visibleRows, scrollRows);
   }

   private void renderPreviewGrid(
      GuiGraphics graphics,
      ScrollbarState bar,
      int startY,
      int minValue,
      int maxValue,
      IntPredicate selectedPredicate,
      CharacterCustomizationScreen.PreviewRenderMode mode,
      boolean headZoom,
      int visibleRows,
      int scrollRows
   ) {
      if (maxValue >= minValue) {
         int total = maxValue - minValue + 1;
         int startX = 26;
         int firstIndex = Mth.clamp(scrollRows, 0, this.getMaxScrollRows(minValue, maxValue, visibleRows)) * 3;
         int visible = Math.min(3 * visibleRows, Math.max(0, total - firstIndex));

         for (int i = 0; i < visible; i++) {
            int value = minValue + firstIndex + i;
            int col = i % 3;
            int row = i / 3;
            int cardX = startX + col * 39;
            int cardY = startY + row * 49;
            boolean selected = selectedPredicate.test(value);
            int borderColor = selected ? -1519455 : -14013910;
            graphics.fill(cardX - 1, cardY - 1, cardX + 34 + 1, cardY + 44 + 1, borderColor);
            graphics.fill(cardX, cardY, cardX + 34, cardY + 44, -1441722095);
            int previewScale = headZoom ? 42 : 23;
            int previewY = headZoom ? cardY + 44 + 12 : cardY + 44 - 2;
            this.renderPreviewModelVariant(graphics, cardX, cardY, cardX + 17, previewY, previewScale, headZoom, mode, value);
         }

         int maxScrollRows = this.getMaxScrollRows(minValue, maxValue, visibleRows);
         int gridHeight = visibleRows * 44 + (visibleRows - 1) * 5;
         int scrollbarX = startX + 117 - 4;
         int scrollbarW = 3;
         bar.update(scrollbarX, scrollbarW, startY, gridHeight, (float)maxScrollRows);
         if (maxScrollRows > 0) {
            graphics.fill(scrollbarX, startY, scrollbarX + scrollbarW, startY + gridHeight, -2013265920);
            int thumbHeight = Math.max(8, gridHeight / (maxScrollRows + 1));
            int clampedScrollRows = Mth.clamp(scrollRows, 0, maxScrollRows);
            int thumbY = startY + clampedScrollRows * (gridHeight - thumbHeight) / maxScrollRows;
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbHeight, -1);
         }
      }
   }

   private boolean tryScrollGrid(
      double uiMouseX, double uiMouseY, int startY, int minValue, int maxValue, int visibleRows, int direction, int currentScrollRows
   ) {
      int maxScrollRows = this.getMaxScrollRows(minValue, maxValue, visibleRows);
      if (maxScrollRows <= 0) {
         return false;
      } else if (!this.isPointInsideGrid(uiMouseX, uiMouseY, startY, visibleRows)) {
         return false;
      } else {
         int next = Mth.clamp(currentScrollRows + direction, 0, maxScrollRows);
         return next != currentScrollRows;
      }
   }

   private boolean isPointInsideGrid(double uiMouseX, double uiMouseY, int startY, int visibleRows) {
      int startX = 26;
      int width = 112;
      int height = visibleRows * 44 + (visibleRows - 1) * 5;
      return uiMouseX >= (double)startX && uiMouseX <= (double)(startX + width) && uiMouseY >= (double)startY && uiMouseY <= (double)(startY + height);
   }

   private int getMaxScrollRows(int minValue, int maxValue, int visibleRows) {
      if (maxValue < minValue) {
         return 0;
      } else {
         int totalEntries = maxValue - minValue + 1;
         int totalRows = Mth.ceil((float)totalEntries / 3.0F);
         return Math.max(0, totalRows - visibleRows);
      }
   }

   private int clampScrollRows(int minValue, int maxValue, int visibleRows, int scrollRows) {
      return Mth.clamp(scrollRows, 0, this.getMaxScrollRows(minValue, maxValue, visibleRows));
   }

   private void renderPreviewModelVariant(
      GuiGraphics graphics, int cardX, int cardY, int x, int y, int scale, boolean headZoom, CharacterCustomizationScreen.PreviewRenderMode mode, int value
   ) {
      LivingEntity player = Minecraft.getInstance().player;
      if (player != null) {
         CharacterCustomizationScreen.ActiveFormSnapshot snapshot = this.captureLocalPlayerFormSnapshot(player);
         boolean previewApplied = this.applyPreviewTransformationToPlayer(player);
         int originalBody = this.character.getBodyType();
         int originalHair = this.character.getHairId();
         int originalEyes = this.character.getEyesType();
         int originalNose = this.character.getNoseType();
         int originalMouth = this.character.getMouthType();
         int originalTattoo = this.character.getTattooType();
         String originalActiveBone = this.character.getActiveHeadBone();
         boolean oldHairPhysics = HairRenderer.PHYSICS_ENABLED;
         boolean[] hadTailState = new boolean[]{false};
         boolean[] oldTailVisible = new boolean[]{true};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            hadTailState[0] = true;
            oldTailVisible[0] = stats.getStatus().isTailVisible();
            stats.getStatus().setTailVisible(false);
         });
         String originalGender = this.character.getGender();
         switch (mode) {
            case FULL_BODY:
               int maleCount = Math.max(0, TextureCounter.getMaxBodyTypes(this.getEffectiveModelBase(), "male")) + 1;
               if (this.character.canHaveGender() && value >= maleCount) {
                  this.character.setGender("female");
                  this.character.setBodyType(value - maleCount);
               } else {
                  this.character.setGender("male");
                  this.character.setBodyType(value);
               }
               break;
            case HAIR_ONLY:
               boolean supportsHair = HairManager.canUseHair(this.character);
               int emptyCount = this.headBoneEmptyCount();
               List<String> extraBones = this.getAvailableExtraHeadBonesForCurrentState();
               if (this.hasEmptyHeadBoneOption() && value == 0) {
                  this.character.setHairId(0);
                  this.character.setActiveHeadBone("");
               } else {
                  int local = value - emptyCount;
                  if (local < extraBones.size()) {
                     this.character.setHairId(0);
                     this.character.setActiveHeadBone(extraBones.get(local));
                  } else if (supportsHair) {
                     this.character.setHairId(local - extraBones.size() + 1);
                     this.character.setActiveHeadBone("hair");
                  }
               }

               this.character.setEyesType(0);
               this.character.setNoseType(0);
               this.character.setMouthType(0);
               this.character.setTattooType(0);
               break;
            case EYES_ONLY:
               this.character.setHairId(0);
               this.character.setEyesType(value);
               this.character.setNoseType(0);
               this.character.setMouthType(0);
               break;
            case NOSE_ONLY:
               this.character.setHairId(0);
               this.character.setEyesType(0);
               this.character.setNoseType(value);
               this.character.setMouthType(0);
               break;
            case MOUTH_ONLY:
               this.character.setHairId(0);
               this.character.setEyesType(0);
               this.character.setNoseType(0);
               this.character.setMouthType(value);
               break;
            case TATTOO_ONLY:
               this.character.setTattooType(value);
         }

         HairRenderer.PHYSICS_ENABLED = false;
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(0.0F);
         pose.mul(cameraOrientation);
         float yBodyRotO = player.yBodyRot;
         float yBodyRotOField = player.yBodyRotO;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float xRotOField = player.xRotO;
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         int tickCountO = player.tickCount;
         Vec3 oldDeltaMovement = player.getDeltaMovement();
         float previewYaw = 180.0F;
         player.yBodyRot = previewYaw;
         player.yBodyRotO = previewYaw;
         player.setYRot(previewYaw);
         float previewPitch = headZoom ? 18.0F : 8.0F;
         player.setXRot(previewPitch);
         player.xRotO = previewPitch;
         player.yHeadRot = previewYaw;
         player.yHeadRotO = previewYaw;
         player.tickCount = 0;
         player.setDeltaMovement(0.0, 0.0, 0.0);
         player.walkAnimation.setSpeed(0.0F);
         player.walkAnimation.position(0.0F);
         player.walkDist = 0.0F;
         player.walkDistO = 0.0F;

         int previewScale = switch (mode) {
            case HAIR_ONLY -> Math.max(26, scale - 6);
            case EYES_ONLY, NOSE_ONLY, MOUTH_ONLY -> scale + 16;
            default -> scale;
         };
         if (mode != CharacterCustomizationScreen.PreviewRenderMode.EYES_ONLY
            && mode != CharacterCustomizationScreen.PreviewRenderMode.NOSE_ONLY
            && mode != CharacterCustomizationScreen.PreviewRenderMode.MOUTH_ONLY) {
            previewScale = this.getAdjustedModelScale(player, previewScale);
         } else {
            previewScale = this.getNormalizedPreviewScale(player, previewScale);
         }

         int previewY = 0;

         previewY = switch (mode) {
            case HAIR_ONLY -> {
               if (ConfigManager.getRaceCharacter(this.character.getRace()) != null) {
                  if (Arrays.stream(ConfigManager.getRaceCharacter(this.character.getRace()).getHeadBones()).toList().contains("hair")) {
                     yield y + 36;
                  } else {
                     yield y + 28;
                  }
               } else {
                  yield y + 36;
               }
            }
            case EYES_ONLY, NOSE_ONLY, MOUTH_ONLY -> y + 58;
            default -> y + 8;
         };

         try {
            graphics.enableScissor(
               this.toScreenCoord((double)(cardX + 1)),
               this.toScreenCoord((double)(cardY + 1)),
               this.toScreenCoord((double)(cardX + 34 - 1)),
               this.toScreenCoord((double)(cardY + 44 - 1))
            );
            graphics.pose().pushPose();
            graphics.pose().translate(0.0, 0.0, 320.0);
            DMZSkinLayer.PREVIEW_MODE = previewApplied;

            try {
               EntityPreviewRenderContext.renderEntityInInventory(
                  graphics, x, previewY, previewScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player
               );
            } finally {
               DMZSkinLayer.PREVIEW_MODE = false;
               graphics.pose().popPose();
               graphics.disableScissor();
            }
         } finally {
            if (previewApplied && snapshot != null) {
               this.restoreLocalPlayerFormSnapshot(player, snapshot);
            }

            player.yBodyRot = yBodyRotO;
            player.yBodyRotO = yBodyRotOField;
            player.setYRot(yRotO);
            player.setXRot(xRotO);
            player.xRotO = xRotOField;
            player.yHeadRotO = yHeadRotO;
            player.yHeadRot = yHeadRot;
            player.tickCount = tickCountO;
            player.setDeltaMovement(oldDeltaMovement);
            if (hadTailState[0]) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> stats.getStatus().setTailVisible(oldTailVisible[0]));
            }

            this.character.setGender(originalGender);
            this.character.setBodyType(originalBody);
            this.character.setHairId(originalHair);
            this.character.setEyesType(originalEyes);
            this.character.setNoseType(originalNose);
            this.character.setMouthType(originalMouth);
            this.character.setTattooType(originalTattoo);
            this.character.setActiveHeadBone(originalActiveBone);
            HairRenderer.PHYSICS_ENABLED = oldHairPhysics;
         }
      }
   }

   private static final class ActiveFormSnapshot {
      private final String activeFormGroup;
      private final String activeForm;
      private final String activeStackFormGroup;
      private final String activeStackForm;
      private final boolean androidUpgraded;

      private ActiveFormSnapshot(String activeFormGroup, String activeForm, String activeStackFormGroup, String activeStackForm, boolean androidUpgraded) {
         this.activeFormGroup = activeFormGroup;
         this.activeForm = activeForm;
         this.activeStackFormGroup = activeStackFormGroup;
         this.activeStackForm = activeStackForm;
         this.androidUpgraded = androidUpgraded;
      }
   }

   private static final class PreviewFormOption {
      private final String groupName;
      private final String formName;

      private PreviewFormOption(String groupName, String formName) {
         this.groupName = groupName;
         this.formName = formName;
      }
   }

   private static enum PreviewRenderMode {
      FULL_BODY,
      HAIR_ONLY,
      EYES_ONLY,
      NOSE_ONLY,
      MOUTH_ONLY,
      TATTOO_ONLY;
   }

   private static enum TabId {
      PRESET,
      HAIR,
      EYES,
      FACE,
      BODY,
      AURA_CLASS;
   }
}
