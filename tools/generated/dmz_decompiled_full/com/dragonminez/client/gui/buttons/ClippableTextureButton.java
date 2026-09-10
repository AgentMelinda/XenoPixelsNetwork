package com.dragonminez.client.gui.buttons;

import com.dragonminez.common.init.MainSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClippableTextureButton extends Button {
   private final ResourceLocation texture;
   private final int normalU;
   private final int normalV;
   private final int hoverU;
   private final int hoverV;
   private final int textureWidth;
   private final int textureHeight;
   private final boolean clipping;
   private int scissorX1;
   private int scissorY1;
   private int scissorX2;
   private int scissorY2;
   private final SoundEvent sound;

   public ClippableTextureButton(
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
      boolean clipping,
      int scissorX1,
      int scissorY1,
      int scissorX2,
      int scissorY2,
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
      this.clipping = clipping;
      this.scissorX1 = scissorX1;
      this.scissorY1 = scissorY1;
      this.scissorX2 = scissorX2;
      this.scissorY2 = scissorY2;
      this.sound = sound;
   }

   public void setScissorRect(int x1, int y1, int x2, int y2) {
      this.scissorX1 = x1;
      this.scissorY1 = y1;
      this.scissorX2 = x2;
      this.scissorY2 = y2;
   }

   public void playDownSound(SoundManager handler) {
      if (this.sound != null) {
         handler.play(SimpleSoundInstance.forUI(this.sound, 1.0F));
      }
   }

   public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.clipping) {
         graphics.enableScissor(this.scissorX1, this.scissorY1, this.scissorX2, this.scissorY2);
      }

      int u = this.isHoveredOrFocused() ? this.hoverU : this.normalU;
      int v = this.isHoveredOrFocused() ? this.hoverV : this.normalV;
      graphics.blit(this.texture, this.getX(), this.getY(), u, v, this.width, this.height);
      if (this.clipping) {
         graphics.disableScissor();
      }
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
      private Component message = Component.empty();
      private OnPress onPress;
      private boolean clipping = false;
      private int scissorX1;
      private int scissorY1;
      private int scissorX2;
      private int scissorY2;
      private SoundEvent sound = (SoundEvent)MainSounds.UI_NAVE_COOLDOWN.get();

      public ClippableTextureButton.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public ClippableTextureButton.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public ClippableTextureButton.Builder texture(ResourceLocation texture) {
         this.texture = texture;
         return this;
      }

      public ClippableTextureButton.Builder textureCoords(int normalU, int normalV, int hoverU, int hoverV) {
         this.normalU = normalU;
         this.normalV = normalV;
         this.hoverU = hoverU;
         this.hoverV = hoverV;
         return this;
      }

      public ClippableTextureButton.Builder textureSize(int width, int height) {
         this.textureWidth = width;
         this.textureHeight = height;
         return this;
      }

      public ClippableTextureButton.Builder message(Component message) {
         this.message = message;
         return this;
      }

      public ClippableTextureButton.Builder onPress(OnPress onPress) {
         this.onPress = onPress;
         return this;
      }

      public ClippableTextureButton.Builder clipping(boolean clipping, int x1, int y1, int x2, int y2) {
         this.clipping = clipping;
         this.scissorX1 = x1;
         this.scissorY1 = y1;
         this.scissorX2 = x2;
         this.scissorY2 = y2;
         return this;
      }

      public ClippableTextureButton.Builder sound(SoundEvent sound) {
         this.sound = sound;
         return this;
      }

      public ClippableTextureButton build() {
         return new ClippableTextureButton(
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
            this.clipping,
            this.scissorX1,
            this.scissorY1,
            this.scissorX2,
            this.scissorY2,
            this.message,
            this.onPress,
            this.sound
         );
      }
   }
}
