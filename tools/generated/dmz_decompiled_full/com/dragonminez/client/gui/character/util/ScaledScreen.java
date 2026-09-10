package com.dragonminez.client.gui.character.util;

import com.dragonminez.common.config.ConfigManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ScaledScreen extends Screen {
   private static final float MIN_MENU_SCALE_MULTIPLIER = 0.25F;
   private static final float MAX_MENU_SCALE_MULTIPLIER = 3.0F;
   protected static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private float uiScale = 1.0F;
   private int uiWidth;
   private int uiHeight;
   private int cachedGuiWidth = -1;
   private int cachedGuiHeight = -1;
   private float cachedMultiplier = Float.NaN;

   protected ScaledScreen(Component title) {
      super(title);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      for (Renderable renderable : this.renderables) {
         renderable.render(graphics, mouseX, mouseY, partialTick);
      }
   }

   protected void updateUiScale() {
      if (this.minecraft == null) {
         this.uiScale = 1.0F;
         this.uiWidth = this.width;
         this.uiHeight = this.height;
      } else {
         Window window = this.minecraft.getWindow();
         int currentWidth = window.getGuiScaledWidth();
         int currentHeight = window.getGuiScaledHeight();
         float multiplier = this.getMenuScaleMultiplier();
         if (currentWidth != this.cachedGuiWidth || currentHeight != this.cachedGuiHeight || multiplier != this.cachedMultiplier) {
            float newScale = this.calculateUiScale(window, multiplier);
            if (newScale <= 0.0F || Float.isNaN(newScale) || Float.isInfinite(newScale)) {
               newScale = 1.0F;
            }

            newScale = Math.max(1.0F, (float)Math.floor((double)newScale));
            this.uiScale = newScale;
            this.uiWidth = Math.max(1, Math.round((float)currentWidth / this.uiScale));
            this.uiHeight = Math.max(1, Math.round((float)currentHeight / this.uiScale));
            this.cachedGuiWidth = currentWidth;
            this.cachedGuiHeight = currentHeight;
            this.cachedMultiplier = multiplier;
         }
      }
   }

   private float calculateUiScale(Window window, float multiplier) {
      float availableScale = this.getAvailableScale(window);
      float dynamicScale = this.computeDynamicScale(availableScale);
      float desiredScale = dynamicScale * multiplier;
      float minScale = this.getMinUiScale();
      return this.clamp(desiredScale, minScale, Math.max(minScale, availableScale));
   }

   protected float computeDynamicScale(float availableScale) {
      return (float)Math.sqrt((double)availableScale);
   }

   protected float getMinUiScale() {
      return 1.0F;
   }

   private float getAvailableScale(Window window) {
      int guiWidth = Math.max(1, window.getGuiScaledWidth());
      int guiHeight = Math.max(1, window.getGuiScaledHeight());
      float widthScale = (float)guiWidth / (float)this.getMinGuiWidth();
      float heightScale = (float)guiHeight / (float)this.getMinGuiHeight();
      float availableScale = Math.min(widthScale, heightScale);
      return Float.isFinite(availableScale) && !(availableScale <= 0.0F) ? Math.max(1.0F, availableScale) : 1.0F;
   }

   private float getMenuScaleMultiplier() {
      float multiplier = ConfigManager.getUserConfig().getMenuScaleMultiplier();
      return !Float.isFinite(multiplier) ? 1.0F : this.clamp(multiplier, 0.25F, 3.0F);
   }

   private float clamp(float value, float min, float max) {
      return max < min ? min : Math.max(min, Math.min(max, value));
   }

   protected int getMinGuiWidth() {
      return 320;
   }

   protected int getMinGuiHeight() {
      return 240;
   }

   protected float getUiScale() {
      this.updateUiScale();
      return this.uiScale;
   }

   protected int getUiWidth() {
      this.updateUiScale();
      return this.uiWidth;
   }

   protected int getUiHeight() {
      this.updateUiScale();
      return this.uiHeight;
   }

   protected double toUiX(double mouseX) {
      this.updateUiScale();
      return mouseX / (double)this.uiScale;
   }

   protected double toUiY(double mouseY) {
      this.updateUiScale();
      return mouseY / (double)this.uiScale;
   }

   protected int toScreenCoord(double uiCoord) {
      this.updateUiScale();
      return (int)Math.round(uiCoord * (double)this.uiScale);
   }

   protected void beginUiScale(GuiGraphics graphics) {
      this.updateUiScale();
      graphics.pose().pushPose();
      graphics.pose().scale(this.uiScale, this.uiScale, 1.0F);
   }

   protected void endUiScale(GuiGraphics graphics) {
      graphics.pose().popPose();
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return super.mouseClicked(this.toUiX(mouseX), this.toUiY(mouseY), button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return super.mouseReleased(this.toUiX(mouseX), this.toUiY(mouseY), button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      return super.mouseDragged(this.toUiX(mouseX), this.toUiY(mouseY), button, dragX / (double)this.getUiScale(), dragY / (double)this.getUiScale());
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return super.mouseScrolled(this.toUiX(mouseX), this.toUiY(mouseY), scrollX, scrollY);
   }

   public void mouseMoved(double mouseX, double mouseY) {
      super.mouseMoved(this.toUiX(mouseX), this.toUiY(mouseY));
   }

   public MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   public MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }
}
