package com.dragonminez.client.gui.buttons;

import com.dragonminez.common.init.MainSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TexturedTextButton extends Button {
   private final ResourceLocation texture;
   private final int textureWidth;
   private final int textureHeight;
   private final int normalU;
   private final int normalV;
   private final int hoverU;
   private final int hoverV;
   private final int normalTextColor;
   private final int hoverTextColor;
   private int backgroundColor;
   private boolean hasBackgroundColor;
   private final SoundEvent sound;

   public TexturedTextButton(
      int x,
      int y,
      int width,
      int height,
      ResourceLocation texture,
      int normalU,
      int normalV,
      int hoverU,
      int hoverV,
      int textureWidth,
      int textureHeight,
      int normalTextColor,
      int hoverTextColor,
      int backgroundColor,
      boolean hasBackgroundColor,
      Component message,
      OnPress onPress,
      SoundEvent sound
   ) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
      this.texture = texture;
      this.normalU = normalU;
      this.normalV = normalV;
      this.hoverU = hoverU;
      this.hoverV = hoverV;
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
      this.normalTextColor = normalTextColor;
      this.hoverTextColor = hoverTextColor;
      this.backgroundColor = backgroundColor;
      this.hasBackgroundColor = hasBackgroundColor;
      this.sound = sound;
   }

   public void playDownSound(SoundManager handler) {
      if (this.sound != null) {
         handler.play(SimpleSoundInstance.forUI(this.sound, 1.0F));
      }
   }

   protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      Minecraft minecraft = Minecraft.getInstance();
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderTexture(0, this.texture);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      int u = this.isHoveredOrFocused() ? this.hoverU : this.normalU;
      int v = this.isHoveredOrFocused() ? this.hoverV : this.normalV;
      graphics.blit(this.texture, this.getX(), this.getY(), u, v, this.textureWidth, this.textureHeight);
      if (this.hasBackgroundColor) {
         graphics.fill(
            this.getX() + 2, this.getY() + 2, this.getX() + this.width - 2, this.getY() + this.height - 2, 0xFF000000 | this.backgroundColor & 16777215
         );
      }

      int textColor = this.active ? (this.isHoveredOrFocused() ? this.hoverTextColor : this.normalTextColor) : 10526880;
      graphics.drawCenteredString(minecraft.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
   }

   public void setBackgroundColor(int color) {
      this.backgroundColor = color;
      this.hasBackgroundColor = true;
   }

   public static class Builder {
      private int x;
      private int y;
      private int width;
      private int height;
      private ResourceLocation texture;
      private int normalU;
      private int normalV;
      private int hoverU;
      private int hoverV;
      private int textureWidth;
      private int textureHeight;
      private int normalTextColor = 16777215;
      private int hoverTextColor = 8191446;
      private int backgroundColor = 0;
      private boolean hasBackgroundColor = false;
      private Component message = Component.empty();
      private OnPress onPress;
      private SoundEvent sound = (SoundEvent)MainSounds.PIP_MENU.get();

      public TexturedTextButton.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public TexturedTextButton.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public TexturedTextButton.Builder texture(ResourceLocation texture) {
         this.texture = texture;
         return this;
      }

      public TexturedTextButton.Builder textureCoords(int normalU, int normalV, int hoverU, int hoverV) {
         this.normalU = normalU;
         this.normalV = normalV;
         this.hoverU = hoverU;
         this.hoverV = hoverV;
         return this;
      }

      public TexturedTextButton.Builder textureSize(int width, int height) {
         this.textureWidth = width;
         this.textureHeight = height;
         return this;
      }

      public TexturedTextButton.Builder textColors(int normalColor, int hoverColor) {
         this.normalTextColor = normalColor;
         this.hoverTextColor = hoverColor;
         return this;
      }

      public TexturedTextButton.Builder backgroundColor(int color) {
         this.backgroundColor = color;
         this.hasBackgroundColor = true;
         return this;
      }

      public TexturedTextButton.Builder message(Component message) {
         this.message = message;
         return this;
      }

      public TexturedTextButton.Builder onPress(OnPress onPress) {
         this.onPress = onPress;
         return this;
      }

      public TexturedTextButton.Builder sound(SoundEvent sound) {
         this.sound = sound;
         return this;
      }

      public TexturedTextButton build() {
         return new TexturedTextButton(
            this.x,
            this.y,
            this.width,
            this.height,
            this.texture,
            this.normalU,
            this.normalV,
            this.hoverU,
            this.hoverV,
            this.textureWidth,
            this.textureHeight,
            this.normalTextColor,
            this.hoverTextColor,
            this.backgroundColor,
            this.hasBackgroundColor,
            this.message,
            this.onPress,
            this.sound
         );
      }
   }
}
