package com.dragonminez.client.gui.tooltip.dynamic;

import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.weapon.AttributesContainer;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.combat.weapon.WeaponAttributesHelper;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

public class MergedAttributeHandler {
   private static final int MERGE_BASE_COLOR = 16763904;
   private static final int BASE_COLOR = 43520;
   private static final int MODIFIER_BLUE = 5592575;
   private static final int MODIFIER_RED = 16733525;
   private static final DecimalFormat FORMAT = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ROOT));

   public static boolean processTooltips(ItemStack stack, List<Component> lines) {
      boolean needsShiftPrompt = false;
      WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
      if (attributes == null) {
         AttributesContainer container = WeaponAttributesHelper.getContainerFromNBT(stack);
         if (container != null && container.attributes() != null) {
            attributes = container.attributes();
         }
      }

      cleanRedundantHeaders(lines);
      if (attributes != null && attributes.attackRange() > 0.0) {
         needsShiftPrompt = handleAttackRange(lines, attributes.attackRange());
      }

      if (attributes != null && attributes.isTwoHanded()) {
         handleTwoHanded(lines);
      }

      return needsShiftPrompt;
   }

   private static void cleanRedundantHeaders(List<Component> lines) {
      int firstHeaderIndex = -1;
      List<Integer> headersToRemove = new ArrayList<>();

      for (int i = 0; i < lines.size(); i++) {
         ComponentContents var5 = lines.get(i).getContents();
         if (var5 instanceof TranslatableContents) {
            TranslatableContents translatable = (TranslatableContents)var5;
            if (translatable.getKey().startsWith("item.modifiers.")) {
               if (firstHeaderIndex == -1) {
                  firstHeaderIndex = i;
               } else {
                  headersToRemove.add(i);
               }
            }
         }
      }

      for (int ix = headersToRemove.size() - 1; ix >= 0; ix--) {
         lines.remove(headersToRemove.get(ix).intValue());
      }

      if (firstHeaderIndex != -1) {
         lines.set(firstHeaderIndex, Component.translatable("item.modifiers.mainhand").withStyle(ChatFormatting.GRAY));
      }
   }

   private static boolean handleAttackRange(List<Component> lines, double totalRange) {
      int targetIndex = findLastAttributeIndex(lines);
      double basePlayerRange = 3.0;
      double modifierValue = totalRange - basePlayerRange;
      boolean hasModifier = Math.abs(modifierValue) > 0.01;
      if (Screen.hasShiftDown() && hasModifier) {
         MutableComponent totalLine = createRangeComponent(totalRange, "attribute.name.generic.attack_range", 16763904);
         MutableComponent baseLine = createRangeComponent(basePlayerRange, "attribute.name.generic.attack_range", 43520);
         MutableComponent modLine = createModifierComponent(modifierValue, "attribute.name.generic.attack_range");
         lines.add(targetIndex + 1, totalLine);
         lines.add(targetIndex + 2, Component.literal(" ┠ ").withStyle(ChatFormatting.GRAY).append(baseLine));
         lines.add(targetIndex + 3, Component.literal(" ┠ ").withStyle(ChatFormatting.GRAY).append(modLine));
      } else {
         int color = hasModifier ? 16763904 : 'ꨀ';
         lines.add(targetIndex + 1, createRangeComponent(totalRange, "attribute.name.generic.attack_range", color));
      }

      return hasModifier;
   }

   private static void handleTwoHanded(List<Component> lines) {
      int headerIndex = -1;

      for (int i = 0; i < lines.size(); i++) {
         if (lines.get(i).getContents() instanceof TranslatableContents t && t.getKey().startsWith("item.modifiers.")) {
            headerIndex = i;
            break;
         }
      }

      if (headerIndex >= 0) {
         lines.add(headerIndex + 1, Component.translatable("tooltip.dragonminez.two_handed").withStyle(ChatFormatting.DARK_GRAY));
      }
   }

   private static int findLastAttributeIndex(List<Component> lines) {
      int lastIndex = lines.size() - 1;

      for (int i = 0; i < lines.size(); i++) {
         Component line = lines.get(i);
         if (line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("attribute.modifier")) {
            lastIndex = i;
            continue;
         }

         for (Component sibling : line.getSiblings()) {
            ComponentContents var8 = sibling.getContents();
            if (var8 instanceof TranslatableContents) {
               TranslatableContents tSibling = (TranslatableContents)var8;
               if (tSibling.getKey().startsWith("attribute.modifier")) {
                  lastIndex = i;
               }
            }
         }
      }

      return lastIndex;
   }

   private static MutableComponent createRangeComponent(double value, String translationKey, int colorCode) {
      return Component.literal(" ")
         .append(Component.translatable("attribute.modifier.equals.0", new Object[]{FORMAT.format(value), Component.translatable(translationKey)}))
         .withStyle(style -> style.withColor(colorCode));
   }

   private static MutableComponent createModifierComponent(double value, String translationKey) {
      boolean isPositive = value > 0.0;
      String key = isPositive ? "attribute.modifier.plus.0" : "attribute.modifier.take.0";
      int color = isPositive ? 5592575 : 16733525;
      return Component.translatable(key, new Object[]{FORMAT.format(Math.abs(value)), Component.translatable(translationKey)})
         .withStyle(style -> style.withColor(color));
   }
}
