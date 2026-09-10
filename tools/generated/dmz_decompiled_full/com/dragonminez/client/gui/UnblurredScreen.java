package com.dragonminez.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class UnblurredScreen extends Screen {
   protected UnblurredScreen(Component title) {
      super(title);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      for (Renderable renderable : this.renderables) {
         renderable.render(graphics, mouseX, mouseY, partialTick);
      }
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderTransparentBackground(graphics);
   }
}
