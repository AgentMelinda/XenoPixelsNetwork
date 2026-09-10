package com.dragonminez.client.gui.buttons;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AxisSlider extends AbstractSliderButton {
   private final float minValue;
   private final float maxValue;
   private final float step;
   private final Consumer<Float> onValueChange;
   private final AxisSlider.Axis axis;
   private float lastAppliedValue = Float.NaN;

   public AxisSlider(
      int x, int y, int width, int height, float minValue, float maxValue, float currentValue, AxisSlider.Axis axis, Consumer<Float> onValueChange
   ) {
      this(x, y, width, height, minValue, maxValue, currentValue, 0.0F, axis, onValueChange);
   }

   public AxisSlider(
      int x, int y, int width, int height, float minValue, float maxValue, float currentValue, float step, AxisSlider.Axis axis, Consumer<Float> onValueChange
   ) {
      super(x, y, width, height, Component.empty(), (double)(currentValue - minValue) / (double)(maxValue - minValue));
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.step = step;
      this.onValueChange = onValueChange;
      this.axis = axis;
   }

   protected void updateMessage() {
   }

   protected void applyValue() {
      if (this.step > 0.0F) {
         float snapped = Mth.clamp((float)Math.round(this.getValue() / this.step) * this.step, this.minValue, this.maxValue);
         this.value = (double)(snapped - this.minValue) / (double)(this.maxValue - this.minValue);
      }

      float current = this.getValue();
      if (current != this.lastAppliedValue) {
         this.lastAppliedValue = current;
         if (this.onValueChange != null) {
            this.onValueChange.accept(current);
         }
      }
   }

   public float getValue() {
      return (float)((double)this.minValue + (double)(this.maxValue - this.minValue) * this.value);
   }

   public void setValue(float value) {
      this.value = (double)(value - this.minValue) / (double)(this.maxValue - this.minValue);
   }

   public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, -16777216);
      graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, this.axis.getColor());
      int sliderX = this.getX() + (int)(this.value * (double)(this.width - 6));
      graphics.fill(sliderX, this.getY() - 1, sliderX + 6, this.getY() + this.height + 1, -1);
      graphics.fill(sliderX + 1, this.getY(), sliderX + 5, this.getY() + this.height, -13619152);
   }

   public static enum Axis {
      X(-43691),
      Y(-11141291),
      Z(-11184641);

      private final int color;

      private Axis(int color) {
         this.color = color;
      }

      public int getColor() {
         return this.color;
      }
   }

   public static class Builder {
      private int x;
      private int y;
      private int width;
      private int height;
      private float minValue = -180.0F;
      private float maxValue = 180.0F;
      private float currentValue = 0.0F;
      private float step = 0.0F;
      private AxisSlider.Axis axis = AxisSlider.Axis.X;
      private Consumer<Float> onValueChange;

      public AxisSlider.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public AxisSlider.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public AxisSlider.Builder range(float min, float max) {
         this.minValue = min;
         this.maxValue = max;
         return this;
      }

      public AxisSlider.Builder value(float value) {
         this.currentValue = value;
         return this;
      }

      public AxisSlider.Builder step(float step) {
         this.step = step;
         return this;
      }

      public AxisSlider.Builder axis(AxisSlider.Axis axis) {
         this.axis = axis;
         return this;
      }

      public AxisSlider.Builder onValueChange(Consumer<Float> onValueChange) {
         this.onValueChange = onValueChange;
         return this;
      }

      public AxisSlider build() {
         return new AxisSlider(
            this.x, this.y, this.width, this.height, this.minValue, this.maxValue, this.currentValue, this.step, this.axis, this.onValueChange
         );
      }
   }
}
