package com.dragonminez.client.gui.tooltip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public class CustomTooltipRenderers {
   public static class HeaderRenderer implements ClientTooltipComponent {
      private final ItemStack stack;
      private final FormattedCharSequence title;

      public HeaderRenderer(CustomTooltipNodes.HeaderNode node) {
         this.stack = node.stack();
         this.title = Language.getInstance().getVisualOrder(node.title());
      }

      public int getHeight() {
         return 24;
      }

      public int getWidth(Font font) {
         return 26 + font.width(this.title);
      }

      public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
         if (TooltipDecor.hasSpecialBorder) {
            graphics.flush();
            int margin = 2;
            int renderWidth = 22;
            int renderHeight = 22;
            int borderStart = TooltipDecor.combineARGB(
               (int)((float)(TooltipDecor.currentBorderStart >> 24 & 0xFF) * 0.35F),
               TooltipDecor.currentBorderStart >> 16 & 0xFF,
               TooltipDecor.currentBorderStart >> 8 & 0xFF,
               TooltipDecor.currentBorderStart & 0xFF
            );
            int bgStart = TooltipDecor.combineARGB(
               (int)((float)(TooltipDecor.currentBackgroundStart >> 24 & 0xFF) * 0.15F),
               TooltipDecor.currentBackgroundStart >> 16 & 0xFF,
               TooltipDecor.currentBackgroundStart >> 8 & 0xFF,
               TooltipDecor.currentBackgroundStart & 0xFF
            );
            int bgEnd = TooltipDecor.combineARGB(
               (int)((float)(TooltipDecor.currentBackgroundEnd >> 24 & 0xFF) * 0.6F),
               TooltipDecor.currentBackgroundEnd >> 16 & 0xFF,
               TooltipDecor.currentBackgroundEnd >> 8 & 0xFF,
               TooltipDecor.currentBackgroundEnd & 0xFF
            );
            PoseStack pose = graphics.pose();
            Matrix4f matrix = pose.last().pose();
            TooltipDecor.drawGradientRect(
               matrix, 0, x + margin + 1, y + margin + 1, x + renderWidth - margin - 1, y + renderHeight - margin - 1, bgStart, bgEnd
            );
            TooltipDecor.drawGradientRectHorizontal(
               matrix, 0, x + margin + 1, y + margin + 1, x + renderWidth - margin - 1, y + renderHeight - margin - 1, bgStart, bgEnd
            );
            TooltipDecor.drawGradientRect(matrix, 0, x + margin + 1, y + margin, x + renderWidth - margin - 1, y + margin + 1, borderStart, borderStart);
            TooltipDecor.drawGradientRect(
               matrix, 0, x + margin + 1, y + renderHeight - margin - 1, x + renderWidth - margin - 1, y + renderHeight - margin, borderStart, borderStart
            );
            TooltipDecor.drawGradientRect(matrix, 0, x + margin, y + margin + 1, x + margin + 1, y + renderHeight - margin - 1, borderStart, borderStart);
            TooltipDecor.drawGradientRect(
               matrix, 0, x + renderWidth - margin - 1, y + margin + 1, x + renderWidth - margin, y + renderHeight - margin - 1, borderStart, borderStart
            );
            pose.pushPose();
            float offsetX = -0.75F;
            float offsetY = -0.75F;
            float centerX = (float)(x + margin - 1 + 11) + offsetX;
            float centerY = (float)(y + margin - 1 + 11) + offsetY;
            pose.translate(centerX, centerY, 150.0F);
            float rotationAngle = (float)(System.currentTimeMillis() % 4000L) / 4000.0F * 360.0F;
            pose.mulPose(Axis.YP.rotationDegrees(rotationAngle));
            float scale = 0.9F;
            pose.scale(scale, scale, scale);
            pose.translate(-8.0F, -8.0F, -150.0F);
            graphics.renderItem(this.stack, 0, 0);
            pose.popPose();
         }
      }

      public void renderText(Font font, int x, int y, Matrix4f matrix, BufferSource bufferSource) {
         int textY = y + (22 - 9) / 2;
         font.drawInBatch(this.title, (float)(x + 26), (float)textY, -1, true, matrix, bufferSource, DisplayMode.NORMAL, 0, 15728880);
      }
   }

   public static class PaddingRenderer implements ClientTooltipComponent {
      private final int height;

      public PaddingRenderer(CustomTooltipNodes.PaddingNode node) {
         this.height = node.height();
      }

      public int getHeight() {
         return this.height;
      }

      public int getWidth(Font font) {
         return 0;
      }

      public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
      }
   }

   public static class SeparatorRenderer implements ClientTooltipComponent {
      public SeparatorRenderer(CustomTooltipNodes.SeparatorNode node) {
      }

      public int getHeight() {
         return 0;
      }

      public int getWidth(Font font) {
         return 0;
      }

      public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
         if (TooltipDecor.hasSpecialBorder) {
            TooltipDecor.drawSeparator(graphics.pose(), TooltipDecor.lastTooltipX, y - 8, TooltipDecor.lastTooltipW, TooltipDecor.currentBorderStart);
         }
      }
   }
}
