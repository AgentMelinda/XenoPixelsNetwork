package com.dragonminez.client.gui.buttons;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IconButton extends Button {
   private final ResourceLocation icon;
   private final int iconSize;
   private final int textureSize;

   public IconButton(int x, int y, int width, int height, ResourceLocation icon, int iconSize, int textureSize, Component message, OnPress onPress) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
      this.icon = icon;
      this.iconSize = iconSize;
      this.textureSize = textureSize;
   }

   protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      int iconX = this.getX() + (this.getWidth() - this.iconSize) / 2;
      int iconY = this.getY() + (this.getHeight() - this.iconSize) / 2;
      graphics.blit(this.icon, iconX, iconY, this.iconSize, this.iconSize, 0.0F, 0.0F, this.textureSize, this.textureSize, this.textureSize, this.textureSize);
   }

   public void renderString(GuiGraphics graphics, Font font, int color) {
   }
}
