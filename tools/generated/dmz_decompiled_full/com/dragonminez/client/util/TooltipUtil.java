package com.dragonminez.client.util;

import com.dragonminez.client.gui.tooltip.TooltipDecor;
import com.dragonminez.mixin.client.ClientTextTooltipAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class TooltipUtil {
   public static List<ClientTooltipComponent> wrapComponents(List<ClientTooltipComponent> components, Font font, int screenWidth) {
      if (TooltipDecor.forceCustomBorder) {
         return new ArrayList<>(components);
      } else {
         int maxWidth = Math.max(screenWidth / 2, 60);
         List<ClientTooltipComponent> wrapped = new ArrayList<>();

         for (ClientTooltipComponent component : components) {
            if (component instanceof ClientTextTooltip) {
               ClientTextTooltip textTooltip = (ClientTextTooltip)component;
               FormattedCharSequence charSequence = ((ClientTextTooltipAccessor)textTooltip).getText();
               Component text = toText(charSequence);
               if (text.getString().isEmpty()) {
                  wrapped.add(component);
               } else {
                  for (FormattedCharSequence splitLine : font.split(text, maxWidth)) {
                     wrapped.add(new CenteredTextComponent(splitLine));
                  }
               }
            } else {
               wrapped.add(component);
            }
         }

         return wrapped;
      }
   }

   public static Component toText(FormattedCharSequence sequence) {
      final MutableComponent result = Component.empty();

      class Accumulator {
         StringBuilder sb = new StringBuilder();
         Style currentStyle = Style.EMPTY;

         void flush() {
            if (this.sb.length() > 0) {
               result.append(Component.literal(this.sb.toString()).withStyle(this.currentStyle));
               this.sb.setLength(0);
            }
         }
      }

      Accumulator acc = new Accumulator();
      sequence.accept((index, style, codePoint) -> {
         if (!style.equals(acc.currentStyle)) {
            acc.flush();
            acc.currentStyle = style;
         }

         acc.sb.append(Character.toChars(codePoint));
         return true;
      });
      acc.flush();
      return result;
   }
}
