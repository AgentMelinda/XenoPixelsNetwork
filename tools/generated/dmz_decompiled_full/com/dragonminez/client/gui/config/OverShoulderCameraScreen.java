package com.dragonminez.client.gui.config;

import com.dragonminez.client.gui.UnblurredScreen;
import com.dragonminez.client.gui.buttons.AxisSlider;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.render.camera.OverShoulderCamera;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralUserConfig;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OverShoulderCameraScreen extends UnblurredScreen {
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final int PANEL_W = 141;
   private static final int PANEL_H = 213;
   private static final int MARGIN = 16;
   private static final int BUTTON_W = 74;
   private static final int SLIDER_W = 111;
   private final Screen parent;
   private CameraType previousCameraType;
   private float panelX;
   private float targetPanelX;
   private int panelY;
   private TexturedTextButton modeButton;
   private TexturedTextButton sideButton;
   private AxisSlider backSlider;
   private AxisSlider upSlider;
   private AxisSlider sideSlider;
   private AxisSlider smoothingSlider;

   public OverShoulderCameraScreen(Screen parent) {
      super(Component.translatable("gui.dragonminez.overShoulder.title"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      if (this.minecraft != null) {
         this.previousCameraType = this.minecraft.options.getCameraType();
         this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      }

      OverShoulderCamera.setPreviewOverride(true);
      this.panelY = (this.height - 213) / 2;
      this.targetPanelX = (float)this.computeTargetPanelX();
      this.panelX = this.targetPanelX;
      GeneralUserConfig config = ConfigManager.getUserConfig();
      this.modeButton = this.buildButton(this.modeLabel(config.getOverShoulderMode()), b -> {
         GeneralUserConfig c = ConfigManager.getUserConfig();
         c.setOverShoulderMode((c.getOverShoulderMode() + 1) % 3);
         this.modeButton.setMessage(this.modeLabel(c.getOverShoulderMode()));
      });
      this.sideButton = this.buildButton(this.sideLabel(config.getOverShoulderLeft()), b -> {
         GeneralUserConfig c = ConfigManager.getUserConfig();
         c.setOverShoulderLeft(!c.getOverShoulderLeft());
         this.sideButton.setMessage(this.sideLabel(c.getOverShoulderLeft()));
         this.targetPanelX = (float)this.computeTargetPanelX();
      });
      this.backSlider = this.buildSlider(1.0F, 6.0F, config.getOverShoulderBack(), AxisSlider.Axis.Z, v -> ConfigManager.getUserConfig().setOverShoulderBack(v));
      this.upSlider = this.buildSlider(-1.0F, 2.0F, config.getOverShoulderUp(), AxisSlider.Axis.Y, v -> ConfigManager.getUserConfig().setOverShoulderUp(v));
      this.sideSlider = this.buildSlider(0.0F, 3.0F, config.getOverShoulderSide(), AxisSlider.Axis.X, v -> ConfigManager.getUserConfig().setOverShoulderSide(v));
      this.smoothingSlider = this.buildSlider(
         0.05F, 0.6F, config.getOverShoulderSmoothing(), AxisSlider.Axis.X, v -> ConfigManager.getUserConfig().setOverShoulderSmoothing(v)
      );
      this.addRenderableWidget(this.modeButton);
      this.addRenderableWidget(this.sideButton);
      this.addRenderableWidget(this.backSlider);
      this.addRenderableWidget(this.upSlider);
      this.addRenderableWidget(this.sideSlider);
      this.addRenderableWidget(this.smoothingSlider);
      this.layoutWidgets();
   }

   private TexturedTextButton buildButton(MutableComponent message, OnPress onPress) {
      return new TexturedTextButton.Builder()
         .position(0, 0)
         .size(74, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(message)
         .onPress(onPress)
         .build();
   }

   private AxisSlider buildSlider(float min, float max, float current, AxisSlider.Axis axis, Consumer<Float> onChange) {
      return new OverShoulderCameraScreen.SnapSlider(0, 0, 111, 10, min, max, current, axis, v -> onChange.accept((float)Math.round(v * 100.0F) / 100.0F));
   }

   private int computeTargetPanelX() {
      boolean left = ConfigManager.getUserConfig().getOverShoulderLeft();
      return left ? 16 : this.width - 141 - 16;
   }

   private void layoutWidgets() {
      int px = Math.round(this.panelX);
      int buttonX = px + 33;
      int sliderX = px + 15;
      this.modeButton.setPosition(buttonX, this.panelY + 40);
      this.sideButton.setPosition(buttonX, this.panelY + 64);
      this.backSlider.setPosition(sliderX, this.panelY + 102);
      this.upSlider.setPosition(sliderX, this.panelY + 130);
      this.sideSlider.setPosition(sliderX, this.panelY + 158);
      this.smoothingSlider.setPosition(sliderX, this.panelY + 186);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.panelX = this.panelX + (this.targetPanelX - this.panelX) * 0.25F;
      if (Math.abs(this.targetPanelX - this.panelX) < 0.5F) {
         this.panelX = this.targetPanelX;
      }

      this.layoutWidgets();
      int px = Math.round(this.panelX);
      graphics.blit(MENU_BIG, px, this.panelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, px + 17, this.panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.overShoulder.title"), px + 70, this.panelY + 15, -10496);
      this.drawSliderLabel(graphics, "gui.dragonminez.overShoulder.distance", this.backSlider.getValue(), px, this.backSlider.getY());
      this.drawSliderLabel(graphics, "gui.dragonminez.overShoulder.height", this.upSlider.getValue(), px, this.upSlider.getY());
      this.drawSliderLabel(graphics, "gui.dragonminez.overShoulder.side", this.sideSlider.getValue(), px, this.sideSlider.getY());
      this.drawSliderLabel(graphics, "gui.dragonminez.overShoulder.smoothing", this.smoothingSlider.getValue(), px, this.smoothingSlider.getY());
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void drawSliderLabel(GuiGraphics graphics, String key, float value, int px, int sliderY) {
      int sliderX = px + 15;
      TextUtil.drawStringWithBorder(graphics, this.font, this.tr(key), sliderX, sliderY - 11, -1);
      MutableComponent valueText = this.txt(String.format("%.2f", value));
      int textW = this.font.width(valueText);
      TextUtil.drawStringWithBorder(graphics, this.font, valueText, sliderX + 111 - textW, sliderY - 11, -8585770);
   }

   private MutableComponent modeLabel(int mode) {
      String key = switch (mode) {
         case 1 -> "gui.dragonminez.overShoulder.mode.lockOn";
         case 2 -> "gui.dragonminez.overShoulder.mode.always";
         default -> "gui.dragonminez.overShoulder.mode.none";
      };
      return this.tr(key);
   }

   private MutableComponent sideLabel(boolean left) {
      return this.tr(left ? "gui.dragonminez.overShoulder.side.left" : "gui.dragonminez.overShoulder.side.right");
   }

   private MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   public void removed() {
      if (this.minecraft != null && this.previousCameraType != null) {
         this.minecraft.options.setCameraType(this.previousCameraType);
      }

      OverShoulderCamera.setPreviewOverride(false);
      ConfigManager.saveGeneralUserConfig();
      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static class SnapSlider extends AxisSlider {
      private SnapSlider(
         int x, int y, int width, int height, float minValue, float maxValue, float currentValue, AxisSlider.Axis axis, Consumer<Float> onValueChange
      ) {
         super(x, y, width, height, minValue, maxValue, currentValue, axis, onValueChange);
      }

      @Override
      protected void applyValue() {
         float step = Screen.hasShiftDown() ? 0.05F : 0.01F;
         float snapped = (float)Math.round(this.getValue() / step) * step;
         this.setValue(snapped);
         super.applyValue();
      }
   }
}
