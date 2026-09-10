package com.dragonminez.client.gui.buttons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ColorSlider extends AbstractSliderButton {
   private final int minValue;
   private final int maxValue;
   private final Consumer<Integer> onValueChange;
   private final ResourceLocation texture;
   private final int sliderU;
   private final int sliderV;
   private final int sliderWidth;
   private final int sliderHeight;
   private float currentHue = 0.0F;
   private float currentSaturation = 100.0F;

   public ColorSlider(
      int x,
      int y,
      int width,
      int height,
      int minValue,
      int maxValue,
      int currentValue,
      ResourceLocation texture,
      int sliderU,
      int sliderV,
      int sliderWidth,
      int sliderHeight,
      Component message,
      Consumer<Integer> onValueChange
   ) {
      super(x, y, width, height, message, (double)(currentValue - minValue) / (double)(maxValue - minValue));
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.onValueChange = onValueChange;
      this.texture = texture;
      this.sliderU = sliderU;
      this.sliderV = sliderV;
      this.sliderWidth = sliderWidth;
      this.sliderHeight = sliderHeight;
      this.updateMessage();
   }

   public ColorSlider(int x, int y, int width, int height, int minValue, int maxValue, int currentValue, Component message, Consumer<Integer> onValueChange) {
      this(x, y, width, height, minValue, maxValue, currentValue, null, 0, 0, 0, 0, message, onValueChange);
   }

   protected void updateMessage() {
   }

   protected void applyValue() {
      if (this.onValueChange != null) {
         this.onValueChange.accept(this.getValue());
      }
   }

   public int getValue() {
      return (int)Math.round((double)this.minValue + (double)(this.maxValue - this.minValue) * this.value);
   }

   public void setValue(int value) {
      this.value = (double)(value - this.minValue) / (double)(this.maxValue - this.minValue);
   }

   public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      PoseStack poseStack = graphics.pose();
      poseStack.pushPose();
      poseStack.translate(0.0, 0.0, 200.0);
      graphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, -16777216);
      this.drawGradientBackground(graphics);
      int sliderX = this.getX() + (int)(this.value * (double)(this.width - 6));
      graphics.fill(sliderX, this.getY() - 1, sliderX + 6, this.getY() + this.height + 1, -1);
      graphics.fill(sliderX + 1, this.getY(), sliderX + 5, this.getY() + this.height, -8355712);
      poseStack.popPose();
   }

   private void drawGradientBackground(GuiGraphics graphics) {
      Component msg = this.getMessage();
      String messageText = msg.getString();
      if (messageText.equalsIgnoreCase("Hue") || messageText.equalsIgnoreCase("H")) {
         this.drawHueGradient(graphics);
      } else if (messageText.equalsIgnoreCase("Saturation") || messageText.equalsIgnoreCase("S")) {
         this.drawSaturationGradient(graphics);
      } else if (!messageText.equalsIgnoreCase("Value") && !messageText.equalsIgnoreCase("V")) {
         graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, -8355712);
      } else {
         this.drawValueGradient(graphics);
      }
   }

   private void drawHueGradient(GuiGraphics graphics) {
      int segments = this.width;

      for (int i = 0; i < segments; i++) {
         float hue = (float)i / (float)segments * 360.0F;
         int color = this.hsvToRgb(hue, 100.0F, 100.0F);
         graphics.fill(this.getX() + i, this.getY(), this.getX() + i + 1, this.getY() + this.height, 0xFF000000 | color);
      }
   }

   private void drawSaturationGradient(GuiGraphics graphics) {
      int segments = this.width;

      for (int i = 0; i < segments; i++) {
         float saturation = 100.0F - (float)i / (float)segments * 100.0F;
         int color = this.hsvToRgb(this.currentHue, saturation, 100.0F);
         graphics.fill(this.getX() + i, this.getY(), this.getX() + i + 1, this.getY() + this.height, 0xFF000000 | color);
      }
   }

   private void drawValueGradient(GuiGraphics graphics) {
      int segments = this.width;

      for (int i = 0; i < segments; i++) {
         float value = 100.0F - (float)i / (float)segments * 100.0F;
         int color = this.hsvToRgb(this.currentHue, this.currentSaturation, value);
         graphics.fill(this.getX() + i, this.getY(), this.getX() + i + 1, this.getY() + this.height, 0xFF000000 | color);
      }
   }

   private int hsvToRgb(float h, float s, float v) {
      s /= 100.0F;
      v /= 100.0F;
      float c = v * s;
      float x = c * (1.0F - Math.abs(h / 60.0F % 2.0F - 1.0F));
      float m = v - c;
      float r;
      float g;
      float b;
      if (h < 60.0F) {
         r = c;
         g = x;
         b = 0.0F;
      } else if (h < 120.0F) {
         r = x;
         g = c;
         b = 0.0F;
      } else if (h < 180.0F) {
         r = 0.0F;
         g = c;
         b = x;
      } else if (h < 240.0F) {
         r = 0.0F;
         g = x;
         b = c;
      } else if (h < 300.0F) {
         r = x;
         g = 0.0F;
         b = c;
      } else {
         r = c;
         g = 0.0F;
         b = x;
      }

      int ri = (int)((r + m) * 255.0F);
      int gi = (int)((g + m) * 255.0F);
      int bi = (int)((b + m) * 255.0F);
      return ri << 16 | gi << 8 | bi;
   }

   public void setCurrentHue(float hue) {
      this.currentHue = hue;
   }

   public void setCurrentSaturation(float saturation) {
      this.currentSaturation = saturation;
   }

   public static class Builder {
      private int x;
      private int y;
      private int width;
      private int height;
      private int minValue = 0;
      private int maxValue = 255;
      private int currentValue = 0;
      private ResourceLocation texture = null;
      private int sliderU = 0;
      private int sliderV = 0;
      private int sliderWidth = 0;
      private int sliderHeight = 0;
      private Component message = Component.empty();
      private Consumer<Integer> onValueChange;

      public ColorSlider.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public ColorSlider.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public ColorSlider.Builder range(int min, int max) {
         this.minValue = min;
         this.maxValue = max;
         return this;
      }

      public ColorSlider.Builder value(int value) {
         this.currentValue = value;
         return this;
      }

      public ColorSlider.Builder texture(ResourceLocation texture, int u, int v, int width, int height) {
         this.texture = texture;
         this.sliderU = u;
         this.sliderV = v;
         this.sliderWidth = width;
         this.sliderHeight = height;
         return this;
      }

      public ColorSlider.Builder message(Component message) {
         this.message = message;
         return this;
      }

      public ColorSlider.Builder onValueChange(Consumer<Integer> onValueChange) {
         this.onValueChange = onValueChange;
         return this;
      }

      public ColorSlider build() {
         return this.texture != null
            ? new ColorSlider(
               this.x,
               this.y,
               this.width,
               this.height,
               this.minValue,
               this.maxValue,
               this.currentValue,
               this.texture,
               this.sliderU,
               this.sliderV,
               this.sliderWidth,
               this.sliderHeight,
               this.message,
               this.onValueChange
            )
            : new ColorSlider(this.x, this.y, this.width, this.height, this.minValue, this.maxValue, this.currentValue, this.message, this.onValueChange);
      }
   }
}
