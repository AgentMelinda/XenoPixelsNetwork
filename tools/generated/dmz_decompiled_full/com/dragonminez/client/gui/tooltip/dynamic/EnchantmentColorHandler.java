package com.dragonminez.client.gui.tooltip.dynamic;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class EnchantmentColorHandler {
   private static final int ENCHANTMENT_COLOR = 11184810;
   private static final int SUPER_LEVELED_COLOR = 16733695;
   private static final int CURSE_COLOR = 16733525;

   public static void colorizeEnchantmentNames(ItemStack stack, List<Component> tooltip) {
      ItemEnchantments enchantments = (ItemEnchantments)stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
      if (enchantments.isEmpty()) {
         enchantments = (ItemEnchantments)stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
      }

      if (!enchantments.isEmpty()) {
         for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            ComponentContents var6 = line.getContents();
            if (var6 instanceof TranslatableContents) {
               TranslatableContents translatable = (TranslatableContents)var6;
               if (translatable.getKey().startsWith("enchantment.")) {
                  for (Holder<Enchantment> holder : enchantments.keySet()) {
                     int level = enchantments.getLevel(holder);
                     String descId = holder.unwrapKey().map(key -> Util.makeDescriptionId("enchantment", key.location())).orElse("");
                     if (translatable.getKey().equals(descId)) {
                        int color = 11184810;
                        if (descId.contains("curse") || descId.contains("vanishing") || descId.contains("binding")) {
                           color = 16733525;
                        } else if (level > 5) {
                           color = 16733695;
                        }

                        int finalColor = color;
                        MutableComponent newComponent = line.copy().withStyle(style -> style.withColor(finalColor));
                        tooltip.set(i, newComponent);
                        break;
                     }
                  }
               }
            }
         }
      }
   }
}
