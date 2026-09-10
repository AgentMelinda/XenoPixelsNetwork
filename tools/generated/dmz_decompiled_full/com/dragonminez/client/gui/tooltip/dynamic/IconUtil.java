package com.dragonminez.client.gui.tooltip.dynamic;

import net.minecraft.network.chat.Component;

public final class IconUtil {
   private IconUtil() {
   }

   public static boolean isIconGlyph(int cp) {
      return cp >= 57344 && cp <= 63743;
   }

   public static int[] firstIconSpan(String s) {
      int formattingStart = -1;
      int i = 0;

      while (i < s.length()) {
         int cp = s.codePointAt(i);
         int len = Character.charCount(cp);
         if (cp == 167) {
            if (formattingStart == -1) {
               formattingStart = i;
            }

            if (i + 1 < s.length()) {
               int nextCp = s.codePointAt(i + 1);
               if (nextCp != 120 && nextCp != 88) {
                  i += 2;
               } else {
                  i += 2;

                  for (int k = 0; k < 6 && i < s.length() && s.charAt(i) == 167 && i + 1 < s.length(); k++) {
                     i += 2;
                  }
               }
            } else {
               i++;
            }
         } else {
            if (isIconGlyph(cp)) {
               int actualStart = formattingStart >= 0 ? formattingStart : i;
               return new int[]{actualStart, i + len};
            }

            formattingStart = -1;
            i += len;
         }
      }

      return new int[]{-1, -1};
   }

   public static String stripSectionCodes(String s) {
      StringBuilder out = new StringBuilder(s.length());
      int i = 0;

      while (i < s.length()) {
         int cp = s.codePointAt(i);
         int len = Character.charCount(cp);
         if (cp != 167) {
            out.appendCodePoint(cp);
            i += len;
         } else if (i + 1 < s.length()) {
            int nextCp = s.codePointAt(i + 1);
            if (nextCp != 120 && nextCp != 88) {
               i += 2;
            } else {
               i += 2;

               for (int k = 0; k < 6 && i < s.length() && s.codePointAt(i) == 167 && i + 1 < s.length(); k++) {
                  i += 2;
               }
            }
         } else {
            i++;
         }
      }

      return out.toString();
   }

   public static Component processIcon(Component originalAttr, Component translatedStat) {
      String raw = originalAttr.getString();
      int[] span = firstIconSpan(raw);
      if (span[0] >= 0) {
         String icon = raw.substring(span[0], span[1]);
         return Component.empty()
            .append(Component.literal(icon).withStyle(style -> style.withColor(16777215)))
            .append(Component.literal(" "))
            .append(translatedStat);
      } else {
         return translatedStat;
      }
   }

   public static Component getAttributeNameWithoutIcon(Component attributeComponent) {
      String raw = attributeComponent.getString();
      int[] span = firstIconSpan(raw);
      if (span[0] >= 0) {
         String restRaw = raw.substring(0, span[0]) + raw.substring(span[1]);
         String rest = stripSectionCodes(restRaw).replaceFirst("^\\s+", "");
         return Component.literal(rest);
      } else {
         return attributeComponent;
      }
   }
}
