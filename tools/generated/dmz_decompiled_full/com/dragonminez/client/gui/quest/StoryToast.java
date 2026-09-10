package com.dragonminez.client.gui.quest;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class StoryToast implements Toast {
   private static final long DURATION_MS = 5000L;
   private final Component title;
   private final Component description;
   private final StoryToast.Tone tone;

   public StoryToast(Component title, Component description, StoryToast.Tone tone) {
      this.title = title;
      this.description = description;
      this.tone = tone;
   }

   public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long delta) {
      int backgroundColor = switch (this.tone) {
         case INFO -> -871358165;
         case PROGRESS -> -870505672;
         case FAILURE -> -869132521;
         case SUCCESS -> -870895326;
      };

      int borderColor = switch (this.tone) {
         case INFO -> -10963969;
         case PROGRESS -> -8483841;
         case FAILURE -> -37523;
         case SUCCESS -> -11740275;
      };
      Font font = toastComponent.getMinecraft().font;
      guiGraphics.fill(0, 0, this.width(), this.height(), backgroundColor);
      guiGraphics.fill(0, 0, this.width(), 2, borderColor);
      guiGraphics.fill(0, this.height() - 1, this.width(), this.height(), 1711276032);
      int progressWidth = (int)((1.0F - Math.min((float)delta / 5000.0F, 1.0F)) * (float)(this.width() - 8));
      guiGraphics.fill(4, this.height() - 4, 4 + progressWidth, this.height() - 2, borderColor);
      int textWidth = this.width() - 16;
      List<FormattedCharSequence> titleLines = font.split(this.title, textWidth);
      List<FormattedCharSequence> descriptionLines = font.split(this.description, textWidth);
      int textY = 6;
      int titleHeight = drawWrappedLines(guiGraphics, font, titleLines, 8, textY, 16777215, 2);
      textY += titleHeight;
      if (titleHeight > 0 && !descriptionLines.isEmpty()) {
         textY += 2;
      }

      drawWrappedLines(guiGraphics, font, descriptionLines, 8, textY, 14214911, 4);
      return delta >= 5000L ? Visibility.HIDE : Visibility.SHOW;
   }

   private static int drawWrappedLines(GuiGraphics guiGraphics, Font font, List<FormattedCharSequence> lines, int x, int startY, int color, int maxLines) {
      int count = Math.min(maxLines, lines.size());

      for (int i = 0; i < count; i++) {
         guiGraphics.drawString(font, lines.get(i), x, startY + i * 9, color, false);
      }

      return count * 9;
   }

   public int width() {
      return 220;
   }

   public int height() {
      return 52;
   }

   public static enum Tone {
      INFO,
      PROGRESS,
      FAILURE,
      SUCCESS;
   }
}
