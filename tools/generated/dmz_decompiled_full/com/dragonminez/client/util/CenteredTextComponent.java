package com.dragonminez.client.util;

import com.dragonminez.client.gui.tooltip.TooltipDecor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

public class CenteredTextComponent implements ClientTooltipComponent {
   private final ClientTooltipComponent original;

   public CenteredTextComponent(FormattedCharSequence text) {
      this.original = ClientTooltipComponent.create(text);
   }

   public int getWidth(Font font) {
      return this.original.getWidth(font);
   }

   public int getHeight() {
      return this.original.getHeight();
   }

   public void renderText(Font font, int x, int y, Matrix4f matrix, BufferSource bufferSource) {
      int finalX = x;
      if (TooltipDecor.hasSpecialBorder && !TooltipDecor.hasItemBox && y <= TooltipDecor.lastTooltipY + 12) {
         int textW = this.getWidth(font);
         finalX = TooltipDecor.lastTooltipX + TooltipDecor.lastTooltipW / 2 - textW / 2;
      }

      this.original.renderText(font, finalX, y, matrix, bufferSource);
   }
}
